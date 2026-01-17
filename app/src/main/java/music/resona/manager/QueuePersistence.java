package music.resona.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import music.resona.models.Song;

/**
 * Handles queue state persistence using SharedPreferences.
 * Saves and restores queue, position, shuffle state, and history.
 * Phase 2 Implementation.
 */
public class QueuePersistence {
    
    private static final String TAG = "QueuePersistence";
    private static final String PREFS_NAME = "queue_state";
    private static final String KEY_QUEUE = "original_queue";
    private static final String KEY_CURRENT_INDEX = "current_index";
    private static final String KEY_SHUFFLE_ENABLED = "shuffle_enabled";
    private static final String KEY_REPEAT_MODE = "repeat_mode";
    private static final String KEY_HISTORY = "play_history";
    private static final String KEY_CURRENT_POSITION_MS = "current_position_ms";
    private static final String KEY_SHUFFLE_SEED = "shuffle_seed";
    
    private final SharedPreferences prefs;
    private final Gson gson;
    
    public QueuePersistence(@NonNull Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }
    
    /**
     * Save complete queue state.
     */
    public void saveQueueState(@NonNull QueueState state) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            
            // Save queue as JSON
            String queueJson = gson.toJson(state.queue);
            editor.putString(KEY_QUEUE, queueJson);
            
            // Save history as JSON
            String historyJson = gson.toJson(state.history);
            editor.putString(KEY_HISTORY, historyJson);
            
            // Save state
            editor.putInt(KEY_CURRENT_INDEX, state.currentIndex);
            editor.putBoolean(KEY_SHUFFLE_ENABLED, state.isShuffleEnabled);
            editor.putString(KEY_REPEAT_MODE, state.repeatMode);
            editor.putInt(KEY_CURRENT_POSITION_MS, state.currentPositionMs);
            editor.putLong(KEY_SHUFFLE_SEED, state.shuffleSeed);
            
            editor.apply();
            Log.d(TAG, "Queue state saved: " + state.queue.size() + " songs");
        } catch (Exception e) {
            Log.e(TAG, "Error saving queue state", e);
        }
    }
    
    /**
     * Restore saved queue state.
     * Returns null if no saved state exists.
     */
    @Nullable
    public QueueState restoreQueueState() {
        try {
            String queueJson = prefs.getString(KEY_QUEUE, null);
            if (queueJson == null) {
                Log.d(TAG, "No saved queue state found");
                return null;
            }
            
            // Parse queue
            Type listType = new TypeToken<ArrayList<Song>>() {}.getType();
            List<Song> queue = gson.fromJson(queueJson, listType);
            
            if (queue == null || queue.isEmpty()) {
                Log.d(TAG, "Empty queue in saved state");
                return null;
            }
            
            // Parse history
            String historyJson = prefs.getString(KEY_HISTORY, null);
            List<Song> history = new ArrayList<>();
            if (historyJson != null) {
                history = gson.fromJson(historyJson, listType);
                if (history == null) {
                    history = new ArrayList<>();
                }
            }
            
            // Get state
            int currentIndex = prefs.getInt(KEY_CURRENT_INDEX, 0);
            boolean isShuffleEnabled = prefs.getBoolean(KEY_SHUFFLE_ENABLED, false);
            String repeatMode = prefs.getString(KEY_REPEAT_MODE, "OFF");
            int currentPositionMs = prefs.getInt(KEY_CURRENT_POSITION_MS, 0);
            long shuffleSeed = prefs.getLong(KEY_SHUFFLE_SEED, 0);
            
            QueueState state = new QueueState(
                queue,
                history,
                currentIndex,
                isShuffleEnabled,
                repeatMode,
                currentPositionMs,
                shuffleSeed
            );
            
            Log.d(TAG, "Queue state restored: " + queue.size() + " songs, position: " + currentIndex);
            return state;
        } catch (Exception e) {
            Log.e(TAG, "Error restoring queue state", e);
            return null;
        }
    }
    
    /**
     * Clear all saved queue state.
     */
    public void clearSavedState() {
        prefs.edit().clear().apply();
        Log.d(TAG, "Queue state cleared");
    }
    
    /**
     * Check if saved state exists.
     */
    public boolean hasSavedState() {
        return prefs.contains(KEY_QUEUE);
    }
    
    /**
     * Save only the current playback position (for quick updates).
     */
    public void savePosition(int positionMs) {
        prefs.edit()
            .putInt(KEY_CURRENT_POSITION_MS, positionMs)
            .apply();
    }
    
    /**
     * Data class for queue state.
     */
    public static class QueueState {
        public final List<Song> queue;
        public final List<Song> history;
        public final int currentIndex;
        public final boolean isShuffleEnabled;
        public final String repeatMode;
        public final int currentPositionMs;
        public final long shuffleSeed;
        
        public QueueState(
            @NonNull List<Song> queue,
            @NonNull List<Song> history,
            int currentIndex,
            boolean isShuffleEnabled,
            @NonNull String repeatMode,
            int currentPositionMs,
            long shuffleSeed
        ) {
            this.queue = queue;
            this.history = history;
            this.currentIndex = currentIndex;
            this.isShuffleEnabled = isShuffleEnabled;
            this.repeatMode = repeatMode;
            this.currentPositionMs = currentPositionMs;
            this.shuffleSeed = shuffleSeed;
        }
    }
}
