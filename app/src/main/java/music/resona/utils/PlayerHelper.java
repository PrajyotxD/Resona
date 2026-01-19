package music.resona.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

import music.resona.manager.MusicPlaybackManager;
import music.resona.models.Song;
import music.resona.service.MusicService;
import music.resona.ui.QueueBottomSheet;

/**
 * PlayerHelper - JavaScript Bridge for Custom UI Player Controls
 * 
 * This class provides a comprehensive interface between native Android player functionality
 * and JavaScript/WebView custom UI implementations. It wraps MusicPlaybackManager and
 * MusicService functionality with ExoPlayer2 integration.
 * 
 * Usage in WebView:
 * <pre>
 * webView.addJavascriptInterface(new PlayerHelper(context, playbackManager), "PlayerHelper");
 * 
 * // JavaScript usage:
 * PlayerHelper.play();
 * PlayerHelper.pause();
 * var currentSong = JSON.parse(PlayerHelper.getCurrentSong());
 * </pre>
 * 
 * Features:
 * - Playback control (play, pause, next, previous, seek)
 * - Queue management (add, remove, reorder)
 * - Volume control
 * - Shuffle and repeat modes
 * - Progress tracking
 * - Song metadata retrieval
 * - State synchronization
 * 
 * @author Resona Music Team
 * @version 1.0
 */
public class PlayerHelper implements MusicPlaybackManager.PlaybackListener {
    
    private static final String TAG = "PlayerHelper";
    
    private final Context context;
    private final MusicPlaybackManager playbackManager;
    private MusicService musicService;
    private final Handler mainHandler;
    private java.lang.ref.WeakReference<android.app.Activity> activityRef;
    
    // JavaScript callback interface name
    private String jsCallbackInterface = "PlayerCallback";
    
    /**
     * Create a new PlayerHelper instance
     * 
     * @param context Application context
     * @param playbackManager MusicPlaybackManager instance
     */
    public PlayerHelper(@NonNull Context context, @NonNull MusicPlaybackManager playbackManager) {
        this.context = context.getApplicationContext();
        this.playbackManager = playbackManager;
        this.musicService = playbackManager.getMusicService();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Store activity reference if context is an Activity
        if (context instanceof android.app.Activity) {
            this.activityRef = new java.lang.ref.WeakReference<>((android.app.Activity) context);
        }
        
        // Register as listener to forward events to JavaScript
        playbackManager.addListener(this);
        
        Log.d(TAG, "PlayerHelper initialized for JavaScript bridge");
    }
    
    /**
     * Set activity reference for UI operations like showing dialogs
     * Call this from the Activity that hosts the WebView
     */
    public void setActivity(android.app.Activity activity) {
        this.activityRef = new java.lang.ref.WeakReference<>(activity);
    }
    
    /**
     * Helper method to run code on main thread
     */
    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
    
    /**
     * Set the JavaScript callback interface name
     * Default is "PlayerCallback"
     */
    public void setJsCallbackInterface(String interfaceName) {
        this.jsCallbackInterface = interfaceName;
    }
    
    // ========== PLAYBACK CONTROL ==========
    
    /**
     * Start or resume playback
     */
    @JavascriptInterface
    public void play() {
        Log.d(TAG, "JS: play()");
        runOnMainThread(() -> {
            if (playbackManager != null) {
                playbackManager.play();
            }
        });
    }
    
    /**
     * Pause playback
     */
    @JavascriptInterface
    public void pause() {
        Log.d(TAG, "JS: pause()");
        runOnMainThread(() -> {
            if (playbackManager != null) {
                playbackManager.pause();
            }
        });
    }
    
    /**
     * Toggle between play and pause
     */
    @JavascriptInterface
    public void togglePlayPause() {
        Log.d(TAG, "JS: togglePlayPause()");
        runOnMainThread(() -> {
            if (playbackManager != null) {
                playbackManager.togglePlayPause();
            }
        });
    }
    
