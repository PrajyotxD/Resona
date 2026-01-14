package music.resona.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import music.resona.MainActivity;
import music.resona.R;
import music.resona.models.Song;

/**
 * Foreground service for background music playback.
 * Handles queue management, shuffle, repeat, and MediaPlayer lifecycle.
 */
public class MusicService extends Service {
    
    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "music_playback_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 500;
    
    // Playback state
    private MediaPlayer mediaPlayer;
    private final List<Song> queue = new ArrayList<>();
    private final List<Song> shuffledQueue = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isPlaying = false;
    private boolean isShuffleEnabled = false;
    private RepeatMode repeatMode = RepeatMode.OFF;
    private boolean isPreparing = false;
    
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
        progressHandler.post(progressRunnable);
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        progressHandler.removeCallbacks(progressRunnable);
        releaseMediaPlayer();
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
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Song currentSong = getCurrentSong();
        String title = currentSong != null ? currentSong.getTitle() : "Resona";
        String artist = currentSong != null ? currentSong.getArtist() : "Music Player";
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
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
        playUrl(streamUrl);
        notifySongChanged(song);
    }
    
    public void playUrl(String url) {
        if (mediaPlayer == null) {
            initMediaPlayer();
        }
        
        try {
            isPreparing = true;
            notifyLoadingStateChanged(true);
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
            // Stream URL will be fetched by MusicPlaybackManager
        }
    }
    
    public void play() {
        if (mediaPlayer != null && !isPlaying && !isPreparing) {
            mediaPlayer.start();
            isPlaying = true;
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
    
    public void next() {
        if (queue.isEmpty()) return;
        
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
}
