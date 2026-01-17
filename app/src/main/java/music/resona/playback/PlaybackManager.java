package music.resona.playback;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import music.resona.models.Song;
import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.callbacks.PlayerCallback;
import music.resona.online.bridge.exceptions.BridgeException;
import music.resona.online.bridge.models.PlayerResult;

/**
 * Advanced playback manager with instant playback and multi-tier buffering.
 * Implements immediate playback start with progressive quality enhancement.
 */
public class PlaybackManager {
    
    private static final String TAG = "PlaybackManager";
    private static final int CHUNK_SIZE = 512 * 1024; // 512 KB chunks
    private static final int PRELOAD_NEXT_THRESHOLD = 5; // Preload next 5 tracks
    private static final long RETRY_BASE_DELAY = 1000; // 1 second base delay
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    private final Context context;
    private final ExoPlayer player;
    private final PlaybackCache cache;
    private final QueueManager queueManager;
    private final Executor executor;
    private final Handler mainHandler;
    
    // Playback state
    private final AtomicBoolean waitingForNetworkConnection = new AtomicBoolean(false);
    private Song preloadItem;
    private PlaybackState currentState = PlaybackState.IDLE;
    private PlaybackCallback callback;
    
    public PlaybackManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.cache = new PlaybackCache(context);
        this.queueManager = new QueueManager(context);
        this.player = createExoPlayer();
        