    /**
     * Skip to next song in queue
     */
    @JavascriptInterface
    public void next() {
        Log.d(TAG, "JS: next()");
        runOnMainThread(() -> {
            if (playbackManager != null) {
                playbackManager.next();
            }
        });
    }
    
    /**
     * Go to previous song in queue
     */
    @JavascriptInterface
    public void previous() {
        Log.d(TAG, "JS: previous()");
        runOnMainThread(() -> {
            if (playbackManager != null) {
                playbackManager.previous();
            }
        });
    }
    
    /**
     * Seek to specific position in current song
     * 
     * @param positionMs Position in milliseconds
     */
    @JavascriptInterface
    public void seekTo(int positionMs) {
        Log.d(TAG, "JS: seekTo(" + positionMs + ")");
        runOnMainThread(() -> {
            if (playbackManager != null) {
                playbackManager.seekTo(positionMs);
            }
        });
    }
    
    /**
     * Seek to specific position as percentage (0.0 to 1.0)
     * 
     * @param percentage Position as percentage (0.0 to 1.0)
     */
    @JavascriptInterface
    public void seekToPercentage(float percentage) {
        runOnMainThread(() -> {
            int duration = getDuration();
            if (duration > 0) {
                int positionMs = (int) (duration * Math.max(0f, Math.min(1f, percentage)));
                seekTo(positionMs);
            }
        });
    }
    
    /**
     * Stop playback and reset player
     */
    @JavascriptInterface
    public void stop() {
        Log.d(TAG, "JS: stop()");
        runOnMainThread(() -> {
            if (playbackManager != null) {
                playbackManager.pause();
                playbackManager.seekTo(0);
            }
        });
    }
    
    // ========== PLAYBACK STATE ==========
    
    /**
     * Check if player is currently playing
     * 
     * @return true if playing, false otherwise
     */
    @JavascriptInterface
    public boolean isPlaying() {
        try {
            return playbackManager != null && playbackManager.isPlaying();
        } catch (IllegalStateException e) {
            Log.w(TAG, "isPlaying() called from wrong thread, returning false");
            return false;
        }
    }
    
    /**
     * Check if player is preparing/loading
     * 
     * @return true if loading, false otherwise
     */
    @JavascriptInterface
    public boolean isLoading() {
        try {
            MusicService service = getMusicService();
            return service != null && service.isPreparing();
        } catch (IllegalStateException e) {
            Log.w(TAG, "isLoading() called from wrong thread, returning false");
            return false;
        }
    }
    
    /**
     * Get current playback position in milliseconds
     * 
     * @return Current position in ms
     */
    @JavascriptInterface
    public int getCurrentPosition() {
        try {
            return playbackManager != null ? playbackManager.getCurrentPosition() : 0;
        } catch (IllegalStateException e) {
            Log.w(TAG, "getCurrentPosition() called from wrong thread, returning 0");
            return 0;
        }
    }
    
    /**
     * Get total duration of current song in milliseconds
     * 
     * @return Duration in ms
     */
    @JavascriptInterface
    public int getDuration() {
        try {
            return playbackManager != null ? playbackManager.getDuration() : 0;
        } catch (IllegalStateException e) {
            Log.w(TAG, "getDuration() called from wrong thread, returning 0");
            return 0;
        }
    }
    
    /**
     * Get current playback position as percentage (0.0 to 1.0)
     * 
     * @return Position percentage
     */
    @JavascriptInterface
    public float getPositionPercentage() {
        int duration = getDuration();
        if (duration <= 0) return 0f;
        return getCurrentPosition() / (float) duration;
    }
    
    /**
     * Get formatted current time (MM:SS)
     * 
     * @return Formatted time string
     */
    @JavascriptInterface
    public String getCurrentTimeFormatted() {
        return formatTime(getCurrentPosition());
    }
    
    /**
     * Get formatted total duration (MM:SS)
     * 
     * @return Formatted duration string
     */
    @JavascriptInterface
    public String getDurationFormatted() {
        return formatTime(getDuration());
    }
    
