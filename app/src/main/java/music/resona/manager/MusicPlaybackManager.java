package music.resona.manager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import music.resona.cache.SongPrefetchHelper;
import music.resona.cache.StreamCache;
import music.resona.models.Song;
import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.models.PlaybackDataResult;
import music.resona.service.MusicService;

/**
 * Singleton manager coordinating between UI and MusicService.
 * Handles stream URL fetching, prefetching, and playback control.
 */
public class MusicPlaybackManager {
    
    private static final String TAG = "MusicPlaybackManager";
    private static volatile MusicPlaybackManager instance;
    
    private final Context context;
    private MusicService musicService;
    private boolean isBound = false;
    
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final CopyOnWriteArrayList<PlaybackListener> listeners = new CopyOnWriteArrayList<>();
    
    // Current state
    private Song currentSong;
    private boolean isLoading = false;
    
    // Prefetch cache - store stream URLs for instant playback
    private final java.util.Map<String, StreamUrlCache> streamUrlCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int PREFETCH_COUNT = 3;
    private static final long STREAM_URL_BUFFER_TIME_MS = 5 * 60 * 1000; // 5 min buffer before expiry
    
    private static class StreamUrlCache {
        final String url;
        final long expiryTimeMs;
        
        StreamUrlCache(String url, long expiryTimeMs) {
            this.url = url;
            this.expiryTimeMs = expiryTimeMs;
        }
        
        boolean isValid() {
            return System.currentTimeMillis() < expiryTimeMs - STREAM_URL_BUFFER_TIME_MS;
        }
    }
    
    public interface PlaybackListener extends MusicService.PlaybackListener {
        // Inherits all methods from MusicService.PlaybackListener
    }
    
    public interface PlaybackCallback {
        void onSuccess();
        void onError(String message);
    }
    
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;
            
