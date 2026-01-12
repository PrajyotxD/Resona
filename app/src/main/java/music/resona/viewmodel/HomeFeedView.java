package music.resona.viewmodel;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.callbacks.HomePageCallback;
import music.resona.online.bridge.exceptions.BridgeException;
import music.resona.online.bridge.models.ChipResult;
import music.resona.online.bridge.models.HomePageResult;
import music.resona.online.bridge.models.HomeSectionResult;
import music.resona.online.bridge.models.YTItemResult;

/**
 * ViewModel for managing home feed data and state.
 * 
 * <p>Handles fetching home feed content from YouTube API, managing pagination
 * with continuation tokens, and providing observable state to the UI layer.</p>
 * 
 * <p>Thread Safety: This class uses LiveData for thread-safe state management.
 * All API calls are executed asynchronously.</p>
 */
public class HomeFeedView extends ViewModel {
    
    private static final String TAG = "HomeFeedView";
    private static final int AUTO_LOAD_THRESHOLD = 5;
    private static final int AUTO_LOAD_DELAY_MS = 300;
    
    private final MutableLiveData<List<HomeSectionResult>> homeSections;
    private final MutableLiveData<List<ChipResult>> chips;
    private final MutableLiveData<List<YTItemResult>> quickPicks;
    private final MutableLiveData<Boolean> isLoading;
    private final MutableLiveData<String> errorMessage;
    private final Handler mainHandler;
    private final ExecutorService executorService;
    
    @Nullable
    private String continuationToken;
    private volatile boolean isLoadingMore;
    
    public HomeFeedView() {
        this.homeSections = new MutableLiveData<>(new ArrayList<>());
        this.chips = new MutableLiveData<>(new ArrayList<>());
        this.quickPicks = new MutableLiveData<>(new ArrayList<>());
        this.isLoading = new MutableLiveData<>(false);
        this.errorMessage = new MutableLiveData<>();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executorService = Executors.newSingleThreadExecutor();
        this.isLoadingMore = false;
    }
    
    /**
     * Returns observable LiveData containing the list of home feed sections.
     * 
     * @return LiveData containing immutable list of HomeSectionResult
     */
    @NonNull
    public LiveData<List<HomeSectionResult>> getHomeSections() {
        return homeSections;
    }
    
    /**
     * Returns observable LiveData containing the list of filter chips.
     * 
     * @return LiveData containing list of ChipResult
     */
    @NonNull
    public LiveData<List<ChipResult>> getChips() {
        return chips;
    }
    
    /**
     * Returns observable LiveData containing the list of quick picks.
     * 
     * @return LiveData containing list of YTItemResult for quick picks
     */
    @NonNull
    public LiveData<List<music.resona.online.bridge.models.YTItemResult>> getQuickPicks() {
        return quickPicks;
    }
    
    /**
     * Returns observable LiveData indicating whether data is currently loading.
     * 
     * @return LiveData containing Boolean loading state
     */
    @NonNull
    public LiveData<Boolean> getLoadingState() {
        return isLoading;
    }
    
    /**
     * Returns observable LiveData containing error messages.
     * 
     * @return LiveData containing error message String, or null if no error
     */
    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Loads the initial home feed data.
     * 
     * <p>This method should be called when the fragment is first created.
     * It will automatically load additional content if the initial response
     * contains fewer sections than required for proper scrolling.</p>
     * 
     * <p>On error, automatically falls back to explore feed data.</p>
     */
    public void loadHomeData() {
        if (Boolean.TRUE.equals(isLoading.getValue())) {
            Log.d(TAG, "Already loading, ignoring duplicate request");
            return;
        }
        
        Log.d(TAG, "Loading home feed data...");
        isLoading.setValue(true);
        
        logVisitorData();
        
        InnertubeBridge.getHomeAsync(new HomePageCallback() {
            @Override
            public void onSuccess(HomePageResult result) {
                handleHomeDataSuccess(result);
            }
            
            @Override
            public void onError(BridgeException error) {
                handleHomeDataError(error);
            }
        });
    }
    
    private void handleHomeDataSuccess(@NonNull HomePageResult result) {
        Log.d(TAG, "Home data loaded successfully with " + result.getSections().size() + " sections");
        
        // Extract quick picks from first section if it contains "quick" in title
        List<music.resona.online.bridge.models.YTItemResult> extractedQuickPicks = extractQuickPicks(result.getSections());
        if (!extractedQuickPicks.isEmpty()) {
            quickPicks.setValue(extractedQuickPicks);
            Log.d(TAG, "Extracted " + extractedQuickPicks.size() + " quick picks");
        }
        
        homeSections.setValue(result.getSections());
        
        // Set chips if available
        if (result.getChips() != null && !result.getChips().isEmpty()) {
            chips.setValue(result.getChips());
            Log.d(TAG, "Loaded " + result.getChips().size() + " filter chips");
        }
        
        continuationToken = result.getContinuation();
        isLoading.setValue(false);
        isLoadingMore = false;
        
        Log.d(TAG, "Continuation token: " + (continuationToken != null ? "Available" : "None"));
        
        if (shouldAutoLoadMore(result.getSections().size())) {
            scheduleAutoLoad();
        }
    }
    