    // ========== CURRENT SONG INFO ==========
    
    /**
     * Get current song as JSON string
     * 
     * @return JSON representation of current song, or empty object if no song
     */
    @JavascriptInterface
    public String getCurrentSong() {
        Song song = playbackManager != null ? playbackManager.getCurrentSong() : null;
        return songToJson(song).toString();
    }
    
    /**
     * Get current song title
     * 
     * @return Song title or "Not Playing"
     */
    @JavascriptInterface
    public String getSongTitle() {
        Song song = playbackManager != null ? playbackManager.getCurrentSong() : null;
        return song != null ? song.getTitle() : "Not Playing";
    }
    
    /**
     * Get current song artist
     * 
     * @return Artist name or "Unknown Artist"
     */
    @JavascriptInterface
    public String getSongArtist() {
        Song song = playbackManager != null ? playbackManager.getCurrentSong() : null;
        return song != null && song.getArtist() != null ? song.getArtist() : "Unknown Artist";
    }
    
    /**
     * Get current song album
     * 
     * @return Album name or empty string
     */
    @JavascriptInterface
    public String getSongAlbum() {
        Song song = playbackManager != null ? playbackManager.getCurrentSong() : null;
        return song != null && song.getAlbum() != null ? song.getAlbum() : "";
    }
    
    /**
     * Get current song thumbnail URL
     * 
     * @return Thumbnail URL or empty string
     */
    @JavascriptInterface
    public String getSongThumbnail() {
        Song song = playbackManager != null ? playbackManager.getCurrentSong() : null;
        return song != null && song.getThumbnailUrl() != null ? song.getThumbnailUrl() : "";
    }
    
    /**
     * Get current song video ID
     * 
     * @return Video ID or empty string
     */
    @JavascriptInterface
    public String getSongVideoId() {
        Song song = playbackManager != null ? playbackManager.getCurrentSong() : null;
        return song != null ? song.getVideoId() : "";
    }
    
    /**
     * Get current song duration in seconds
     * 
     * @return Duration in seconds
     */
    @JavascriptInterface
    public int getSongDurationSeconds() {
        Song song = playbackManager != null ? playbackManager.getCurrentSong() : null;
        return song != null ? song.getDurationSeconds() : 0;
    }
    
    // ========== QUEUE MANAGEMENT ==========
    
    /**
     * Get entire queue as JSON array
     * 
     * @return JSON array of songs
     */
    @JavascriptInterface
    public String getQueue() {
        List<Song> queue = playbackManager != null ? playbackManager.getQueue() : null;
        return queueToJson(queue).toString();
    }
    
    /**
     * Show native queue bottom sheet dialog
     * This requires an Activity context to be set via setActivity()
     */
    @JavascriptInterface
    public void showQueueBottomSheet() {
        Log.d(TAG, "JS: showQueueBottomSheet()");
        runOnMainThread(() -> {
            android.app.Activity activity = activityRef != null ? activityRef.get() : null;
            if (activity == null || activity.isFinishing()) {
                Log.w(TAG, "Cannot show queue: no valid activity reference");
                return;
            }
            
            MusicService service = getMusicService();
            if (service == null) {
                Log.w(TAG, "Cannot show queue: service not available");
                return;
            }
            
            List<Song> queue = service.getQueue();
            int currentIndex = service.getCurrentIndex();
            
            QueueBottomSheet queueSheet = new QueueBottomSheet(activity, queue, currentIndex, 
                new QueueBottomSheet.OnQueueItemClickListener() {
                    @Override
                    public void onSongClick(int position) {
                        MusicService svc = getMusicService();
                        if (svc != null) {
                            svc.skipToPosition(position);
                        }
                    }
                    
                    @Override
                    public void onRemoveClick(int position) {
                        MusicService svc = getMusicService();
                        if (svc != null) {
                            svc.removeFromQueue(position);
                        }
                    }
                });
            queueSheet.show();
        });
    }
    