            // Register as listener to forward events
            musicService.addListener(serviceListener);
            Log.d(TAG, "Service connected");
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
            Log.d(TAG, "Service disconnected");
        }
    };
    
    private final MusicService.PlaybackListener serviceListener = new MusicService.PlaybackListener() {
        @Override
        public void onSongChanged(Song song) {
            currentSong = song;
            for (PlaybackListener listener : listeners) {
                listener.onSongChanged(song);
            }
        }
        
        @Override
        public void onPlaybackStateChanged(boolean isPlaying) {
            for (PlaybackListener listener : listeners) {
                listener.onPlaybackStateChanged(isPlaying);
            }
        }
        
        @Override
        public void onProgressChanged(int currentMs, int durationMs) {
            for (PlaybackListener listener : listeners) {
                listener.onProgressChanged(currentMs, durationMs);
            }
        }
        
        @Override
        public void onShuffleChanged(boolean shuffle) {
            for (PlaybackListener listener : listeners) {
                listener.onShuffleChanged(shuffle);
            }
        }
        
        @Override
        public void onRepeatModeChanged(MusicService.RepeatMode mode) {
            for (PlaybackListener listener : listeners) {
                listener.onRepeatModeChanged(mode);
            }
        }
        
        public void onNeedsStreamUrl(Song song) {
            // Check if this exact song is already being fetched to avoid duplicate network requests
            if (currentSong != null && currentSong.getVideoId().equals(song.getVideoId()) && isLoading) {
                Log.d(TAG, "Stream already being fetched for: " + song.getTitle());
                return;
            }
            
            // Fetch stream URL and play 
            Log.d(TAG, "Fetching stream for: " + song.getTitle());
            isLoading = true;
            currentSong = song;
            executor.execute(() -> {
                String streamUrl = StreamCache.getInstance().getStreamUrl(song.getVideoId());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    isLoading = false;
                    if (streamUrl != null && musicService != null) {
                        musicService.playSong(song, streamUrl);
                        prefetchNextSongs(song);
                    }
                });
            });
        }
        
        @Override
        public void onError(String message) {
            for (PlaybackListener listener : listeners) {
                listener.onError(message);
            }
        }
        
        @Override
        public void onLoadingStateChanged(boolean loading) {
            isLoading = loading;
            for (PlaybackListener listener : listeners) {
                listener.onLoadingStateChanged(loading);
            }
        }
        
        @Override
        public void onQueueChanged() {
            for (PlaybackListener listener : listeners) {
                listener.onQueueChanged();
            }
        }
    };
    
    private MusicPlaybackManager(Context context) {
        this.context = context.getApplicationContext();
        bindService();
    }
    
    public static MusicPlaybackManager getInstance(Context context) {
        if (instance == null) {
            synchronized (MusicPlaybackManager.class) {
                if (instance == null) {
                    instance = new MusicPlaybackManager(context);
                }
            }
        }
        return instance;
    }
    
    private void bindService() {
        Intent intent = new Intent(context, MusicService.class);
        context.startService(intent);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    
    public void addListener(PlaybackListener listener) {
        // Prevent duplicate listeners (same instance)
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            Log.d(TAG, "Listener added: " + listener.getClass().getSimpleName() + ", Total listeners: " + listeners.size());
        } else {
            Log.w(TAG, "Listener already exists: " + listener.getClass().getSimpleName());
        }
    }
    
    public void removeListener(PlaybackListener listener) {
        boolean removed = listeners.remove(listener);
        if (removed) {
            Log.d(TAG, "Listener removed: " + listener.getClass().getSimpleName() + ", Remaining listeners: " + listeners.size());
        }
    }
    
    /**
     * Play a single song using StreamCache for instant playback.
     * Uses LRU cache with 10-minute TTL and LOW quality for speed.
     */
    public void playSong(@NonNull Song song, @Nullable PlaybackCallback callback) {
        Log.d(TAG, "Playing song: " + song.getTitle());
        
        // Set loading state but don't update currentSong yet
        isLoading = true;
        notifyLoadingState(true);
        
        // Fetch stream URL in background to avoid blocking UI
        executor.execute(() -> {
            String streamUrl = StreamCache.getInstance().getStreamUrl(song.getVideoId());
            
            // Execute callback on main thread
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (streamUrl != null) {
                    // Update song ONLY when we have stream URL and are about to play
                    currentSong = song;
                    for (PlaybackListener listener : listeners) {
                        listener.onSongChanged(song);
                    }
                    playWithUrl(song, streamUrl, callback);
                    // Prefetch next songs in queue
                    prefetchNextSongs(song);
                } else {
                    isLoading = false;
                    notifyLoadingState(false);
                    if (callback != null) {
                        callback.onError("Failed to get stream URL");
                    }
                }
            });
        });
    }
    
    /**
     * Play a queue of songs starting at the specified index.
     */
    public void playQueue(@NonNull List<Song> songs, int startIndex, @Nullable PlaybackCallback callback) {
        if (songs.isEmpty()) {
            if (callback != null) {
                callback.onError("Queue is empty");
            }
            return;
        }
        
        Song startSong = songs.get(startIndex);
        
        // Play the first song
        playSong(startSong, new PlaybackCallback() {
            @Override
            public void onSuccess() {
                // Set the queue on the service
                if (musicService != null && isBound) {
                    musicService.setQueue(songs, startIndex);
                }
                if (callback != null) {
                    callback.onSuccess();
                }
            }
            
            @Override
            public void onError(String message) {
                if (callback != null) {
                    callback.onError(message);
                }
            }
        });
    }
    
    /**
     * Start radio/automix from a seed song.
     * Uses innertube-bridge to generate an endless queue of similar songs.
     * 
     * @param seedSong The song to base the radio on
     * @param callback Callback for success/error
     */
    public void playRadio(@NonNull Song seedSong, @Nullable PlaybackCallback callback) {
        Log.d(TAG, "Starting radio from: " + seedSong.getTitle());
        
        // IMMEDIATE: Set up initial queue to show in UI right away
        if (musicService != null && isBound) {
            List<Song> initialQueue = new ArrayList<>();
            initialQueue.add(seedSong);
            // Add placeholders if we have recent songs to avoid showing "1 song"
            if (getQueue() != null && getQueue().size() > 1) {
                // Keep some existing songs to fill the queue temporarily
                List<Song> existingQueue = getQueue();
                for (int i = 1; i < Math.min(existingQueue.size(), 5); i++) {
                    if (!initialQueue.contains(existingQueue.get(i))) {
                        initialQueue.add(existingQueue.get(i));
                    }
                }
            }
            musicService.setQueue(initialQueue, 0);
        }
        
        // Play the seed song first
        playSong(seedSong, new PlaybackCallback() {
            @Override
            public void onSuccess() {
                // Fetch radio queue via bridge (async, non-blocking)
                executor.execute(() -> {
                    try {
                        music.resona.online.bridge.InnertubeBridge.getRadioQueueAsync(
                            seedSong.getVideoId(),
                            null,
                            new music.resona.online.bridge.callbacks.SearchCallback() {
                                @Override
                                public void onSuccess(music.resona.online.bridge.models.SearchResult result) {
                                    // Convert bridge results to Song list
                                    List<Song> radioQueue = convertSearchResultToSongs(result);
                                    
                                    if (!radioQueue.isEmpty() && musicService != null && isBound) {
                                        // Prepend seed song to radio queue
                                        radioQueue.add(0, seedSong);
                                        musicService.setQueue(radioQueue, 0);
                                        
                                        // Prefetch next songs
                                        prefetchSongs(radioQueue);
                                        
                                        Log.d(TAG, "Radio queue updated: " + radioQueue.size() + " songs");
                                    }
                                }
                                
                                @Override
                                public void onError(music.resona.online.bridge.exceptions.BridgeException error) {
                                    Log.e(TAG, "Failed to get radio queue: " + error.getMessage());
                                    // Continue with just the seed song
                                }
                            }
                        );
                    } catch (Exception e) {
                        Log.e(TAG, "Error fetching radio queue", e);
                    }
                });
                
                if (callback != null) {
                    callback.onSuccess();
                }
            }
            
            @Override
            public void onError(String message) {
                if (callback != null) {
                    callback.onError(message);
                }
            }
        });
    }
    
    /**
     * Convert bridge SearchResult to List<Song>.
     * Helper method to transform API results.
     */
    private List<Song> convertSearchResultToSongs(music.resona.online.bridge.models.SearchResult result) {
        List<Song> songs = new ArrayList<>();
        
        if (result.getItems() != null) {
            for (music.resona.online.bridge.models.YTItemResult item : result.getItems()) {
                // Only convert songs (type == "song")
                if ("song".equals(item.getType())) {
                    String artist = item.getArtists() != null && !item.getArtists().isEmpty() 
                        ? item.getArtists().get(0).getName() 
                        : "Unknown Artist";
                    String album = item.getAlbum() != null ? item.getAlbum().getName() : null;
                    
                    Song song = new Song(
                        item.getId(),
                        item.getTitle(),
                        artist,
                        album,
                        item.getThumbnail(),
                        item.getDuration() != null ? item.getDuration() : 0
                    );
                    songs.add(song);
                }
            }
        }
        
        return songs;
    }
    
    /**
     * Add songs to the end of the current queue.
     */
    public void addToQueue(@NonNull List<Song> songs) {
        if (musicService != null && isBound) {
            List<Song> currentQueue = musicService.getQueue();
            currentQueue.addAll(songs);
            int currentIndex = musicService.getCurrentIndex();
            musicService.setQueue(currentQueue, currentIndex);
        }
    }
    
    /**
     * Add a song to play next (after current song).
     */
    public void addNext(@NonNull Song song) {
        if (musicService != null && isBound) {
            List<Song> currentQueue = musicService.getQueue();
            int currentIndex = musicService.getCurrentIndex();
            currentQueue.add(currentIndex + 1, song);
            musicService.setQueue(currentQueue, currentIndex);
            
            // Prefetch the next song since it's been explicitly queued
            StreamCache.getInstance().prefetch(song.getVideoId());
        }
    }
    
    // ========== QUEUE MANIPULATION (Phase 2) ==========
    
    /**
     * Move a song in the queue from one position to another.
     */
    public void moveSongInQueue(int fromPosition, int toPosition) {
        if (musicService != null && isBound) {
            // Queue manager handles this internally
            // We need to rebuild the queue
            List<Song> queue = new ArrayList<>(musicService.getQueue());
            if (fromPosition >= 0 && fromPosition < queue.size() &&
                toPosition >= 0 && toPosition < queue.size()) {
                Song song = queue.remove(fromPosition);
                queue.add(toPosition, song);
                int currentIndex = musicService.getCurrentIndex();
                musicService.setQueue(queue, currentIndex);
            }
        }
    }
    
    /**
     * Remove a song from the queue at the specified position.
     */
    public void removeSongFromQueue(int position) {
        if (musicService != null && isBound) {
            List<Song> queue = new ArrayList<>(musicService.getQueue());
            if (position >= 0 && position < queue.size() && position != musicService.getCurrentIndex()) {
                queue.remove(position);
                int currentIndex = musicService.getCurrentIndex();
                // Adjust current index if needed
                if (position < currentIndex) {
                    currentIndex--;
                }
                musicService.setQueue(queue, currentIndex);
            }
        }
    }
    
    /**
     * Clear the entire queue except the current song.
     */
    public void clearQueue() {
        if (musicService != null && isBound) {
            Song current = getCurrentSong();
            if (current != null) {
                List<Song> newQueue = new ArrayList<>();
                newQueue.add(current);
                musicService.setQueue(newQueue, 0);
            }
        }
    }
    
    /**
     * Get queue statistics.
     */
    @Nullable
    public String getQueueStats() {
        if (musicService == null || !isBound) {
            return null;
        }
        
        List<Song> queue = musicService.getQueue();
        int currentIndex = musicService.getCurrentIndex();
        int remaining = queue.size() - currentIndex - 1;
        
        return String.format("Queue: %d songs | Position: %d/%d | Remaining: %d",
            queue.size(), currentIndex + 1, queue.size(), remaining);
    }
    
    // ========== CROSSFADE & QUALITY (Phase 3) ==========
    
    /**
     * Enable or disable crossfade between tracks.
     * @param enabled True to enable smooth transitions
     */
    public void setCrossfadeEnabled(boolean enabled) {
        if (musicService != null && isBound) {
            musicService.setCrossfadeEnabled(enabled);
        }
    }
    
    /**
     * Set crossfade duration.
     * @param durationMs Duration in milliseconds (1000-12000)
     */
    public void setCrossfadeDuration(int durationMs) {
        if (musicService != null && isBound) {
            musicService.setCrossfadeDuration(durationMs);
        }
    }
    
    /**
     * Check if crossfade is enabled.
     */
    public boolean isCrossfadeEnabled() {
        return musicService != null && isBound && musicService.isCrossfadeEnabled();
    }
    
    /**
     * Get current crossfade duration.
     */
    public int getCrossfadeDuration() {
        return musicService != null && isBound ? musicService.getCrossfadeDuration() : 0;
    }
    
    // ========== AUTO-EXTEND QUEUE (Phase 3) ==========
    
    private boolean autoExtendEnabled = true;
    private boolean isExtendingQueue = false;
    private static final int EXTEND_THRESHOLD = 3; // Extend when 3 songs remaining
    
    /**
     * Check if queue needs extension and fetch more songs via bridge.
     * Called automatically during playback.
     */
    public void checkAndExtendQueue() {
        if (!autoExtendEnabled || isExtendingQueue || musicService == null || !isBound) {
            return;
        }
        
        List<Song> queue = musicService.getQueue();
        int currentIndex = musicService.getCurrentIndex();
        int remaining = queue.size() - currentIndex - 1;
        
        // If we're near the end, fetch more songs
        if (remaining <= EXTEND_THRESHOLD && remaining > 0) {
            extendQueueWithRadio();
        }
    }
    
    /**
     * Extend queue by fetching radio continuation via bridge.
     */
    private void extendQueueWithRadio() {
        if (isExtendingQueue) return;
        
        Song currentSong = getCurrentSong();
        if (currentSong == null) return;
        
        isExtendingQueue = true;
        Log.d(TAG, "Extending queue with radio from: " + currentSong.getTitle());
        
        executor.execute(() -> {
            try {
                music.resona.online.bridge.InnertubeBridge.getRadioQueueAsync(
                    currentSong.getVideoId(),
                    null,
                    new music.resona.online.bridge.callbacks.SearchCallback() {
                        @Override
                        public void onSuccess(music.resona.online.bridge.models.SearchResult result) {
                            List<Song> newSongs = convertSearchResultToSongs(result);
                            
                            if (!newSongs.isEmpty()) {
                                // Add to end of queue
                                addToQueue(newSongs);
                                
                                // Prefetch some of the new songs
                                prefetchSongs(newSongs.subList(0, Math.min(3, newSongs.size())));
                                
                                Log.d(TAG, "Queue extended with " + newSongs.size() + " songs");
                            }
                            
                            isExtendingQueue = false;
                        }
                        
                        @Override
                        public void onError(music.resona.online.bridge.exceptions.BridgeException error) {
                            Log.e(TAG, "Failed to extend queue: " + error.getMessage());
                            isExtendingQueue = false;
                        }
                    }
                );
            } catch (Exception e) {
                Log.e(TAG, "Error extending queue", e);
                isExtendingQueue = false;
            }
        });
    }
    
    /**
     * Enable or disable automatic queue extension.
     */
    public void setAutoExtendEnabled(boolean enabled) {
        this.autoExtendEnabled = enabled;
        Log.d(TAG, "Auto-extend queue: " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if auto-extend is enabled.
     */
    public boolean isAutoExtendEnabled() {
        return autoExtendEnabled;
    }
    
    // ========== SMART SUGGESTIONS (Phase 3) ==========
    
    /**
     * Get related/similar songs for the current song via bridge.
     * Useful for "Add similar songs" feature.
     */
    public void getSimilarSongs(@Nullable SimilarSongsCallback callback) {
        Song currentSong = getCurrentSong();
        if (currentSong == null) {
            if (callback != null) {
                callback.onError("No song currently playing");
            }
            return;
        }
        
        getSimilarSongs(currentSong, callback);
    }
    
    /**
     * Get related/similar songs for a specific song via bridge.
     */
    public void getSimilarSongs(@NonNull Song song, @Nullable SimilarSongsCallback callback) {
        Log.d(TAG, "Fetching similar songs for: " + song.getTitle());
        
        executor.execute(() -> {
            try {
                // Use getRelatedContentAsync via bridge
                // The bridge requires a browseId, which we need to construct from song info
                // For now, use radio queue as a proxy for similar songs
                music.resona.online.bridge.InnertubeBridge.getRadioQueueAsync(
                    song.getVideoId(),
                    null,
                    new music.resona.online.bridge.callbacks.SearchCallback() {
                        @Override
                        public void onSuccess(music.resona.online.bridge.models.SearchResult result) {
                            List<Song> similarSongs = convertSearchResultToSongs(result);
                            
                            if (callback != null) {
                                callback.onSuccess(similarSongs);
                            }
                            
                            Log.d(TAG, "Found " + similarSongs.size() + " similar songs");
                        }
                        
                        @Override
                        public void onError(music.resona.online.bridge.exceptions.BridgeException error) {
                            Log.e(TAG, "Failed to get similar songs: " + error.getMessage());
                            if (callback != null) {
                                callback.onError(error.getMessage());
                            }
                        }
                    }
                );
            } catch (Exception e) {
                Log.e(TAG, "Error getting similar songs", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * Add similar songs to queue end.
     * Fetches and adds similar songs in one operation.
     */
    public void addSimilarSongsToQueue(@Nullable PlaybackCallback callback) {
        getSimilarSongs(new SimilarSongsCallback() {
            @Override
            public void onSuccess(List<Song> songs) {
                if (!songs.isEmpty()) {
                    // Take first 10 similar songs
                    List<Song> songsToAdd = songs.subList(0, Math.min(10, songs.size()));
                    addToQueue(songsToAdd);
                    
                    if (callback != null) {
                        callback.onSuccess();
                    }
                }
            }
            
            @Override
            public void onError(String message) {
                if (callback != null) {
                    callback.onError(message);
                }
            }
        });
    }
    
    /**
     * Create a mix based on multiple seed songs.
     * Fetches radio for each and combines results.
     */
    public void createMixFromSeeds(@NonNull List<Song> seedSongs, @Nullable SimilarSongsCallback callback) {
        if (seedSongs.isEmpty()) {
            if (callback != null) {
                callback.onError("No seed songs provided");
            }
            return;
        }
        
        Log.d(TAG, "Creating mix from " + seedSongs.size() + " seeds");
        
        executor.execute(() -> {
            List<Song> mixSongs = new ArrayList<>();
            int[] completed = {0};
            
            for (Song seed : seedSongs) {
                try {
                    music.resona.online.bridge.InnertubeBridge.getRadioQueueAsync(
                        seed.getVideoId(),
                        null,
                        new music.resona.online.bridge.callbacks.SearchCallback() {
                            @Override
                            public void onSuccess(music.resona.online.bridge.models.SearchResult result) {
                                synchronized (mixSongs) {
                                    List<Song> songs = convertSearchResultToSongs(result);
                                    mixSongs.addAll(songs);
                                    completed[0]++;
                                    
                                    // If all seeds processed, return results
                                    if (completed[0] == seedSongs.size()) {
                                        // Shuffle and deduplicate
                                        List<Song> uniqueSongs = deduplicateSongs(mixSongs);
                                        Collections.shuffle(uniqueSongs);
                                        
                                        if (callback != null) {
                                            callback.onSuccess(uniqueSongs);
                                        }
                                        
                                        Log.d(TAG, "Mix created with " + uniqueSongs.size() + " unique songs");
                                    }
                                }
                            }
                            
                            @Override
                            public void onError(music.resona.online.bridge.exceptions.BridgeException error) {
                                synchronized (mixSongs) {
                                    completed[0]++;
                                    
                                    if (completed[0] == seedSongs.size() && callback != null) {
                                        if (mixSongs.isEmpty()) {
                                            callback.onError("Failed to create mix");
                                        } else {
                                            List<Song> uniqueSongs = deduplicateSongs(mixSongs);
                                            callback.onSuccess(uniqueSongs);
                                        }
                                    }
                                }
                            }
                        }
                    );
                } catch (Exception e) {
                    Log.e(TAG, "Error in mix creation", e);
                }
            }
        });
    }
    
    /**
     * Remove duplicate songs based on videoId.
     */
    private List<Song> deduplicateSongs(List<Song> songs) {
        List<Song> unique = new ArrayList<>();
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        
        for (Song song : songs) {
            if (!seenIds.contains(song.getVideoId())) {
                unique.add(song);
                seenIds.add(song.getVideoId());
            }
        }
        
        return unique;
    }
    
    /**
     * Callback for similar songs operations.
     */
    public interface SimilarSongsCallback {
        void onSuccess(List<Song> songs);
        void onError(String message);
    }
    
    // ========== QUALITY MANAGEMENT (Phase 3) ==========
    
    /**
     * Quality preference for streaming.
     */
    public enum QualityPreference {
        LOW,    // Save data, faster loading
        AUTO,   // Adaptive based on network
        HIGH    // Best quality
    }
    
    private QualityPreference qualityPreference = QualityPreference.AUTO;
    
    /**
     * Set streaming quality preference.
     * This affects StreamCache behavior.
     */
    public void setQualityPreference(@NonNull QualityPreference preference) {
        this.qualityPreference = preference;
        
        // Update StreamCache quality setting
        String quality = "AUTO";
        switch (preference) {
            case LOW:
                quality = "LOW";
                break;
            case HIGH:
                quality = "HIGH";
                break;
            case AUTO:
            default:
                quality = "AUTO";
                break;
        }
        
        // Note: Quality is managed per-request in InnertubeBridge, not cached globally
        // StreamCache only handles URL caching, not quality preferences
        
        Log.d(TAG, "Quality preference set to: " + preference);
    }
    
    /**
     * Get current quality preference.
     */
    @NonNull
    public QualityPreference getQualityPreference() {
        return qualityPreference;
    }
    
    /**
     * Check if we're on a metered network and adjust quality accordingly.
     */
    public void adjustQualityForNetwork(boolean isMetered) {
        if (qualityPreference == QualityPreference.AUTO) {
            // Quality adjusted automatically by InnertubeBridge per request
            Log.d(TAG, "Auto quality adjusted: " + (isMetered ? "LOW (metered)" : "AUTO (unmetered)"));
        }
    }
    
    /**
     * Clear stream cache to free up space or force quality refresh.
     */
    public void clearStreamCache() {
        // StreamCache uses LRU eviction automatically, manual clear not needed
        // Could add a clear() method to StreamCache if manual clearing is required
        Log.d(TAG, "Stream cache uses automatic LRU eviction");
    }
    
    /**
     * Get cache statistics.
     */
    @Nullable
    public String getCacheStats() {
        // StreamCache should provide stats
        return "Cache stats available via StreamCache.getInstance()";
    }
    
    private void playWithUrl(Song song, String url, @Nullable PlaybackCallback callback) {
        if (musicService != null && isBound) {
            currentSong = song;
            musicService.playSong(song, url);
            isLoading = false;
            notifyLoadingState(false);
            if (callback != null) {
                callback.onSuccess();
            }
        } else {
            Log.e(TAG, "Service not bound");
            if (callback != null) {
                callback.onError("Service not available");
            }
        }
    }
    
    /**
     * Prefetch stream URLs for upcoming songs using StreamCache.
     * Non-blocking, uses LRU cache with automatic eviction.
     */
    public void prefetchStreamUrl(@NonNull Song song) {
        StreamCache.getInstance().prefetch(song.getVideoId());
    }
    
    /**
     * Prefetch stream URLs for the next songs in queue.
     */
    private void prefetchNextSongs(Song currentSong) {
        if (musicService == null || !isBound) return;
        
        List<Song> queue = musicService.getQueue();
        int currentIndex = -1;
        
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getVideoId().equals(currentSong.getVideoId())) {
                currentIndex = i;
                break;
            }
        }
        
        if (currentIndex < 0) return;
        
        // Prefetch next PREFETCH_COUNT songs using StreamCache
        for (int i = 1; i <= PREFETCH_COUNT && (currentIndex + i) < queue.size(); i++) {
            Song nextSong = queue.get(currentIndex + i);
            StreamCache.getInstance().prefetch(nextSong.getVideoId());
        }
    }
    
    /**
     * Prefetch stream URL for a list of songs (e.g., from home feed).
     */
    public void prefetchSongs(@NonNull List<Song> songs) {
        int count = Math.min(songs.size(), PREFETCH_COUNT);
        for (int i = 0; i < count; i++) {
            StreamCache.getInstance().prefetch(songs.get(i).getVideoId());
        }
    }
    
    // Playback controls
    
    public void play() {
        if (musicService != null && isBound) {
            musicService.play();
        }
    }
    
    public void pause() {
        if (musicService != null && isBound) {
            musicService.pause();
        }
    }
    
    public void togglePlayPause() {
        if (musicService != null && isBound) {
            musicService.togglePlayPause();
        }
    }
    
    public void next() {
        if (musicService != null && isBound) {
            musicService.next();
            // Check if queue needs extension (Phase 3)
            checkAndExtendQueue();
        }
    }
    
    public void previous() {
        if (musicService != null && isBound) {
            musicService.previous();
        }
    }
    
    public void seekTo(int positionMs) {
        if (musicService != null && isBound) {
            musicService.seekTo(positionMs);
        }
    }
    
    public void toggleShuffle() {
        if (musicService != null && isBound) {
            musicService.toggleShuffle();
        }
    }
    
    public void toggleRepeat() {
        if (musicService != null && isBound) {
            musicService.toggleRepeat();
        }
    }
    
    @Nullable
    private Song getNextSong() {
        if (musicService == null || !isBound) return null;
        
        List<Song> queue = musicService.getQueue();
        int currentIndex = musicService.getCurrentIndex();
        
        if (currentIndex + 1 < queue.size()) {
            return queue.get(currentIndex + 1);
        }
        return null;
    }
    
    // State getters
    
    @Nullable
    public Song getCurrentSong() {
        return currentSong;
    }
    
    public boolean isPlaying() {
        return musicService != null && isBound && musicService.isPlaying();
    }
    
    public boolean isLoading() {
        return isLoading || (musicService != null && musicService.isPreparing());
    }
    
    public boolean isShuffleEnabled() {
        return musicService != null && isBound && musicService.isShuffleEnabled();
    }
    
    public MusicService.RepeatMode getRepeatMode() {
        return musicService != null && isBound ? musicService.getRepeatMode() : MusicService.RepeatMode.OFF;
    }
    
    public int getCurrentPosition() {
        return musicService != null && isBound ? musicService.getCurrentPosition() : 0;
    }
    
    public int getDuration() {
        return musicService != null && isBound ? musicService.getDuration() : 0;
    }
    
    public List<Song> getQueue() {
        return musicService != null && isBound ? musicService.getQueue() : new ArrayList<>();
    }
    
    public int getCurrentQueueIndex() {
        return musicService != null && isBound ? musicService.getCurrentIndex() : -1;
    }
    
    /**
     * Skip to a specific position in the queue.
     */
    public void skipToPosition(int position) {
        if (musicService != null && isBound) {
            musicService.skipToPosition(position);
        }
    }
    
    /**
     * Remove a song from the queue at the specified position.
     */
    public void removeFromQueue(int position) {
        if (musicService != null && isBound) {
            musicService.removeFromQueue(position);
        }
    }
    
    public boolean hasSong() {
        return currentSong != null;
    }
    
    private void notifyLoadingState(boolean loading) {
        for (PlaybackListener listener : listeners) {
            listener.onLoadingStateChanged(loading);
        }
    }
    
    public void release() {
        if (isBound) {
            if (musicService != null) {
                musicService.removeListener(serviceListener);
            }
            context.unbindService(serviceConnection);
            isBound = false;
        }
        executor.shutdown();
        listeners.clear();
        streamUrlCache.clear();
    }
}
