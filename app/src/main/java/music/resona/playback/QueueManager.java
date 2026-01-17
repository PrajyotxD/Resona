package music.resona.playback;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import music.resona.models.Song;

/**
 * Core queue management system with persistence and multiple queue types.
 * Implements automatic state saving and restoration across app restarts.
 */
public class QueueManager {
    
    private static final String TAG = "QueueManager";
    private static final String PREFS_NAME = "queue_manager_prefs";
    private static final String KEY_CURRENT_QUEUE = "current_queue";
    private static final String KEY_CURRENT_INDEX = "current_index";
    private static final String KEY_CURRENT_POSITION = "current_position";
    private static final String KEY_QUEUE_TYPE = "queue_type";
    private static final long SAVE_INTERVAL_SECONDS = 30;
    
    private final Context context;
    private final SharedPreferences preferences;
    private final Gson gson;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final ShuffleManager shuffleManager;
    
    // Queue state
    private PlaybackQueue currentQueue;
    private int currentIndex = 0;
    private long currentPosition = 0; // Position in milliseconds
    private QueueType currentQueueType = QueueType.STATIC;
    
    // State tracking
    private volatile boolean isDirty = false;
    private QueueStateListener stateListener;
    
    public QueueManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.executor = Executors.newCachedThreadPool();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.shuffleManager = new ShuffleManager();
        
        // Start periodic saving
        startPeriodicSaving();
        