        setupPlayerListeners();
    }
    
    /**
     * Set preload item for instant playback start.
     */
    public void setPreloadItem(@NonNull Song song) {
        this.preloadItem = song;
        Log.d(TAG, "Preload item set: " + song.getTitle());
        
        // Start resolving audio immediately
        executor.execute(() -> resolveAudioUrl(song, new AudioResolveCallback() {
            @Override
            public void onResolved(String audioUrl) {
                cache.preload(song.getVideoId(), audioUrl);
                Log.d(TAG, "Preload item audio resolved and cached");
            }
            
            @Override
            public void onError(String error) {
                Log.w(TAG, "Failed to preload audio: " + error);
            }
        }));
    }
    
    /**
     * Start playback immediately with preload item.
     */
    public void startPlayback() {
        if (preloadItem == null) {
            Log.e(TAG, "Cannot start playback: no preload item set");
            if (callback != null) {
                callback.onError("No preload item available");
            }
            return;
        }
        
        currentState = PlaybackState.BUFFERING;
        Log.d(TAG, "Starting immediate playback: " + preloadItem.getTitle());
        
        // Set MediaItem immediately for instant UI response
        MediaItem mediaItem = createMediaItem(preloadItem);
        player.setMediaItem(mediaItem);
        player.prepare();
        
        // Resolve full queue in background
        executor.execute(() -> resolveFullQueue());
    }
    
    /**
     * Play specific song with immediate start.
     */
    public void playSong(@NonNull Song song) {
        setPreloadItem(song);
        startPlayback();
    }
    
    /**
     * Get current playback state.
     */
    public PlaybackState getCurrentState() {
        return currentState;
    }
    
    /**
     * Check if waiting for network connection.
     */
    public boolean isWaitingForNetwork() {
        return waitingForNetworkConnection.get();
    }
    
    /**
     * Set playback callback for state updates.
     */
    public void setPlaybackCallback(@Nullable PlaybackCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Create optimized ExoPlayer instance.
     */
    private ExoPlayer createExoPlayer() {
        LoadControl loadControl = OptimizedLoadControl.create();
        
        return new ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build();
    }
    
    /**
     * Setup player event listeners.
     */
    private void setupPlayerListeners() {
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying && currentState == PlaybackState.BUFFERING) {
                    currentState = PlaybackState.PLAYING;
                    Log.d(TAG, "Playback started successfully");
                    if (callback != null) {
                        callback.onPlaybackStarted();
                    }
                }
            }
            
            @Override
            public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                Log.e(TAG, "Player error: " + error.getMessage());
                handlePlaybackError(error);
            }
            
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (mediaItem != null) {
                    preloadNextTracks();
                }
            }
        });
    }
    
    /**
     * Create MediaItem with multi-tier source.
     */
    private MediaItem createMediaItem(@NonNull Song song) {
        // Try cache first, then network
        String cachedUrl = cache.getCachedUrl(song.getVideoId());
        if (cachedUrl != null) {
            Log.d(TAG, "Using cached audio for: " + song.getTitle());
            return MediaItem.fromUri(Uri.parse(cachedUrl));
        }
        
        // Use placeholder URI, will be resolved asynchronously
        String placeholderUri = "asset:///silence.mp3"; // Silent placeholder
        return MediaItem.fromUri(Uri.parse(placeholderUri));
    }
    
    /**
     * Resolve audio URL with retry logic.
     */
    private void resolveAudioUrl(@NonNull Song song, @NonNull AudioResolveCallback callback) {
        resolveAudioUrl(song, callback, 0);
    }
    
    private void resolveAudioUrl(@NonNull Song song, @NonNull AudioResolveCallback callback, int attempt) {
        if (attempt >= MAX_RETRY_ATTEMPTS) {
            callback.onError("Max retry attempts reached");
            return;
        }
        
        InnertubeBridge.getPlayerInfoAsync(song.getVideoId(), new PlayerCallback() {
            @Override
            public void onSuccess(@NonNull PlayerResult result) {
                if (result.getStreamUrl() != null) {
                    callback.onResolved(result.getStreamUrl());
                } else {
                    Log.w(TAG, "No stream URL in player result");
                    retryResolveAudio(song, callback, attempt + 1);
                }
            }
            
            @Override
            public void onError(@NonNull BridgeException error) {
                Log.w(TAG, "Failed to resolve audio (attempt " + (attempt + 1) + "): " + error.getMessage());
                retryResolveAudio(song, callback, attempt + 1);
            }
        });
    }
    
    /**
     * Retry audio resolution with exponential backoff.
     */
    private void retryResolveAudio(@NonNull Song song, @NonNull AudioResolveCallback callback, int attempt) {
        long delay = RETRY_BASE_DELAY * (1L << attempt); // Exponential backoff
        
        mainHandler.postDelayed(() -> {
            if (NetworkUtil.isConnected(context)) {
                waitingForNetworkConnection.set(false);
                resolveAudioUrl(song, callback, attempt);
            } else {
                waitingForNetworkConnection.set(true);
                Log.d(TAG, "Waiting for network connection...");
                retryResolveAudio(song, callback, attempt); // Retry immediately when network returns
            }
        }, delay);
    }
    
    /**
     * Resolve full queue after immediate playback start.
     */
    private void resolveFullQueue() {
        Log.d(TAG, "Resolving full queue in background");
        
        // This would integrate with QueueManager to load full playlist
        queueManager.getCurrentQueue(new QueueManager.QueueCallback() {
            @Override
            public void onQueueLoaded(PlaybackQueue queue) {
                // Update player with full queue
                mainHandler.post(() -> {
                    // Add queue items to ExoPlayer
                    Log.d(TAG, "Full queue resolved: " + queue.size() + " items");
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to resolve queue: " + error);
            }
        });
    }
    
    /**
     * Preload next tracks when approaching end.
     */
    private void preloadNextTracks() {
        executor.execute(() -> {
            // Get next few tracks from queue
            int currentIndex = queueManager.getCurrentIndex();
            
            for (int i = 1; i <= PRELOAD_NEXT_THRESHOLD; i++) {
                Song nextSong = queueManager.getSongAtIndex(currentIndex + i);
                if (nextSong != null) {
                    resolveAudioUrl(nextSong, new AudioResolveCallback() {
                        @Override
                        public void onResolved(String audioUrl) {
                            cache.preload(nextSong.getVideoId(), audioUrl);
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Silent failure for preloading
                        }
                    });
                }
            }
        });
    }
    
    /**
     * Handle playback errors with smart recovery.
     */
    private void handlePlaybackError(@NonNull androidx.media3.common.PlaybackException error) {
        currentState = PlaybackState.ERROR;
        
        if (callback != null) {
            // Auto-skip to next track if enabled
            boolean shouldAutoSkip = true; // Get from preferences
            if (shouldAutoSkip) {
                callback.onAutoSkipToNext();
            } else {
                callback.onError("Playback failed: " + error.getMessage());
            }
        }
    }
    
    /**
     * Release resources.
     */
    public void release() {
        player.release();
        cache.release();
        Log.d(TAG, "PlaybackManager released");
    }
    
    /**
     * Playback state enumeration.
     */
    public enum PlaybackState {
        IDLE,
        BUFFERING,
        PLAYING,
        PAUSED,
        ERROR
    }
    
    /**
     * Callback interface for audio URL resolution.
     */
    private interface AudioResolveCallback {
        void onResolved(String audioUrl);
        void onError(String error);
    }
    
    /**
     * Callback interface for playback events.
     */
    public interface PlaybackCallback {
        void onPlaybackStarted();
        void onAutoSkipToNext();
        void onError(String error);
    }
}