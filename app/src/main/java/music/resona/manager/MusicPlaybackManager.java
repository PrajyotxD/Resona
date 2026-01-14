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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import music.resona.cache.SongPrefetchHelper;
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
        listeners.add(listener);
    }
    
    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Play a single song. Fetches stream URL and starts playback.
     */
    public void playSong(@NonNull Song song, @Nullable PlaybackCallback callback) {
        Log.d(TAG, "Playing song: " + song.getTitle());
        
        // Check if we have a cached stream URL in our local cache
        StreamUrlCache cached = streamUrlCache.get(song.getVideoId());
        if (cached != null && cached.isValid()) {
            Log.d(TAG, "Using cached stream URL for: " + song.getTitle());
            playWithUrl(song, cached.url, callback);
            return;
        }
        
        // Check if song has cached URL
        String cachedUrl = song.getCachedStreamUrl();
        if (cachedUrl != null) {
            Log.d(TAG, "Using song's cached stream URL: " + song.getTitle());
            playWithUrl(song, cachedUrl, callback);
            return;
        }
        
        // Check the SongPrefetchHelper cache (from home feed prefetching)
        String prefetchedUrl = SongPrefetchHelper.getInstance().getCachedStreamUrl(song.getVideoId());
        if (prefetchedUrl != null) {
            Log.d(TAG, "Using prefetched stream URL for: " + song.getTitle());
            long expiryTime = SongPrefetchHelper.getInstance().getCacheExpiry(song.getVideoId());
            // Cache locally too
            streamUrlCache.put(song.getVideoId(), new StreamUrlCache(prefetchedUrl, expiryTime));
            song.setCachedStreamUrl(prefetchedUrl, expiryTime);
            playWithUrl(song, prefetchedUrl, callback);
            return;
        }
        
        // Fetch stream URL
        isLoading = true;
        notifyLoadingState(true);
        
        executor.execute(() -> {
            try {
                PlaybackDataResult result = InnertubeBridge.getPlaybackDataSync(song.getVideoId());
                
                if (result != null && result.getStreamUrl() != null && !result.getStreamUrl().isEmpty()) {
                    String streamUrl = result.getStreamUrl();
                    long expiryTime = System.currentTimeMillis() + (result.getStreamExpiresInSeconds() * 1000L);
                    
                    // Cache the URL
                    streamUrlCache.put(song.getVideoId(), new StreamUrlCache(streamUrl, expiryTime));
                    song.setCachedStreamUrl(streamUrl, expiryTime);
                    
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        playWithUrl(song, streamUrl, callback);
                        // Prefetch next songs
                        prefetchNextSongs(song);
                    });
                } else {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        isLoading = false;
                        notifyLoadingState(false);
                        if (callback != null) {
                            callback.onError("Failed to get stream URL");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching stream URL", e);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    isLoading = false;
                    notifyLoadingState(false);
                    if (callback != null) {
                        callback.onError("Error: " + e.getMessage());
                    }
                });
            }
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
     * Prefetch stream URLs for upcoming songs for instant playback.
     */
    public void prefetchStreamUrl(@NonNull Song song) {
        if (streamUrlCache.containsKey(song.getVideoId())) {
            StreamUrlCache cached = streamUrlCache.get(song.getVideoId());
            if (cached != null && cached.isValid()) {
                return; // Already cached
            }
        }
        
        executor.execute(() -> {
            try {
                PlaybackDataResult result = InnertubeBridge.getPlaybackDataSync(song.getVideoId());
                if (result != null && result.getStreamUrl() != null) {
                    long expiryTime = System.currentTimeMillis() + (result.getStreamExpiresInSeconds() * 1000L);
                    streamUrlCache.put(song.getVideoId(), new StreamUrlCache(result.getStreamUrl(), expiryTime));
                    song.setCachedStreamUrl(result.getStreamUrl(), expiryTime);
                    Log.d(TAG, "Prefetched stream URL for: " + song.getTitle());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error prefetching: " + song.getTitle(), e);
            }
        });
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
        
        // Prefetch next PREFETCH_COUNT songs
        for (int i = 1; i <= PREFETCH_COUNT && (currentIndex + i) < queue.size(); i++) {
            Song nextSong = queue.get(currentIndex + i);
            prefetchStreamUrl(nextSong);
        }
    }
    
    /**
     * Prefetch stream URL for a list of songs (e.g., from home feed).
     */
    public void prefetchSongs(@NonNull List<Song> songs) {
        int count = Math.min(songs.size(), PREFETCH_COUNT);
        for (int i = 0; i < count; i++) {
            prefetchStreamUrl(songs.get(i));
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
            Song nextSong = getNextSong();
            if (nextSong != null) {
                playSong(nextSong, null);
            } else {
                musicService.next();
            }
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
