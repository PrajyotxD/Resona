package music.resona.playback;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import music.resona.online.bridge.models.YTItemResult;

/**
 * Enhanced Quick Picks Manager with cold start prevention and advanced signals.
 * Coordinates between repository and UI layers with preloading capabilities.
 */
public class QuickPicksManager {
    
    private static final String TAG = "QuickPicksManager";
    
    private final QuickPicksRepository repository;
    private final Executor executor;
    
    public QuickPicksManager(@NonNull Context context) {
        this.repository = new QuickPicksRepository(context);
        this.executor = Executors.newCachedThreadPool();
    }
    
    /**
     * Generate Quick Picks with instant response from cache.
     */
    public void generateQuickPicks(@NonNull QuickPicksCallback callback) {
        executor.execute(() -> {
            repository.generateQuickPicks(new QuickPicksRepository.QuickPicksCallback() {
                @Override
                public void onSuccess(List<YTItemResult> picks, boolean personalized) {
                    callback.onQuickPicksGenerated(picks, personalized);
                }
                
                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        });
    }
    
    /**
     * Preload Quick Picks during app startup for instant cold start.
     */
    public void preloadForColdStart() {
        executor.execute(() -> {
            repository.preloadQuickPicks();
        });
    }
    
    /**
     * Invalidate cache when user preferences change.
     */
    public void invalidateCache() {
        executor.execute(() -> {
            repository.invalidateCache();
        });
    }
    
    /**
     * Record song play event for improving personalization.
     */
    public void recordSongPlay(String videoId, String title, String artist, 
                              String artistId, String thumbnail, int duration, long playDuration) {
        // Implementation would update QuickPicksDatabase
        executor.execute(() -> {
            Log.d(TAG, "Recording play event: " + title + " by " + artist);
            // Add database recording logic here
        });
    }
    
    /**
     * Record relationship between songs for better recommendations.
     */
    public void recordSongRelationship(String currentVideoId, String nextVideoId) {
        executor.execute(() -> {
            Log.d(TAG, "Recording song relationship: " + currentVideoId + " -> " + nextVideoId);
            // Add database relationship logic here
        });
    }
    
    /**
     * Callback interface for Quick Picks generation.
     */
    public interface QuickPicksCallback {
        /**
         * Called when Quick Picks are successfully generated.
         * @param picks List of recommended songs
         * @param personalized Whether picks are personalized or trending fallback
         */
        void onQuickPicksGenerated(List<YTItemResult> picks, boolean personalized);
        
        /**
         * Called when an error occurs.
         * @param error Error message
         */
        void onError(String error);
    }
}