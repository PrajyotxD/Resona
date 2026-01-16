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
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import music.resona.MainActivity;
import music.resona.R;
import music.resona.models.Song;
import music.resona.viewmodel.PersonalizedHomeFeed;

/**
 * Foreground service for background music playback.
 * Handles queue management, shuffle, repeat, and MediaPlayer lifecycle.
 */
public class MusicService extends Service {
    
    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "music_playback_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 500;
    
    // Notification actions
    public static final String ACTION_PLAY = "music.resona.PLAY";
    public static final String ACTION_PAUSE = "music.resona.PAUSE";
    public static final String ACTION_NEXT = "music.resona.NEXT";
    public static final String ACTION_PREVIOUS = "music.resona.PREVIOUS";
    
    // Playback state
    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private Bitmap currentAlbumArt;
    private final List<Song> queue = new ArrayList<>();
    private final List<Song> shuffledQueue = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isPlaying = false;
    private boolean isShuffleEnabled = false;
    private RepeatMode repeatMode = RepeatMode.OFF;
    private boolean isPreparing = false;
    
    // Recommendation tracking
    private PersonalizedHomeFeed personalizedHomeFeed;
    private long songStartTime = 0;
    private int lastTrackedPosition = 0;
    
    // Progress tracking
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPlaying && !isPreparing) {
                try {
                    int current = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();
                    notifyProgressChanged(current, duration);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting progress", e);
                }
            }
            progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
        }
    };
    
    // Listeners
    private final CopyOnWriteArrayList<PlaybackListener> listeners = new CopyOnWriteArrayList<>();
    
    // Binder
    private final IBinder binder = new MusicBinder();
    
    // Broadcast receiver for notification actions
    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            
            switch (action) {
                case ACTION_PLAY:
                    resume();
                    break;
                case ACTION_PAUSE:
                    pause();
                    break;
                case ACTION_NEXT:
                    playNext();
                    break;
                case ACTION_PREVIOUS:
                    playPrevious();
                    break;
            }
        }
    };
    
    public enum RepeatMode {
        OFF, ALL, ONE
    }
    
    public interface PlaybackListener {
        void onSongChanged(Song song);
        void onPlaybackStateChanged(boolean isPlaying);
        void onProgressChanged(int currentMs, int durationMs);
        void onShuffleChanged(boolean shuffle);
        void onRepeatModeChanged(RepeatMode mode);
        void onError(String message);
        void onLoadingStateChanged(boolean isLoading);
    }
    
    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initMediaPlayer();
        initMediaSession();
        personalizedHomeFeed = new PersonalizedHomeFeed(this);
        progressHandler.post(progressRunnable);
        
        // Register broadcast receiver for notification actions
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PREVIOUS);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(notificationReceiver, filter);
        }
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Only start foreground if we have a song
        if (getCurrentSong() != null) {
            startForeground(NOTIFICATION_ID, createNotification());
        }
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        progressHandler.removeCallbacks(progressRunnable);
        releaseMediaPlayer();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        try {
            unregisterReceiver(notificationReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
        super.onDestroy();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Controls for music playback");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "ResonaMusicService");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                             MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        
        // Set callback for media button events
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                resume();
            }
            
            @Override
            public void onPause() {
                pause();
            }
            
            @Override
            public void onSkipToNext() {
                playNext();
            }
            
            @Override
            public void onSkipToPrevious() {
                playPrevious();
            }
            
            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }
        });
        
        mediaSession.setActive(true);
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Song currentSong = getCurrentSong();
        String title = currentSong != null ? currentSong.getTitle() : "Resona";
        String artist = currentSong != null && currentSong.getArtist() != null ? currentSong.getArtist() : "Music Player";
        
        // Create action intents
        PendingIntent playPauseIntent = PendingIntent.getBroadcast(
            this, 0,
            new Intent(isPlaying ? ACTION_PAUSE : ACTION_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        PendingIntent previousIntent = PendingIntent.getBroadcast(
            this, 1,
            new Intent(ACTION_PREVIOUS),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        PendingIntent nextIntent = PendingIntent.getBroadcast(
            this, 2,
            new Intent(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Get current position and duration safely
        long position = 0;
        long duration = 0;
        if (mediaPlayer != null && !isPreparing) {
            try {
                position = mediaPlayer.getCurrentPosition();
                duration = mediaPlayer.getDuration();
                if (duration < 0) duration = 0;
            } catch (Exception e) {
                Log.e(TAG, "Error getting playback position", e);
            }
        }
        
        // Update media session metadata
        if (mediaSession != null) {
            android.support.v4.media.MediaMetadataCompat.Builder metadataBuilder = 
                new android.support.v4.media.MediaMetadataCompat.Builder()
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, duration);
            
            if (currentAlbumArt != null) {
                metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentAlbumArt);
            }
            
            mediaSession.setMetadata(metadataBuilder.build());
            
            // Update playback state with proper duration
            PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE |
                           PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                           PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                           PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                         position, 1.0f);
            mediaSession.setPlaybackState(stateBuilder.build());
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.play)
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Media controls using android system icons
            .addAction(android.R.drawable.ic_media_previous, "Previous", previousIntent)
            .addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                      isPlaying ? "Pause" : "Play", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            // MediaStyle
            .setStyle(new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
        
        // Add album art if available
        if (currentAlbumArt != null) {
            builder.setLargeIcon(currentAlbumArt);
        }
        
        return builder.build();
    }
    
    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }
    
    private void loadAlbumArt(String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
            currentAlbumArt = null;
            updateNotification();
            return;
        }
        
        try {
            Glide.with(getApplicationContext())
                .asBitmap()
                .load(thumbnailUrl)
                .override(512, 512) // Resize for notification
                .centerCrop()
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                        currentAlbumArt = resource;
                        updateNotification();
                        Log.d(TAG, "Album art loaded successfully");
                    }
                    
                    @Override
                    public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                        super.onLoadFailed(errorDrawable);
                        currentAlbumArt = null;
                        updateNotification();
                        Log.e(TAG, "Failed to load album art");
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Error loading album art", e);
            currentAlbumArt = null;
            updateNotification();
        }
    }
    
    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build());
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        
        mediaPlayer.setOnPreparedListener(mp -> {
            isPreparing = false;
            notifyLoadingStateChanged(false);
            mp.start();
            isPlaying = true;
            notifyPlaybackStateChanged(true);
            updateNotification();
        });
        
        mediaPlayer.setOnCompletionListener(mp -> {
            handlePlaybackCompletion();
        });
        
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
            isPreparing = false;
            notifyLoadingStateChanged(false);
            notifyError("Playback error occurred");
            return true;
        });
    }
    
    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
    
    // Public API
    
    public void addListener(PlaybackListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }
    
    public void setQueue(List<Song> songs, int startIndex) {
        queue.clear();
        queue.addAll(songs);
        currentIndex = startIndex;
        
        if (isShuffleEnabled) {
            shuffleQueue();
        }
        
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            playCurrent();
        }
    }
    
    public void playSong(Song song, String streamUrl) {
        queue.clear();
        queue.add(song);
        currentIndex = 0;
        
        // Update notification immediately with new song info
        notifySongChanged(song);
        loadAlbumArt(song.getThumbnailUrl());
        updateNotification();
        
        // Start playing
        playUrl(streamUrl);
    }
    
    public void playUrl(String url) {
        if (mediaPlayer == null) {
            initMediaPlayer();
        }
        
        try {
            isPreparing = true;
            notifyLoadingStateChanged(true);
            
            // Update notification immediately to show loading state
            updateNotification();
            
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Error playing URL", e);
            isPreparing = false;
            notifyLoadingStateChanged(false);
            notifyError("Failed to play: " + e.getMessage());
        }
    }
    
    private void playCurrent() {
        Song song = getCurrentSong();
        if (song != null) {
            notifySongChanged(song);
            // Load album art for notification
            loadAlbumArt(song.getThumbnailUrl());
            // Update notification immediately
            updateNotification();
            // Stream URL will be fetched by MusicPlaybackManager
        }
    }
    
    public void play() {
        if (mediaPlayer != null && !isPlaying && !isPreparing) {
            mediaPlayer.start();
            isPlaying = true;
            songStartTime = System.currentTimeMillis();
            lastTrackedPosition = 0;
            notifyPlaybackStateChanged(true);
            updateNotification();
        }
    }
    
    public void pause() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            notifyPlaybackStateChanged(false);
            updateNotification();
        }
    }
    
    public void togglePlayPause() {
        if (isPlaying) {
            pause();
        } else {
            play();
        }
    }
    
    public void resume() {
        play();
    }
    
    public void playNext() {
        next();
    }
    
    public void playPrevious() {
        previous();
    }
    
    public void next() {
        if (queue.isEmpty()) return;
        
        // Track skip if song was playing for less than 30 seconds
        Song currentSong = getCurrentSong();
        if (currentSong != null && mediaPlayer != null) {
            long playDuration = System.currentTimeMillis() - songStartTime;
            if (playDuration < 30000) { // Less than 30 seconds = skip
                trackSongSkip(currentSong);
            }
        }
        
        List<Song> activeQueue = isShuffleEnabled ? shuffledQueue : queue;
        
        if (repeatMode == RepeatMode.ONE) {
            // Replay current song
            playCurrent();
            return;
        }
        
        currentIndex++;
        if (currentIndex >= activeQueue.size()) {
            if (repeatMode == RepeatMode.ALL) {
                currentIndex = 0;
            } else {
                currentIndex = activeQueue.size() - 1;
                pause();
                return;
            }
        }
        
        playCurrent();
    }
    
    public void previous() {
        if (queue.isEmpty()) return;
        
        // If played more than 3 seconds, restart current song
        if (mediaPlayer != null && mediaPlayer.getCurrentPosition() > 3000) {
            mediaPlayer.seekTo(0);
            return;
        }
        
        currentIndex--;
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        
        playCurrent();
    }
    
    public void seekTo(int positionMs) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(positionMs);
        }
    }
    
    public void toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled;
        if (isShuffleEnabled) {
            shuffleQueue();
        }
        notifyShuffleChanged(isShuffleEnabled);
    }
    
    public void toggleRepeat() {
        switch (repeatMode) {
            case OFF:
                repeatMode = RepeatMode.ALL;
                break;
            case ALL:
                repeatMode = RepeatMode.ONE;
                break;
            case ONE:
                repeatMode = RepeatMode.OFF;
                break;
        }
        notifyRepeatModeChanged(repeatMode);
    }
    
    private void shuffleQueue() {
        shuffledQueue.clear();
        shuffledQueue.addAll(queue);
        
        Song currentSong = getCurrentSong();
        Collections.shuffle(shuffledQueue);
        
        // Move current song to front
        if (currentSong != null) {
            shuffledQueue.remove(currentSong);
            shuffledQueue.add(0, currentSong);
            currentIndex = 0;
        }
    }
    
    private void handlePlaybackCompletion() {
        // Track song completion
        Song currentSong = getCurrentSong();
        if (currentSong != null) {
            trackSongPlay(currentSong);
        }
        
        if (repeatMode == RepeatMode.ONE) {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo(0);
                mediaPlayer.start();
            }
        } else {
            next();
        }
    }
    
    // Getters
    
    @Nullable
    public Song getCurrentSong() {
        List<Song> activeQueue = isShuffleEnabled ? shuffledQueue : queue;
        if (currentIndex >= 0 && currentIndex < activeQueue.size()) {
            return activeQueue.get(currentIndex);
        }
        return null;
    }
    
    public int getCurrentIndex() {
        return currentIndex;
    }
    
    public List<Song> getQueue() {
        return isShuffleEnabled ? new ArrayList<>(shuffledQueue) : new ArrayList<>(queue);
    }
    
    public boolean isPlaying() {
        return isPlaying;
    }
    
    public boolean isPreparing() {
        return isPreparing;
    }
    
    public boolean isShuffleEnabled() {
        return isShuffleEnabled;
    }
    
    public RepeatMode getRepeatMode() {
        return repeatMode;
    }
    
    public int getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
    }
    
    public int getDuration() {
        return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
    }
    
    // Notification helpers
    
    private void notifySongChanged(Song song) {
        for (PlaybackListener listener : listeners) {
            listener.onSongChanged(song);
        }
    }
    
    private void notifyPlaybackStateChanged(boolean isPlaying) {
        for (PlaybackListener listener : listeners) {
            listener.onPlaybackStateChanged(isPlaying);
        }
    }
    
    private void notifyProgressChanged(int currentMs, int durationMs) {
        for (PlaybackListener listener : listeners) {
            listener.onProgressChanged(currentMs, durationMs);
        }
    }
    
    private void notifyShuffleChanged(boolean shuffle) {
        for (PlaybackListener listener : listeners) {
            listener.onShuffleChanged(shuffle);
        }
    }
    
    private void notifyRepeatModeChanged(RepeatMode mode) {
        for (PlaybackListener listener : listeners) {
            listener.onRepeatModeChanged(mode);
        }
    }
    
    private void notifyError(String message) {
        for (PlaybackListener listener : listeners) {
            listener.onError(message);
        }
    }
    
    private void notifyLoadingStateChanged(boolean isLoading) {
        for (PlaybackListener listener : listeners) {
            listener.onLoadingStateChanged(isLoading);
        }
    }
    
    // Recommendation tracking helpers
    
    private void trackSongPlay(Song song) {
        // TODO: Implement tracking when PersonalizedHomeFeed is fixed
        /*
        if (personalizedHomeFeed != null && song != null && song.getVideoId() != null) {
            int duration = mediaPlayer != null ? mediaPlayer.getDuration() : 0;
            personalizedHomeFeed.trackSongPlay(song.getVideoId(), duration);
        }
        */
    }
    
    private void trackSongSkip(Song song) {
        // TODO: Implement tracking when PersonalizedHomeFeed is fixed
        /*
        if (personalizedHomeFeed != null && song != null && song.getVideoId() != null) {
            int position = mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
            int duration = mediaPlayer != null ? mediaPlayer.getDuration() : 0;
            personalizedHomeFeed.trackSongSkip(song.getVideoId(), position, duration);
        }
        */
    }
}
