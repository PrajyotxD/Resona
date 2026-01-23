package music.resona.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import music.resona.MainActivity;
import music.resona.R;
import music.resona.manager.QueueManager;
import music.resona.manager.QueuePersistence;
import music.resona.models.Song;
import music.resona.cache.StreamCache;
import music.resona.database.QuickPicksDatabase;
import music.resona.database.RecommendationDatabase;
import music.resona.online.bridge.InnertubeBridge;

/**
 * Modern foreground service for background music playback using ExoPlayer2.
 * Handles queue management, shuffle, repeat, and optimized streaming performance.
 */
public class MusicService extends Service implements Player.Listener {
    
    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "music_playback_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 500;
    
    // Notification actions
    public static final String ACTION_PLAY = "music.resona.PLAY";
    public static final String ACTION_PAUSE = "music.resona.PAUSE";
    public static final String ACTION_NEXT = "music.resona.NEXT";
    public static final String ACTION_PREVIOUS = "music.resona.PREVIOUS";
    
    // Playback components
    private ExoPlayer exoPlayer;
    private MediaSessionCompat mediaSession;
    private Bitmap currentAlbumArt;
    private final QueueManager queueManager = new QueueManager();
    private QueuePersistence queuePersistence;
    private QuickPicksDatabase database;
    private RecommendationDatabase recommendationDb;
    
    // Volume control
    private float currentVolume = 1.0f;
    
    // Sleep timer
    private Handler sleepTimerHandler;
    private Runnable sleepTimerRunnable;
    private long sleepTimerEndTime = 0;
    
    // Audio focus
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean wasPlayingBeforeFocusLoss = false;
    private boolean isUserInitiatedPlay = false; // Track if play was user-initiated
    
    // State management
    private boolean isPlaying = false;
    private boolean isPreparing = false;
    private boolean isTransitioning = false;
    private long songStartTime = 0;
    private int lastTrackedPosition = 0;
    
    // Repeat and shuffle modes
    public enum RepeatMode {
        OFF(0),
        ONE(1),
        ALL(2);
        
        private final int value;
        RepeatMode(int value) { this.value = value; }
        public int getValue() { return value; }
        
        public static RepeatMode fromInt(int value) {
            for (RepeatMode mode : values()) {
                if (mode.getValue() == value) return mode;
            }
            return OFF;
        }
    }
    
    private RepeatMode repeatMode = RepeatMode.OFF;
    