    /**
     * Get queue size
     * 
     * @return Number of songs in queue
     */
    @JavascriptInterface
    public int getQueueSize() {
        List<Song> queue = playbackManager != null ? playbackManager.getQueue() : null;
        return queue != null ? queue.size() : 0;
    }
    
    /**
     * Get current queue index
     * 
     * @return Current index in queue (0-based)
     */
    @JavascriptInterface
    public int getCurrentQueueIndex() {
        return playbackManager != null ? playbackManager.getCurrentQueueIndex() : -1;
    }
    
    /**
     * Skip to specific position in queue
     * 
     * @param position Queue position (0-based)
     */
    @JavascriptInterface
    public void skipToPosition(int position) {
        Log.d(TAG, "JS: skipToPosition(" + position + ")");
        MusicService service = getMusicService();
        if (service != null) {
            service.skipToPosition(position);
        }
    }
    
    /**
     * Remove song from queue at specific position
     * 
     * @param position Queue position (0-based)
     */
    @JavascriptInterface
    public void removeFromQueue(int position) {
        Log.d(TAG, "JS: removeFromQueue(" + position + ")");
        MusicService service = getMusicService();
        if (service != null) {
            service.removeFromQueue(position);
        }
    }
    
    /**
     * Clear entire queue
     */
    @JavascriptInterface
    public void clearQueue() {
        Log.d(TAG, "JS: clearQueue()");
        MusicService service = getMusicService();
        if (service != null) {
            List<Song> queue = service.getQueue();
            if (queue != null) {
                for (int i = queue.size() - 1; i >= 0; i--) {
                    service.removeFromQueue(i);
                }
            }
        }
    }
    
    /**
     * Check if there's a next song in queue
     * 
     * @return true if next song exists
     */
    @JavascriptInterface
    public boolean hasNext() {
        MusicService service = getMusicService();
        if (service != null) {
            return service.getQueueManager().hasNext();
        }
        return false;
    }
    
    /**
     * Check if there's a previous song in queue
     * 
     * @return true if previous song exists
     */
    @JavascriptInterface
    public boolean hasPrevious() {
        MusicService service = getMusicService();
        if (service != null) {
            return service.getQueueManager().hasPrevious();
        }
        return false;
    }
    
    // ========== SHUFFLE & REPEAT ==========
    
    /**
     * Toggle shuffle mode
     */
    @JavascriptInterface
    public void toggleShuffle() {
        Log.d(TAG, "JS: toggleShuffle()");
        if (playbackManager != null) {
            playbackManager.toggleShuffle();
        }
    }
    
    /**
     * Set shuffle mode
     * 
     * @param enabled true to enable shuffle
     */
    @JavascriptInterface
    public void setShuffle(boolean enabled) {
        Log.d(TAG, "JS: setShuffle(" + enabled + ")");
        MusicService service = getMusicService();
        if (service != null) {
            boolean currentShuffle = service.isShuffleEnabled();
            if (currentShuffle != enabled) {
                service.toggleShuffle();
            }
        }
    }
    
    /**
     * Check if shuffle is enabled
     * 
     * @return true if shuffle is enabled
     */
    @JavascriptInterface
    public boolean isShuffleEnabled() {
        return playbackManager != null && playbackManager.isShuffleEnabled();
    }
    
    /**
     * Toggle repeat mode (OFF -> ALL -> ONE -> OFF)
     */
    @JavascriptInterface
    public void toggleRepeat() {
        Log.d(TAG, "JS: toggleRepeat()");
        if (playbackManager != null) {
            playbackManager.toggleRepeat();
        }
    }
    