        // Restore previous state
        restoreQueueState();
    }
    
    /**
     * Replace entire queue with new content.
     */
    public void playQueue(@NonNull PlaybackQueue queue) {
        Log.d(TAG, "Playing new queue: " + queue.getTitle() + " (" + queue.size() + " items)");
        
        executor.execute(() -> {
            this.currentQueue = queue;
            this.currentIndex = 0;
            this.currentPosition = 0;
            this.currentQueueType = queue.getType();
            this.isDirty = true;
            
            // Apply current shuffle state if enabled
            if (shuffleManager.isShuffleEnabled()) {
                shuffleManager.shuffleQueue(currentQueue, currentIndex);
            }
            
            notifyQueueChanged();
        });
    }
    
    /**
     * Add songs to end of current queue.
     */
    public void addToQueue(@NonNull List<Song> songs) {
        if (currentQueue == null) {
            Log.w(TAG, "No current queue to add to");
            return;
        }
        
        executor.execute(() -> {
            int previousSize = currentQueue.size();
            currentQueue.addSongs(songs);
            this.isDirty = true;
            
            Log.d(TAG, "Added " + songs.size() + " songs to queue (total: " + currentQueue.size() + ")");
            
            // If shuffle is enabled, insert new songs properly
            if (shuffleManager.isShuffleEnabled()) {
                shuffleManager.insertSongsIntoShuffle(songs, previousSize);
            }
            
            notifyQueueChanged();
        });
    }
    
    /**
     * Get current queue asynchronously.
     */
    public void getCurrentQueue(@NonNull QueueCallback callback) {
        executor.execute(() -> {
            if (currentQueue != null) {
                callback.onQueueLoaded(currentQueue);
            } else {
                callback.onError("No current queue");
            }
        });
    }
    
    /**
     * Get song at specific index.
     */
    @Nullable
    public Song getSongAtIndex(int index) {
        if (currentQueue == null || index < 0 || index >= currentQueue.size()) {
            return null;
        }
        
        // Apply shuffle mapping if enabled
        int actualIndex = shuffleManager.isShuffleEnabled() ? 
            shuffleManager.getShuffledIndex(index) : index;
            
        return currentQueue.getSongs().get(actualIndex);
    }
    
    /**
     * Get current playing song.
     */
    @Nullable
    public Song getCurrentSong() {
        return getSongAtIndex(currentIndex);
    }
    
    /**
     * Get current index in queue.
     */
    public int getCurrentIndex() {
        return currentIndex;
    }
    
    /**
     * Set current index (for skipping).
     */
    public void setCurrentIndex(int index) {
        if (currentQueue != null && index >= 0 && index < currentQueue.size()) {
            executor.execute(() -> {
                this.currentIndex = index;
                this.currentPosition = 0;
                this.isDirty = true;
                notifyIndexChanged();
            });
        }
    }
    
    /**
     * Get current position in current song.
     */
    public long getCurrentPosition() {
        return currentPosition;
    }
    
    /**
     * Set current position in current song.
     */
    public void setCurrentPosition(long position) {
        this.currentPosition = Math.max(0, position);
        this.isDirty = true;
    }
    
    /**
     * Skip to next song.
     */
    public void skipToNext() {
        executor.execute(() -> {
            if (currentQueue != null && hasNext()) {
                currentIndex++;
                currentPosition = 0;
                isDirty = true;
                
                Log.d(TAG, "Skipped to next: " + currentIndex + "/" + currentQueue.size());
                notifyIndexChanged();
            }
        });
    }
    
    /**
     * Skip to previous song.
     */
    public void skipToPrevious() {
        executor.execute(() -> {
            if (currentQueue != null && hasPrevious()) {
                currentIndex--;
                currentPosition = 0;
                isDirty = true;
                
                Log.d(TAG, "Skipped to previous: " + currentIndex + "/" + currentQueue.size());
                notifyIndexChanged();
            }
        });
    }
    
    /**
     * Check if there's a next song.
     */
    public boolean hasNext() {
        return currentQueue != null && currentIndex < currentQueue.size() - 1;
    }
    
    /**
     * Check if there's a previous song.
     */
    public boolean hasPrevious() {
        return currentIndex > 0;
    }
    
    /**
     * Enable or disable shuffle.
     */
    public void setShuffleEnabled(boolean enabled) {
        executor.execute(() -> {
            if (shuffleManager.isShuffleEnabled() != enabled) {
                shuffleManager.setShuffleEnabled(enabled);
                
                if (currentQueue != null) {
                    if (enabled) {
                        shuffleManager.shuffleQueue(currentQueue, currentIndex);
                    } else {
                        shuffleManager.disableShuffle();
                        // Restore original index
                        currentIndex = shuffleManager.getOriginalIndex(currentIndex);
                    }
                    
                    isDirty = true;
                    notifyShuffleStateChanged();
                }
            }
        });
    }
    
    /**
     * Check if shuffle is enabled.
     */
    public boolean isShuffleEnabled() {
        return shuffleManager.isShuffleEnabled();
    }
    
    /**
     * Set queue state listener.
     */
    public void setQueueStateListener(@Nullable QueueStateListener listener) {
        this.stateListener = listener;
    }
    
    /**
     * Save queue state immediately.
     */
    public void saveStateNow() {
        executor.execute(this::saveQueueState);
    }
    
    /**
     * Start periodic queue state saving.
     */
    private void startPeriodicSaving() {
        scheduler.scheduleAtFixedRate(() -> {
            if (isDirty) {
                saveQueueState();
                isDirty = false;
            }
        }, SAVE_INTERVAL_SECONDS, SAVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    /**
     * Save current queue state to persistent storage.
     */
    public void saveQueueState() {
        if (currentQueue == null) return;
        
        try {
            String queueJson = gson.toJson(currentQueue);
            preferences.edit()
                    .putString(KEY_CURRENT_QUEUE, queueJson)
                    .putInt(KEY_CURRENT_INDEX, currentIndex)
                    .putLong(KEY_CURRENT_POSITION, currentPosition)
                    .putString(KEY_QUEUE_TYPE, currentQueueType.name())
                    .apply();
                    
            Log.d(TAG, "Queue state saved: " + currentQueue.size() + " items, index: " + currentIndex);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save queue state", e);
        }
    }
    
    /**
     * Restore queue state from persistent storage.
     */
    public void restoreQueueState() {
        executor.execute(() -> {
            try {
                String queueJson = preferences.getString(KEY_CURRENT_QUEUE, null);
                if (queueJson != null) {
                    Type queueType = new TypeToken<StaticQueue>(){}.getType();
                    currentQueue = gson.fromJson(queueJson, queueType);
                    currentIndex = preferences.getInt(KEY_CURRENT_INDEX, 0);
                    currentPosition = preferences.getLong(KEY_CURRENT_POSITION, 0);
                    
                    String typeStr = preferences.getString(KEY_QUEUE_TYPE, QueueType.STATIC.name());
                    currentQueueType = QueueType.valueOf(typeStr);
                    
                    Log.d(TAG, "Queue state restored: " + currentQueue.size() + " items, index: " + currentIndex);
                    notifyQueueChanged();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to restore queue state", e);
            }
        });
    }
    
    /**
     * Notify listeners of queue changes.
     */
    private void notifyQueueChanged() {
        if (stateListener != null) {
            stateListener.onQueueChanged(currentQueue);
        }
    }
    
    /**
     * Notify listeners of index changes.
     */
    private void notifyIndexChanged() {
        if (stateListener != null) {
            stateListener.onCurrentIndexChanged(currentIndex);
        }
    }
    
    /**
     * Notify listeners of shuffle state changes.
     */
    private void notifyShuffleStateChanged() {
        if (stateListener != null) {
            stateListener.onShuffleStateChanged(shuffleManager.isShuffleEnabled());
        }
    }
    
    /**
     * Release resources.
     */
    public void release() {
        saveStateNow();
        scheduler.shutdown();
        Log.d(TAG, "QueueManager released");
    }
    
    /**
     * Queue types enumeration.
     */
    public enum QueueType {
        STATIC,              // Regular playlist/album
        YOUTUBE_DYNAMIC,     // YouTube radio with continuation
        ALBUM_RADIO,         // Album-based radio
        LOCAL_ALBUM_RADIO    // Local album radio
    }
    
    /**
     * Callback interface for queue operations.
     */
    public interface QueueCallback {
        void onQueueLoaded(PlaybackQueue queue);
        void onError(String error);
    }
    
    /**
     * Listener interface for queue state changes.
     */
    public interface QueueStateListener {
        void onQueueChanged(@Nullable PlaybackQueue queue);
        void onCurrentIndexChanged(int newIndex);
        void onShuffleStateChanged(boolean shuffleEnabled);
    }
}