    private void handleHomeDataError(@NonNull BridgeException error) {
        Log.e(TAG, "Error loading home data: " + error.getMessage());
        errorMessage.setValue("Failed to load home feed: " + error.getMessage());
        isLoading.setValue(false);
        loadExploreData();
    }
    
    private void loadExploreData() {
        Log.d(TAG, "Loading explore feed as fallback...");
        
        InnertubeBridge.getExploreAsync(new HomePageCallback() {
            @Override
            public void onSuccess(HomePageResult result) {
                Log.d(TAG, "Explore data loaded successfully with " + result.getSections().size() + " sections");
                homeSections.setValue(result.getSections());
                continuationToken = result.getContinuation();
                errorMessage.setValue(null);
            }
            
            @Override
            public void onError(BridgeException error) {
                Log.e(TAG, "Error loading explore data: " + error.getMessage());
                errorMessage.setValue("Failed to load content: " + error.getMessage());
            }
        });
    }
    
    /**
     * Loads additional home feed sections using the continuation token.
     * 
     * <p>This method is called when the user scrolls to the bottom of the feed.
     * It will automatically continue loading if more content is needed for proper scrolling.</p>
     * 
     * <p>Thread Safety: This method is thread-safe and prevents duplicate concurrent requests.</p>
     */
    public void loadMoreData() {
        if (isLoadingMore) {
            Log.d(TAG, "Cannot load more: already loading");
            return;
        }
        
        if (continuationToken == null) {
            Log.d(TAG, "Cannot load more: no continuation token");
            return;
        }
        
        isLoadingMore = true;
        logContinuationToken();
        
        final String tokenToUse = continuationToken;
        
        executorService.execute(() -> {
            try {
                loadMoreDataInBackground(tokenToUse);
            } catch (Exception error) {
                handleLoadMoreError(error);
            }
        });
    }
    
    /**
     * Checks whether more data can be loaded.
     * 
     * @return true if continuation token exists and not currently loading, false otherwise
     */
    public boolean canLoadMore() {
        return continuationToken != null && !isLoadingMore;
    }
    
    // Helper methods
    
    private void loadMoreDataInBackground(@NonNull String token) throws Exception {
        logVisitorDataForContinuation();
        
        HomePageResult result = InnertubeBridge.getHomeContinuationSync(token);
        Log.d(TAG, "Continuation loaded " + result.getSections().size() + " sections");
        
        if (result.getSections().isEmpty()) {
            handleEmptyContinuationResult();
            return;
        }
        
        List<HomeSectionResult> updatedSections = appendNewSections(result.getSections());
        continuationToken = result.getContinuation();
        
        Log.d(TAG, "New continuation token: " + (continuationToken != null ? "Available" : "None"));
        Log.d(TAG, "Total sections now: " + updatedSections.size());
        
        homeSections.postValue(updatedSections);
        isLoadingMore = false;
        
        if (shouldAutoLoadMore(updatedSections.size())) {
            scheduleAutoLoad();
        }
    }
    
    @NonNull
    private List<HomeSectionResult> appendNewSections(@NonNull List<HomeSectionResult> newSections) {
        List<HomeSectionResult> currentSections = homeSections.getValue();
        if (currentSections == null) {
            currentSections = Collections.emptyList();
        }
        
        List<HomeSectionResult> updatedSections = new ArrayList<>(currentSections);
        updatedSections.addAll(newSections);
        return updatedSections;
    }
    
    private void handleEmptyContinuationResult() {
        Log.w(TAG, "Continuation returned empty - possible reasons:");
        Log.w(TAG, "  - End of content reached");
        Log.w(TAG, "  - Invalid continuation token");
        Log.w(TAG, "  - Visitor data mismatch");
        
        mainHandler.post(() -> isLoadingMore = false);
    }
    
    private void handleLoadMoreError(@NonNull Exception error) {
        Log.e(TAG, "Error loading more data: " + error.getMessage(), error);
        isLoadingMore = false;
    }
    
    private boolean shouldAutoLoadMore(int currentSectionCount) {
        return currentSectionCount < AUTO_LOAD_THRESHOLD && continuationToken != null;
    }
    
    private void scheduleAutoLoad() {
        Log.d(TAG, "Auto-loading more sections...");
        mainHandler.postDelayed(this::loadMoreData, AUTO_LOAD_DELAY_MS);
    }
    
    private void logVisitorData() {
        String visitorData = InnertubeBridge.getVisitorData();
        if (visitorData != null && visitorData.length() > 15) {
            Log.d(TAG, "Visitor data: " + visitorData.substring(0, 15) + "...");
        } else {
            Log.d(TAG, "Visitor data: " + visitorData);
        }
    }
    
