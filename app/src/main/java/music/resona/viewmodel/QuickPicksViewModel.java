package music.resona.viewmodel;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import music.resona.online.bridge.models.YTItemResult;
import music.resona.recommendation.QuickPicksManager;

/**
 * ViewModel for Quick Picks feature.
 * Manages the state and data for Quick Picks recommendations.
 */
public class QuickPicksViewModel {
    
    private static final String TAG = "QuickPicksViewModel";
    
    private final QuickPicksManager manager;
    private final MutableLiveData<List<YTItemResult>> quickPicks;
    private final MutableLiveData<Boolean> isPersonalized;
    private final MutableLiveData<Boolean> isLoading;
    private final MutableLiveData<String> errorMessage;
    
    public QuickPicksViewModel(Context context) {
        this.manager = new QuickPicksManager(context);
        this.quickPicks = new MutableLiveData<>();
        this.isPersonalized = new MutableLiveData<>(false);
        this.isLoading = new MutableLiveData<>(false);
        this.errorMessage = new MutableLiveData<>();
    }
    
    public LiveData<List<YTItemResult>> getQuickPicks() {
        return quickPicks;
    }
    
    public LiveData<Boolean> getIsPersonalized() {
        return isPersonalized;
    }
    
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Load Quick Picks recommendations.
     */
    public void loadQuickPicks() {
        isLoading.setValue(true);
        
        manager.generateQuickPicks(new QuickPicksManager.QuickPicksCallback() {
            @Override
            public void onQuickPicksGenerated(List<YTItemResult> picks, boolean personalized) {
                quickPicks.postValue(picks);
                isPersonalized.postValue(personalized);
                isLoading.postValue(false);
                
                Log.d(TAG, "Loaded " + picks.size() + " Quick Picks (personalized: " + personalized + ")");
            }
            
            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
                
                Log.e(TAG, "Failed to load Quick Picks: " + error);
            }
        });
    }
    
    /**
     * Record a song play for building recommendations.
     */
    public void recordSongPlay(String videoId, String title, String artist, 
                              String artistId, String thumbnail, int duration, long playDuration) {
        manager.recordSongPlay(videoId, title, artist, artistId, thumbnail, duration, playDuration);
    }
    
    /**
     * Record relationship between current and next song.
     */
    public void recordSongRelationship(String currentVideoId, String nextVideoId) {
        manager.recordSongRelationship(currentVideoId, nextVideoId);
    }
}