    /**
     * Set repeat mode
     * 
     * @param mode Repeat mode: "OFF", "ALL", or "ONE"
     */
    @JavascriptInterface
    public void setRepeatMode(String mode) {
        Log.d(TAG, "JS: setRepeatMode(" + mode + ")");
        MusicService service = getMusicService();
        if (service != null) {
            MusicService.RepeatMode repeatMode;
            switch (mode.toUpperCase()) {
                case "ALL":
                    repeatMode = MusicService.RepeatMode.ALL;
                    break;
                case "ONE":
                    repeatMode = MusicService.RepeatMode.ONE;
                    break;
                case "OFF":
                default:
                    repeatMode = MusicService.RepeatMode.OFF;
                    break;
            }
            service.setRepeatMode(repeatMode);
        }
    }
    
    /**
     * Get current repeat mode
     * 
     * @return "OFF", "ALL", or "ONE"
     */
    @JavascriptInterface
    public String getRepeatMode() {
        MusicService service = getMusicService();
        if (service != null) {
            return service.getRepeatMode().name();
        }
        return "OFF";
    }
    
    /**
     * Get repeat mode as integer (0=OFF, 1=ONE, 2=ALL)
     * 
     * @return Repeat mode value
     */
    @JavascriptInterface
    public int getRepeatModeValue() {
        MusicService service = getMusicService();
        if (service != null) {
            return service.getRepeatMode().getValue();
        }
        return 0;
    }
    
    // ========== VOLUME CONTROL ==========
    
    /**
     * Set volume (0.0 to 1.0)
     * 
     * @param volume Volume level (0.0 = mute, 1.0 = max)
     */
    @JavascriptInterface
    public void setVolume(float volume) {
        Log.d(TAG, "JS: setVolume(" + volume + ")");
        MusicService service = getMusicService();
        if (service != null) {
            service.setVolume(Math.max(0f, Math.min(1f, volume)));
        }
    }
    
    /**
     * Get current volume (0.0 to 1.0)
     * 
     * @return Current volume level
     */
    @JavascriptInterface
    public float getVolume() {
        MusicService service = getMusicService();
        return service != null ? service.getVolume() : 1.0f;
    }
    
    /**
     * Set volume as percentage (0 to 100)
     * 
     * @param percentage Volume percentage
     */
    @JavascriptInterface
    public void setVolumePercentage(int percentage) {
        setVolume(percentage / 100f);
    }
    
    /**
     * Get volume as percentage (0 to 100)
     * 
     * @return Volume percentage
     */
    @JavascriptInterface
    public int getVolumePercentage() {
        return (int) (getVolume() * 100);
    }
    
    /**
     * Increase volume by 10%
     */
    @JavascriptInterface
    public void increaseVolume() {
        Log.d(TAG, "JS: increaseVolume()");
        MusicService service = getMusicService();
        if (service != null) {
            service.increaseVolume();
        }
    }
    
    /**
     * Decrease volume by 10%
     */
    @JavascriptInterface
    public void decreaseVolume() {
        Log.d(TAG, "JS: decreaseVolume()");
        MusicService service = getMusicService();
        if (service != null) {
            service.decreaseVolume();
        }
    }
    
    /**
     * Mute audio
     */
    @JavascriptInterface
    public void mute() {
        setVolume(0f);
    }
    
    /**
     * Unmute audio (restore to previous volume or 100%)
     */
    @JavascriptInterface
    public void unmute() {
        if (getVolume() < 0.1f) {
            setVolume(1.0f);
        }
    }
    
    // ========== SLEEP TIMER ==========
    
    /**
     * Set sleep timer
     * 
     * @param durationMs Duration in milliseconds
     */
    @JavascriptInterface
    public void setSleepTimer(long durationMs) {
        Log.d(TAG, "JS: setSleepTimer(" + durationMs + ")");
        MusicService service = getMusicService();
        if (service != null) {
            service.setSleepTimer(durationMs);
        }
    }
    
    /**
     * Set sleep timer in minutes
     * 
     * @param minutes Duration in minutes
     */
    @JavascriptInterface
    public void setSleepTimerMinutes(int minutes) {
        setSleepTimer(minutes * 60 * 1000L);
    }
    
    /**
     * Cancel sleep timer
     */
    @JavascriptInterface
    public void cancelSleepTimer() {
        Log.d(TAG, "JS: cancelSleepTimer()");
        MusicService service = getMusicService();
        if (service != null) {
            service.cancelSleepTimer();
        }
    }
    