    private void logVisitorDataForContinuation() {
        String visitorData = InnertubeBridge.getVisitorDataSync();
        if (visitorData != null && visitorData.length() > 10) {
            Log.d(TAG, "Using visitor data for continuation: " + visitorData.substring(0, 10) + "...");
        } else {
            Log.d(TAG, "Using visitor data for continuation: " + visitorData);
        }
    }
    
    private void logContinuationToken() {
        if (continuationToken != null && continuationToken.length() > 50) {
            Log.d(TAG, "Loading more with token: " + continuationToken.substring(0, 50) + "...");
        } else {
            Log.d(TAG, "Loading more with token: " + continuationToken);
        }
    }
    
    @NonNull
    private List<music.resona.online.bridge.models.YTItemResult> extractQuickPicks(@NonNull List<HomeSectionResult> sections) {
        Log.d(TAG, "Searching for quick picks in " + sections.size() + " sections");
        
        for (HomeSectionResult section : sections) {
            String title = section.getTitle();
            Log.d(TAG, "Section title: " + title + ", items: " + (section.getItems() != null ? section.getItems().size() : 0));
            
            if (title != null) {
                String lowerTitle = title.toLowerCase();
                // Check for quick picks, recommended, mixed, forgotten favorites, or similar patterns
                if (lowerTitle.contains("quick") || 
                    lowerTitle.contains("pick") ||
                    lowerTitle.contains("recommend") ||
                    lowerTitle.contains("mixed") ||
                    lowerTitle.contains("forgotten") ||
                    lowerTitle.contains("favorite")) {
                    
                    List<music.resona.online.bridge.models.YTItemResult> items = section.getItems();
                    if (items != null && !items.isEmpty()) {
                        // Filter for songs only
                        List<music.resona.online.bridge.models.YTItemResult> songs = new ArrayList<>();
                        for (music.resona.online.bridge.models.YTItemResult item : items) {
                            String type = item.getType();
                            if (type != null && type.equalsIgnoreCase("SONG")) {
                                songs.add(item);
                                if (songs.size() >= 20) break; // Limit to 20 songs for grid (4 rows x 5 cols)
                            }
                        }
                        
                        if (!songs.isEmpty()) {
                            Log.d(TAG, "Found quick picks section: " + title + " with " + songs.size() + " songs (filtered from " + items.size() + " total items)");
                            return songs;
                        }
                    }
                }
            }
        }
        
        Log.w(TAG, "No quick picks section with songs found, trying first section");
        // Fallback: filter songs from first section
        if (!sections.isEmpty() && sections.get(0).getItems() != null && !sections.get(0).getItems().isEmpty()) {
            List<music.resona.online.bridge.models.YTItemResult> songs = new ArrayList<>();
            for (music.resona.online.bridge.models.YTItemResult item : sections.get(0).getItems()) {
                String type = item.getType();
                if (type != null && type.equalsIgnoreCase("SONG")) {
                    songs.add(item);
                    if (songs.size() >= 20) break;
                }
            }
            if (!songs.isEmpty()) {
                Log.d(TAG, "Using " + songs.size() + " songs from first section as quick picks");
                return songs;
            }
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Filters home feed by selected chip (mood/genre).
     * 
     * @param chip the selected chip filter
     */
    public void filterByChip(@NonNull ChipResult chip) {
        Log.d(TAG, "Applying chip filter: " + chip.getTitle());
        isLoading.setValue(true);
        
        executorService.execute(() -> {
            try {
                HomePageResult result;
                if (chip.getBrowseId() != null && !chip.getBrowseId().isEmpty()) {
                    // Use browse endpoint with browseId
                    result = InnertubeBridge.browseWithParamsSync(chip.getBrowseId(), chip.getParams());
                    
                    mainHandler.post(() -> {
                        homeSections.setValue(result.getSections());
                        
                        // Extract quick picks from filtered results too
                        List<music.resona.online.bridge.models.YTItemResult> extractedQuickPicks = extractQuickPicks(result.getSections());
                        if (!extractedQuickPicks.isEmpty()) {
                            quickPicks.setValue(extractedQuickPicks);
                            Log.d(TAG, "Extracted " + extractedQuickPicks.size() + " quick picks from filtered results");
                        } else {
                            quickPicks.setValue(new ArrayList<>());
                        }
                        
                        continuationToken = result.getContinuation();
                        isLoading.setValue(false);
                        Log.d(TAG, "Filtered feed loaded: " + result.getSections().size() + " sections");
                    });
                } else {
                    Log.w(TAG, "Chip has no valid browseId, reloading home feed");
                    mainHandler.post(() -> {
                        isLoading.setValue(false);
                        loadHomeData();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error filtering by chip", e);
                mainHandler.post(() -> {
                    errorMessage.setValue("Failed to filter: " + e.getMessage());
                    isLoading.setValue(false);
                });
            }
        });
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "ViewModel cleared and resources released");
    }
}