    // Progress tracking
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null && isPlaying && !isPreparing) {
                try {
                    long current = exoPlayer.getCurrentPosition();
                    long duration = exoPlayer.getDuration();
                    notifyProgressChanged((int) current, (int) duration);
                    
                    // Auto-track song completion for analytics
                    if (duration > 0 && current > duration * 0.8) {
                        Song currentSong = getCurrentSong();
                        if (currentSong != null && current > lastTrackedPosition + 10000) {
                            trackSongCompletion(currentSong, (int) current, (int) duration);
                            lastTrackedPosition = (int) current;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error tracking progress", e);
                }
            }
            progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
        }
    };
    
    // Listeners and callbacks
    private final List<MusicServiceListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    // Service binding
    private final IBinder binder = new MusicBinder();
    
    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }
    
    // Service Lifecycle
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MusicService created");
        
        createNotificationChannel();
        
        // Initialize audio manager for audio focus
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        // Initialize sleep timer handler
        sleepTimerHandler = new Handler(Looper.getMainLooper());
        
        queuePersistence = new QueuePersistence(this);
        database = QuickPicksDatabase.getInstance(this);
        recommendationDb = RecommendationDatabase.getInstance(this);
        initializeMediaSession();
        initializeExoPlayer();
        registerBroadcastReceiver();
        
        // Start progress tracking
        progressHandler.post(progressRunnable);
        
        // Load saved queue and position
        restoreQueueState();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            handleNotificationAction(action);
        }
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "MusicService destroyed");
        
        saveQueueState();
        progressHandler.removeCallbacks(progressRunnable);
        
        // Cancel sleep timer
        cancelSleepTimer();
        
        // Abandon audio focus
        abandonAudioFocus();
        
        if (exoPlayer != null) {
            exoPlayer.removeListener(this);
            exoPlayer.release();
            exoPlayer = null;
        }
        
        if (mediaSession != null) {
            mediaSession.release();
        }
        
        unregisterBroadcastReceiver();
        executor.shutdown();
        
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    // ExoPlayer Initialization
    private void initializeExoPlayer() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build();
        
        exoPlayer = new ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build();
        
        exoPlayer.addListener(this);
        
        Log.d(TAG, "ExoPlayer initialized successfully");
    }
    
    // ExoPlayer Event Listeners
    @Override
    public void onPlaybackStateChanged(int playbackState) {
        switch (playbackState) {
            case Player.STATE_READY:
                if (isPreparing) {
                    isPreparing = false;
                    isPlaying = exoPlayer.getPlayWhenReady();
                    songStartTime = System.currentTimeMillis();
                    Log.d(TAG, "ExoPlayer ready, starting playback");
                    updateNotification();
                    notifyPlaybackStateChanged();
                }
                break;
                
            case Player.STATE_BUFFERING:
                Log.d(TAG, "ExoPlayer buffering...");
                break;
                
            case Player.STATE_ENDED:
                Log.d(TAG, "Song ended, moving to next");
                handleSongCompletion();
                break;
                
            case Player.STATE_IDLE:
                Log.d(TAG, "ExoPlayer idle");
                break;
        }
    }
    
    @Override
    public void onPlayerError(PlaybackException error) {
        Log.e(TAG, "ExoPlayer error: " + error.getMessage(), error);
        isPreparing = false;
        isPlaying = false;
        notifyPlaybackStateChanged();
        updateNotification();
        
        // Try next song on error
        if (queueManager.hasNext()) {
            next();
        }
    }
    
    @Override
    public void onIsPlayingChanged(boolean isPlayingNow) {
        isPlaying = isPlayingNow;
        
        // Record playback start in database for personalization
        if (isPlayingNow && queueManager.getCurrentSong() != null) {
            Song currentSong = queueManager.getCurrentSong();
            songStartTime = System.currentTimeMillis();
            lastTrackedPosition = 0;
            
            // Record song in database with play event
            new Thread(() -> {
                try {
                    String artist = currentSong.getArtist();
                    if (artist == null || artist.trim().isEmpty()) {
                        artist = "Unknown Artist";
                    }
                    database.insertOrUpdateSong(
                        currentSong.getVideoId(),
                        currentSong.getTitle(),
                        artist,
                        "", // artistId - we don't have it here
                        currentSong.getThumbnailUrl(),
                        currentSong.getDurationSeconds()
                    );
                    Log.d(TAG, "Recorded song in database: " + currentSong.getTitle() + " by " + artist);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to record song in database", e);
                }
            }).start();
        }
        
        updateNotification();
        notifyPlaybackStateChanged();
    }
    
    // Media Session Setup
    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, "MusicService");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                play();
            }
            
            @Override
            public void onPause() {
                pause();
            }
            
            @Override
            public void onSkipToNext() {
                next();
            }
            
            @Override
            public void onSkipToPrevious() {
                previous();
            }
            
            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }
        });
        
        mediaSession.setActive(true);
    }
    
    // Public API Methods
    public void playSong(Song song) {
        Log.d(TAG, "Playing song: " + song.getTitle());
        
        // Check if song exists in current queue
        List<Song> activeQueue = queueManager.getActiveQueue();
        int index = activeQueue.indexOf(song);
        if (index >= 0) {
            queueManager.setCurrentIndex(index);
            playCurrent();
        } else {
            // Create new queue with this single song
            List<Song> newQueue = new ArrayList<>();
            newQueue.add(song);
            queueManager.setQueue(newQueue, 0);
            playCurrent();
        }
    }
    
    public void playSong(Song song, String streamUrl) {
        // Direct play with stream URL - used by MusicPlaybackManager
        Log.d(TAG, "Playing song with URL: " + song.getTitle());
        playUrl(streamUrl, song);
    }
    
    public void playQueue(List<Song> queue, int startIndex) {
        Log.d(TAG, "Playing queue with " + queue.size() + " songs, starting at index " + startIndex);
        
        queueManager.setQueue(new ArrayList<>(queue), startIndex);
        playCurrent();
    }
    
    public void setQueue(List<Song> queue, int startIndex) {
        Log.d(TAG, "Setting queue with " + queue.size() + " songs");
        queueManager.setQueue(new ArrayList<>(queue), startIndex);
        notifyQueueChanged();
    }
    
    private void playCurrent() {
        Song song = getCurrentSong();
        if (song == null) {
            Log.w(TAG, "No current song to play");
            return;
        }
        
        isPreparing = true;
        isTransitioning = false;
        lastTrackedPosition = 0;
        
        executor.execute(() -> {
            long startTime = System.currentTimeMillis();
            String streamUrl = StreamCache.getInstance().getStreamUrl(song.getVideoId());
            long fetchTime = System.currentTimeMillis() - startTime;
            
            Log.d(TAG, "Stream URL fetch took: " + fetchTime + "ms");
            
            if (streamUrl != null) {
                runOnUiThread(() -> playUrl(streamUrl, song));
            } else {
                Log.e(TAG, "Failed to get stream URL for: " + song.getTitle());
                isPreparing = false;
                next();
            }
        });
    }
    
    private void playUrl(String url, Song song) {
        try {
            Log.d(TAG, "Starting ExoPlayer with URL: " + url.substring(0, Math.min(100, url.length())));
            
            MediaItem mediaItem = MediaItem.fromUri(url);
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);
            
            updateNotification();
            notifySongChanged(song);
            
            // Prefetch next song for smoother transitions
            prefetchNextSong();
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing URL", e);
            isPreparing = false;
            next();
        }
    }
    
    public void play() {
        if (exoPlayer != null) {
            // ExoPlayer handles audio focus automatically via setAudioAttributes(attrs, true)
            // No need for manual requestAudioFocus() - it causes dual listener conflicts
            exoPlayer.setPlayWhenReady(true);
            startForeground(NOTIFICATION_ID, buildNotification());
        }
    }
    
    public void pause() {
        if (exoPlayer != null) {
            // ExoPlayer handles audio focus automatically
            // No need for manual abandonAudioFocus()
            exoPlayer.setPlayWhenReady(false);
        }
    }
    
    public void next() {
        if (exoPlayer != null && exoPlayer.getCurrentPosition() > 3000) {
            exoPlayer.seekTo(0);
        }
        
        if (repeatMode == RepeatMode.ONE) {
            playCurrent();
            return;
        }
        
        if (queueManager.hasNext()) {
            // Track song transition for recommendations
            Song currentSong = queueManager.getCurrentSong();
            queueManager.moveToNext();
            Song nextSong = queueManager.getCurrentSong();
            
            if (currentSong != null && nextSong != null) {
                // Record relationship between consecutive songs
                executor.execute(() -> {
                    try {
                        database.recordSongRelationship(currentSong.getVideoId(), nextSong.getVideoId());
                        Log.d(TAG, "Recorded song transition: " + currentSong.getTitle() + " → " + nextSong.getTitle());
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to record song relationship", e);
                    }
                });
            }
            
            playCurrent();
        }
    }
    
    public void previous() {
        if (queueManager.hasPrevious()) {
            queueManager.moveToPrevious();
            playCurrent();
        }
    }
    
    public void seekTo(int positionMs) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(positionMs);
        }
    }
    
    // Volume Control
    public void setVolume(float volume) {
        currentVolume = Math.max(0f, Math.min(1f, volume)); // Clamp between 0 and 1
        if (exoPlayer != null) {
            exoPlayer.setVolume(currentVolume);
        }
        Log.d(TAG, "Volume set to: " + currentVolume);
    }
    
    public float getVolume() {
        return currentVolume;
    }
    
    public void increaseVolume() {
        setVolume(currentVolume + 0.1f);
    }
    
    public void decreaseVolume() {
        setVolume(currentVolume - 0.1f);
    }
    
    // Queue Management
    public QueueManager getQueueManager() {
        return queueManager;
    }
    
    public Song getCurrentSong() {
        return queueManager.getCurrentSong();
    }
    
    public List<Song> getQueue() {
        return queueManager.getActiveQueue();
    }
    
    public int getCurrentIndex() {
        return queueManager.getCurrentIndex();
    }
    
    public void skipToPosition(int position) {
        List<Song> activeQueue = queueManager.getActiveQueue();
        if (position >= 0 && position < activeQueue.size()) {
            queueManager.setCurrentIndex(position);
            playCurrent();
        }
    }
    
    public void removeFromQueue(int position) {
        if (queueManager.removeAt(position)) {
            notifyQueueChanged();
        }
    }
    
    // Play Next - Insert after current song
    public void playNext(Song song) {
        queueManager.addNext(song);
        notifyQueueChanged();
        Log.d(TAG, "Added song to play next: " + song.getTitle());
    }
    
    public void playNext(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return;
        // Add in reverse order so they appear in correct order after current
        for (int i = songs.size() - 1; i >= 0; i--) {
            queueManager.addNext(songs.get(i));
        }
        notifyQueueChanged();
        Log.d(TAG, "Added " + songs.size() + " songs to play next");
    }
    
    // Add to Queue - Append to end
    public void addToQueue(Song song) {
        List<Song> singleSong = new ArrayList<>();
        singleSong.add(song);
        queueManager.addToQueue(singleSong);
        notifyQueueChanged();
        Log.d(TAG, "Added song to queue: " + song.getTitle());
    }
    
    public void addToQueue(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return;
        queueManager.addToQueue(songs);
        notifyQueueChanged();
        Log.d(TAG, "Added " + songs.size() + " songs to queue");
    }
    
    public boolean isPreparing() {
        return isPreparing;
    }
    
    public int getCurrentPosition() {
        return exoPlayer != null ? (int) exoPlayer.getCurrentPosition() : 0;
    }
    
    public int getDuration() {
        return exoPlayer != null ? (int) exoPlayer.getDuration() : 0;
    }
    
    public boolean isPlaying() {
        return isPlaying;
    }
    
    // Repeat and Shuffle
    public void setRepeatMode(RepeatMode mode) {
        this.repeatMode = mode;
        updateNotification();
    }
    
    public RepeatMode getRepeatMode() {
        return repeatMode;
    }
    
    public void toggleShuffle() {
        queueManager.toggleShuffle();
        notifyQueueChanged();
        notifyShuffleChanged(queueManager.isShuffleEnabled());
    }
    
    public void togglePlayPause() {
        if (isPlaying) {
            pause();
        } else {
            play();
        }
    }
    
    public void toggleRepeat() {
        RepeatMode nextMode;
        switch (repeatMode) {
            case OFF:
                nextMode = RepeatMode.ALL;
                break;
            case ALL:
                nextMode = RepeatMode.ONE;
                break;
            case ONE:
            default:
                nextMode = RepeatMode.OFF;
                break;
        }
        setRepeatMode(nextMode);
        notifyRepeatModeChanged(nextMode);
    }
    
    public boolean isShuffleEnabled() {
        return queueManager.isShuffleEnabled();
    }
    
    // Crossfade settings (for compatibility - ExoPlayer handles gapless automatically)
    public void setCrossfadeEnabled(boolean enabled) {
        // ExoPlayer handles gapless playback automatically
        Log.d(TAG, "Crossfade setting ignored - using ExoPlayer gapless playback");
    }
    
    public void setCrossfadeDuration(int durationMs) {
        // ExoPlayer handles gapless playback automatically
        Log.d(TAG, "Crossfade duration ignored - using ExoPlayer gapless playback");
    }
    
    public boolean isCrossfadeEnabled() {
        return true; // ExoPlayer always provides gapless
    }
    
    public int getCrossfadeDuration() {
        return 0; // ExoPlayer handles internally
    }
    // Song Completion Handling
    private void handleSongCompletion() {
        Song currentSong = getCurrentSong();
        if (currentSong != null) {
            long playDuration = System.currentTimeMillis() - songStartTime;
            if (playDuration < 30000) { // Less than 30 seconds = skip
                trackSongSkip(currentSong);
            } else {
                trackSongCompletion(currentSong, getCurrentPosition(), getDuration());
            }
        }
        
        // Check if we need to auto-load more songs
        checkAndAutoLoadMore();
        
        if (repeatMode == RepeatMode.ONE) {
            // Replay current song
            if (exoPlayer != null) {
                exoPlayer.seekTo(0);
                exoPlayer.setPlayWhenReady(true);
            }
            return;
        }
        
        // Normal next song
        if (queueManager.hasNext()) {
            queueManager.moveToNext();
            playCurrent();
        } else if (repeatMode == RepeatMode.ALL && !queueManager.getActiveQueue().isEmpty()) {
            // Loop back to beginning
            queueManager.setCurrentIndex(0);
            playCurrent();
        }
    }
    
    // Next Song Prefetching
    private void prefetchNextSong() {
        Song nextSong = queueManager.peekNext();
        if (nextSong == null) {
            return;
        }
        
        // ExoPlayer handles gapless playback automatically
        // Just prefetch the stream URL for faster loading
        executor.execute(() -> {
            StreamCache.getInstance().prefetch(nextSong.getVideoId());
            Log.d(TAG, "Prefetched next song: " + nextSong.getTitle());
        });
    }
    
    // Notification Management
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Controls for music playback");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    
    private void updateNotification() {
        Notification notification = buildNotification();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification);
    }
    
    private Notification buildNotification() {
        Song currentSong = getCurrentSong();
        if (currentSong == null) {
            return createEmptyNotification();
        }
        
        // Create notification with media controls
        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        PendingIntent playPausePendingIntent = PendingIntent.getService(
            this, 0,
            new Intent(this, MusicService.class).setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        PendingIntent nextPendingIntent = PendingIntent.getService(
            this, 0,
            new Intent(this, MusicService.class).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        PendingIntent prevPendingIntent = PendingIntent.getService(
            this, 0,
            new Intent(this, MusicService.class).setAction(ACTION_PREVIOUS),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Get current position and duration safely
        long position = 0;
        long duration = 0;
        if (exoPlayer != null && !isPreparing) {
            try {
                position = exoPlayer.getCurrentPosition();
                duration = exoPlayer.getDuration();
                if (duration < 0) duration = 0;
            } catch (Exception e) {
                Log.e(TAG, "Error getting playback position", e);
            }
        }
        
        // Update media session metadata
        if (mediaSession != null) {
            android.support.v4.media.MediaMetadataCompat.Builder metadataBuilder = 
                new android.support.v4.media.MediaMetadataCompat.Builder()
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, currentSong.getTitle())
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, currentSong.getArtist())
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM, currentSong.getAlbum())
                    .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, duration);
            
            if (currentAlbumArt != null) {
                metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentAlbumArt);
            }
            
            mediaSession.setMetadata(metadataBuilder.build());
            
            // Update playback state
            PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | 
                           PlaybackStateCompat.ACTION_PAUSE | 
                           PlaybackStateCompat.ACTION_SKIP_TO_NEXT | 
                           PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                           PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                         position, 1.0f);
                         
            mediaSession.setPlaybackState(stateBuilder.build());
        }
        
        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentSong.getTitle())
            .setContentText(currentSong.getArtist())
            .setSubText(currentSong.getAlbum())
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(PendingIntent.getService(this, 0, 
                new Intent(this, MusicService.class).setAction("STOP"), 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, 
                       isPlaying ? "Pause" : "Play", playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .setStyle(new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
            .setOnlyAlertOnce(true);
        
        // Load album art asynchronously
        if (currentAlbumArt != null) {
            builder.setLargeIcon(currentAlbumArt);
        } else if (currentSong.getThumbnailUrl() != null && !currentSong.getThumbnailUrl().isEmpty()) {
            loadAlbumArt(currentSong.getThumbnailUrl());
        }
        
        return builder.build();
    }
    
    private Notification createEmptyNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Resona Music")
            .setContentText("No song playing")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();
    }
    
    // Album Art Loading
    private void loadAlbumArt(String imageUrl) {
        Glide.with(this)
            .asBitmap()
            .load(imageUrl)
            .into(new SimpleTarget<Bitmap>() {
                @Override
                public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                    currentAlbumArt = resource;
                    updateNotification();
                }
            });
    }
    
    // Notification Action Handler
    private void handleNotificationAction(String action) {
        if (action == null) return;
        
        switch (action) {
            case ACTION_PLAY:
                play();
                break;
            case ACTION_PAUSE:
                pause();
                break;
            case ACTION_NEXT:
                next();
                break;
            case ACTION_PREVIOUS:
                previous();
                break;
            case "STOP":
                stopSelf();
                break;
        }
    }
    
    // Broadcast Receiver for system events
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                handleNotificationAction(action);
            }
        }
    };
    
    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PREVIOUS);
        
        // Use RECEIVER_NOT_EXPORTED for internal app communication (Android 13+ requirement)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, filter);
        }
    }
    
    private void unregisterBroadcastReceiver() {
        try {
            unregisterReceiver(broadcastReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
    }
    
    // Queue State Persistence
    private void saveQueueState() {
        try {
            int currentPosition = exoPlayer != null ? (int) exoPlayer.getCurrentPosition() : 0;
            // Note: Simplified queue saving - may need to implement proper QueuePersistence
            Log.d(TAG, "Queue state saved (simplified implementation)");
        } catch (Exception e) {
            Log.e(TAG, "Error saving queue state", e);
        }
    }
    
    private void restoreQueueState() {
        try {
            // Note: Simplified queue restoration - may need to implement proper QueuePersistence
            Log.d(TAG, "Queue state restored (simplified implementation)");
        } catch (Exception e) {
            Log.e(TAG, "Error restoring queue state", e);
        }
    }
    
    // Analytics and Tracking
    private void trackSongCompletion(Song song, int position, int duration) {
        try {
            executor.execute(() -> {
                try {
                    String artist = song.getArtist();
                    if (artist == null || artist.trim().isEmpty()) {
                        artist = "Unknown Artist";
                    }
                    
                    // Record song in database for personalized recommendations
                    database.insertOrUpdateSong(
                        song.getVideoId(),
                        song.getTitle(),
                        artist,
                        "", // artistId - not available in Song model
                        song.getThumbnailUrl(),
                        song.getDurationSeconds()
                    );
                    
                    // Record play event (significant listening = 80% completion)
                    database.recordPlayEvent(song.getVideoId(), position);
                    
                    // Also record to RecommendationDatabase for advanced features
                    float completionRate = duration > 0 ? (float) position / duration : 0f;
                    recommendationDb.recordPlay(
                        song.getVideoId(),
                        song.getTitle(),
                        artist,
                        "", // artistId - not available in Song model
                        song.getAlbum(),
                        "", // albumId - not available in Song model
                        song.getThumbnailUrl(),
                        position,
                        completionRate
                    );
                    
                    Log.d(TAG, "Recorded song to databases: " + song.getTitle() + " at " + position + "ms (" + (int)(completionRate * 100) + "% complete)");
                } catch (Exception e) {
                    Log.e(TAG, "Error recording to database", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error tracking song completion", e);
        }
    }
    
    private void trackSongSkip(Song song) {
        try {
            executor.execute(() -> {
                // Note: Analytics tracking disabled - implement PersonalizedHomeFeed if needed
                Log.d(TAG, "Tracked skip: " + song.getTitle());
            });
        } catch (Exception e) {
            Log.e(TAG, "Error tracking song skip", e);
        }
    }
    
    // Listener Management
    public interface PlaybackListener {
        void onSongChanged(Song song);
        void onPlaybackStateChanged(boolean isPlaying);
        void onQueueChanged();
        void onProgressChanged(int position, int duration);
        void onShuffleChanged(boolean shuffle);
        void onRepeatModeChanged(RepeatMode mode);
        void onError(String message);
        void onLoadingStateChanged(boolean loading);
    }
    
    public interface MusicServiceListener {
        void onSongChanged(Song song);
        void onPlaybackStateChanged(boolean isPlaying);
        void onQueueChanged();
        void onProgressChanged(int position, int duration);
    }
    
    public void addListener(MusicServiceListener listener) {
        listeners.add(listener);
    }
    
    public void addListener(PlaybackListener listener) {
        // Convert PlaybackListener to MusicServiceListener
        MusicServiceListener adapter = new MusicServiceListener() {
            @Override
            public void onSongChanged(Song song) {
                listener.onSongChanged(song);
            }
            
            @Override
            public void onPlaybackStateChanged(boolean isPlaying) {
                listener.onPlaybackStateChanged(isPlaying);
            }
            
            @Override
            public void onQueueChanged() {
                listener.onQueueChanged();
            }
            
            @Override
            public void onProgressChanged(int position, int duration) {
                listener.onProgressChanged(position, duration);
            }
        };
        listeners.add(adapter);
    }
    
    public void removeListener(MusicServiceListener listener) {
        listeners.remove(listener);
    }
    
    public void removeListener(PlaybackListener listener) {
        // Note: This is simplified - in production, you'd track PlaybackListener adapters
        Log.d(TAG, "PlaybackListener removal - simplified implementation");
    }
    
    private void notifySongChanged(Song song) {
        for (MusicServiceListener listener : listeners) {
            listener.onSongChanged(song);
        }
    }
    
    private void notifyPlaybackStateChanged() {
        for (MusicServiceListener listener : listeners) {
            listener.onPlaybackStateChanged(isPlaying);
        }
    }
    
    private void notifyQueueChanged() {
        for (MusicServiceListener listener : listeners) {
            listener.onQueueChanged();
        }
    }
    
    private void notifyProgressChanged(int position, int duration) {
        for (MusicServiceListener listener : listeners) {
            listener.onProgressChanged(position, duration);
        }
    }
    
    private void notifyShuffleChanged(boolean shuffle) {
        // Only notify PlaybackListener instances
    }
    
    private void notifyRepeatModeChanged(RepeatMode mode) {
        // Only notify PlaybackListener instances
    }
    
    private void notifyError(String message) {
        // Only notify PlaybackListener instances
    }
    
    private void notifyLoadingStateChanged(boolean loading) {
        // Only notify PlaybackListener instances
    }
    
    // Auto-load More Songs
    private void checkAndAutoLoadMore() {
        List<Song> queue = queueManager.getActiveQueue();
        int currentIndex = queueManager.getCurrentIndex();
        int songsRemaining = queue.size() - currentIndex - 1;
        
        if (songsRemaining < 5) {
            Log.d(TAG, "Queue running low (" + songsRemaining + " songs remaining), consider loading more");
            // Listeners can implement this to load more songs
            for (MusicServiceListener listener : listeners) {
                if (listener instanceof AutoLoadListener) {
                    ((AutoLoadListener) listener).onNeedMoreSongs(songsRemaining);
                }
            }
        }
    }
    
    public interface AutoLoadListener extends MusicServiceListener {
        void onNeedMoreSongs(int songsRemaining);
    }
    
    // Sleep Timer
    public void setSleepTimer(long durationMs) {
        cancelSleepTimer();
        sleepTimerEndTime = System.currentTimeMillis() + durationMs;
        
        sleepTimerRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Sleep timer expired - pausing playback");
                pause();
                sleepTimerEndTime = 0;
                for (MusicServiceListener listener : listeners) {
                    if (listener instanceof SleepTimerListener) {
                        ((SleepTimerListener) listener).onSleepTimerExpired();
                    }
                }
            }
        };
        
        sleepTimerHandler.postDelayed(sleepTimerRunnable, durationMs);
        Log.d(TAG, "Sleep timer set for " + (durationMs / 60000) + " minutes");
        
        for (MusicServiceListener listener : listeners) {
            if (listener instanceof SleepTimerListener) {
                ((SleepTimerListener) listener).onSleepTimerSet(sleepTimerEndTime);
            }
        }
    }
    
    public void cancelSleepTimer() {
        if (sleepTimerRunnable != null) {
            sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
            sleepTimerRunnable = null;
            sleepTimerEndTime = 0;
            Log.d(TAG, "Sleep timer cancelled");
            
            for (MusicServiceListener listener : listeners) {
                if (listener instanceof SleepTimerListener) {
                    ((SleepTimerListener) listener).onSleepTimerCancelled();
                }
            }
        }
    }
    
    public long getSleepTimerEndTime() {
        return sleepTimerEndTime;
    }
    
    public boolean isSleepTimerActive() {
        return sleepTimerEndTime > 0;
    }
    
    public interface SleepTimerListener extends MusicServiceListener {
        void onSleepTimerSet(long endTime);
        void onSleepTimerExpired();
        void onSleepTimerCancelled();
    }
    
    // Audio Focus Management
    private void requestAudioFocus() {
        if (audioManager == null) return;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                    @Override
                    public void onAudioFocusChange(int focusChange) {
                        handleAudioFocusChange(focusChange);
                    }
                })
                .build();
            
            int result = audioManager.requestAudioFocus(audioFocusRequest);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "Audio focus granted");
            }
        } else {
            int result = audioManager.requestAudioFocus(
                new AudioManager.OnAudioFocusChangeListener() {
                    @Override
                    public void onAudioFocusChange(int focusChange) {
                        handleAudioFocusChange(focusChange);
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            );
            
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "Audio focus granted");
            }
        }
    }
    
    private void abandonAudioFocus() {
        if (audioManager == null) return;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
        Log.d(TAG, "Audio focus abandoned");
    }
    
    private void handleAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                Log.d(TAG, "Audio focus gained");
                // Only resume if we were playing before focus loss (not on initial request)
                if (wasPlayingBeforeFocusLoss && !isPlaying && !isUserInitiatedPlay) {
                    exoPlayer.setPlayWhenReady(true);
                    startForeground(NOTIFICATION_ID, buildNotification());
                }
                // Reset the user-initiated flag after handling
                isUserInitiatedPlay = false;
                setVolume(currentVolume);
                break;
            
            case AudioManager.AUDIOFOCUS_LOSS:
                Log.d(TAG, "Audio focus lost permanently");
                wasPlayingBeforeFocusLoss = false; // Don't auto-resume on permanent loss
                if (exoPlayer != null) {
                    exoPlayer.setPlayWhenReady(false);
                }
                break;
            
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                Log.d(TAG, "Audio focus lost transient");
                wasPlayingBeforeFocusLoss = isPlaying; // Remember if we were playing
                if (exoPlayer != null) {
                    exoPlayer.setPlayWhenReady(false);
                }
                break;
            
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                Log.d(TAG, "Audio focus lost transient can duck");
                setVolume(currentVolume * 0.2f); // Duck to 20% volume
                break;
        }
    }
    
    // Skip Silence
    public void setSkipSilenceEnabled(boolean enabled) {
        if (exoPlayer != null) {
            exoPlayer.setSkipSilenceEnabled(enabled);
            Log.d(TAG, "Skip silence " + (enabled ? "enabled" : "disabled"));
        }
    }
    
    public boolean isSkipSilenceEnabled() {
        return exoPlayer != null && exoPlayer.getSkipSilenceEnabled();
    }
    
    // Like/Dislike functionality
    public void likeSong(Song song) {
        if (song == null) return;
        executor.execute(() -> {
            try {
                // Always update local database
                recommendationDb.likeSong(song.getVideoId());
                Log.d(TAG, "Liked song locally: " + song.getTitle());
                
                // Sync with YouTube if authenticated
                if (InnertubeBridge.isAuthenticatedSync()) {
                    boolean success = InnertubeBridge.likeVideoSync(song.getVideoId());
                    if (success) {
                        Log.d(TAG, "Synced like to YouTube: " + song.getTitle());
                    } else {
                        Log.w(TAG, "Failed to sync like to YouTube (API returned false)");
                    }
                } else {
                    Log.d(TAG, "Not authenticated - like saved locally only");
                }
                
                for (MusicServiceListener listener : listeners) {
                    if (listener instanceof LikeListener) {
                        runOnUiThread(() -> ((LikeListener) listener).onSongLiked(song));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error liking song", e);
            }
        });
    }
    
    public void dislikeSong(Song song) {
        if (song == null) return;
        executor.execute(() -> {
            try {
                // Always update local database
                recommendationDb.dislikeSong(song.getVideoId());
                Log.d(TAG, "Disliked song locally: " + song.getTitle());
                
                // Note: YouTube doesn't have a "dislike" for music, only like/neutral
                // So we only store dislikes locally for recommendation filtering
                
                for (MusicServiceListener listener : listeners) {
                    if (listener instanceof LikeListener) {
                        runOnUiThread(() -> ((LikeListener) listener).onSongDisliked(song));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error disliking song", e);
            }
        });
    }
    
    public void unlikeSong(Song song) {
        if (song == null) return;
        executor.execute(() -> {
            try {
                // Always update local database
                recommendationDb.unlikeSong(song.getVideoId());
                Log.d(TAG, "Unliked song locally: " + song.getTitle());
                
                // Sync with YouTube if authenticated (remove like)
                if (InnertubeBridge.isAuthenticatedSync()) {
                    boolean success = InnertubeBridge.unlikeVideoSync(song.getVideoId());
                    if (success) {
                        Log.d(TAG, "Removed like from YouTube: " + song.getTitle());
                    } else {
                        Log.w(TAG, "Failed to remove like from YouTube (API returned false)");
                    }
                } else {
                    Log.d(TAG, "Not authenticated - unlike saved locally only");
                }
                
                for (MusicServiceListener listener : listeners) {
                    if (listener instanceof LikeListener) {
                        runOnUiThread(() -> ((LikeListener) listener).onSongUnliked(song));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error unliking song", e);
            }
        });
    }
    
    public interface LikeListener extends MusicServiceListener {
        void onSongLiked(Song song);
        void onSongDisliked(Song song);
        void onSongUnliked(Song song);
    }
    
    // Utility Methods
    private void runOnUiThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }
}