    // ========== LIKE/DISLIKE ==========
    
    /**
     * Like current song
     */
    @JavascriptInterface
    public void likeSong() {
        Log.d(TAG, "JS: likeSong()");
        MusicService service = getMusicService();
        Song song = getCurrentSongObject();
        if (service != null && song != null) {
            service.likeSong(song);
        }
    }
    
    /**
     * Dislike current song
     */
    @JavascriptInterface
    public void dislikeSong() {
        Log.d(TAG, "JS: dislikeSong()");
        MusicService service = getMusicService();
        Song song = getCurrentSongObject();
        if (service != null && song != null) {
            service.dislikeSong(song);
        }
    }
    
    /**
     * Unlike current song (remove like)
     */
    @JavascriptInterface
    public void unlikeSong() {
        Log.d(TAG, "JS: unlikeSong()");
        MusicService service = getMusicService();
        Song song = getCurrentSongObject();
        if (service != null && song != null) {
            service.unlikeSong(song);
        }
    }
    
    /**
     * Check if current song is liked
     * @return true if current song is liked, false otherwise
     */
    @JavascriptInterface
    public boolean isCurrentSongLiked() {
        Song song = getCurrentSongObject();
        if (song != null) {
            music.resona.database.RecommendationDatabase db = 
                music.resona.database.RecommendationDatabase.getInstance(context);
            return db.isLiked(song.getVideoId());
        }
        return false;
    }
    
    /**
     * Check if a specific song is liked by videoId
     * @param videoId The video ID to check
     * @return true if the song is liked, false otherwise
     */
    @JavascriptInterface
    public boolean isSongLiked(String videoId) {
        if (videoId == null || videoId.isEmpty()) return false;
        music.resona.database.RecommendationDatabase db = 
            music.resona.database.RecommendationDatabase.getInstance(context);
        return db.isLiked(videoId);
    }
    
    /**
     * Toggle like state for current song
     * If liked, unlikes it. If not liked, likes it.
     */
    @JavascriptInterface
    public void toggleLike() {
        Log.d(TAG, "JS: toggleLike()");
        Song song = getCurrentSongObject();
        if (song == null) return;
        
        MusicService service = getMusicService();
        if (service == null) return;
        
        music.resona.database.RecommendationDatabase db = 
            music.resona.database.RecommendationDatabase.getInstance(context);
        
        if (db.isLiked(song.getVideoId())) {
            service.unlikeSong(song);
        } else {
            service.likeSong(song);
        }
    }

    /**
     * Share current song via Android share intent
     */
    @JavascriptInterface
    public void shareSong() {
        Log.d(TAG, "JS: shareSong()");
        runOnMainThread(() -> {
            Song song = getCurrentSongObject();
            if (song != null) {
                String shareText = String.format("🎵 %s by %s\n\nListen on YouTube Music: https://music.youtube.com/watch?v=%s",
                    song.getTitle(),
                    song.getArtist() != null ? song.getArtist() : "Unknown Artist",
                    song.getVideoId()
                );
                
                android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareText);
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, song.getTitle() + " - " + song.getArtist());
                shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                
                android.content.Intent chooser = android.content.Intent.createChooser(shareIntent, "Share Song");
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooser);
            }
        });
    }
    
    // ========== PLAYER STATE ==========
    
    /**
     * Get complete player state as JSON
     * Includes: song info, playback state, queue, settings
     * 
     * @return JSON object with complete player state
     */
    @JavascriptInterface
    public String getPlayerState() {
        JSONObject state = new JSONObject();
        try {
            state.put("currentSong", new JSONObject(getCurrentSong()));
            state.put("isPlaying", isPlaying());
            state.put("isLoading", isLoading());
            state.put("currentPosition", getCurrentPosition());
            state.put("duration", getDuration());
            state.put("positionPercentage", getPositionPercentage());
            state.put("queue", new JSONArray(getQueue()));
            state.put("queueSize", getQueueSize());
            state.put("currentQueueIndex", getCurrentQueueIndex());
            state.put("isShuffleEnabled", isShuffleEnabled());
            state.put("repeatMode", getRepeatMode());
            state.put("volume", getVolume());
            state.put("volumePercentage", getVolumePercentage());
            state.put("hasNext", hasNext());
            state.put("hasPrevious", hasPrevious());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating player state JSON", e);
        } catch (IllegalStateException e) {
            Log.w(TAG, "getPlayerState() called from wrong thread, returning minimal state");
            try {
                state.put("currentSong", new JSONObject().put("title", "Loading..."));
                state.put("isPlaying", false);
                state.put("isLoading", false);
                state.put("currentPosition", 0);
                state.put("duration", 0);
            } catch (JSONException je) {
                // Ignore
            }
        }
        return state.toString();
    }
    
    // ========== HELPER METHODS ==========
    
    /**
     * Get MusicService instance
     */
    @Nullable
    private MusicService getMusicService() {
        if (musicService == null && playbackManager != null) {
            musicService = playbackManager.getMusicService();
        }
        return musicService;
    }
    
    /**
     * Get current song object (internal use)
     */
    @Nullable
    private Song getCurrentSongObject() {
        return playbackManager != null ? playbackManager.getCurrentSong() : null;
    }
    
    /**
     * Convert Song object to JSON
     */
    private JSONObject songToJson(@Nullable Song song) {
        JSONObject json = new JSONObject();
        if (song == null) return json;
        
        try {
            json.put("videoId", song.getVideoId());
            json.put("title", song.getTitle());
            json.put("artist", song.getArtist() != null ? song.getArtist() : "");
            json.put("album", song.getAlbum() != null ? song.getAlbum() : "");
            json.put("thumbnailUrl", song.getThumbnailUrl() != null ? song.getThumbnailUrl() : "");
            json.put("durationSeconds", song.getDurationSeconds());
        } catch (JSONException e) {
            Log.e(TAG, "Error converting song to JSON", e);
        }
        
        return json;
    }
    
    /**
     * Convert queue list to JSON array
     */
    private JSONArray queueToJson(@Nullable List<Song> queue) {
        JSONArray jsonArray = new JSONArray();
        if (queue == null) return jsonArray;
        
        for (Song song : queue) {
            jsonArray.put(songToJson(song));
        }
        
        return jsonArray;
    }
    
    /**
     * Format time in milliseconds to MM:SS
     */
    private String formatTime(int ms) {
        int seconds = ms / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }
    
    // ========== PLAYBACK LISTENER (Forward to JavaScript) ==========
    
    @Override
    public void onSongChanged(Song song) {
        // Can call JavaScript callback here if WebView is available
        Log.d(TAG, "Song changed: " + (song != null ? song.getTitle() : "null"));
    }
    
    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        Log.d(TAG, "Playback state changed: " + isPlaying);
    }
    
    @Override
    public void onProgressChanged(int currentMs, int durationMs) {
        // Called frequently, use sparingly
    }
    
    @Override
    public void onShuffleChanged(boolean shuffle) {
        Log.d(TAG, "Shuffle changed: " + shuffle);
    }
    
    @Override
    public void onRepeatModeChanged(MusicService.RepeatMode mode) {
        Log.d(TAG, "Repeat mode changed: " + mode.name());
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, "Playback error: " + message);
    }
    
    @Override
    public void onLoadingStateChanged(boolean isLoading) {
        Log.d(TAG, "Loading state changed: " + isLoading);
    }
    
    @Override
    public void onQueueChanged() {
        Log.d(TAG, "Queue changed");
    }
    
    /**
     * Cleanup resources when done
     */
    public void destroy() {
        if (playbackManager != null) {
            playbackManager.removeListener(this);
        }
        Log.d(TAG, "PlayerHelper destroyed");
    }
}
