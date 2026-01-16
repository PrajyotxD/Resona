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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.callbacks.HomePageCallback;
import music.resona.online.bridge.exceptions.BridgeException;
import music.resona.online.bridge.models.ChipResult;
import music.resona.online.bridge.models.HomePageResult;
import music.resona.online.bridge.models.HomeSectionResult;
import music.resona.online.bridge.models.YTItemResult;

/**
 * Optimized ViewModel for managing home feed data with efficient pagination.
 * 
 * <p>Key improvements:</p>
 * <ul>
 *   <li>Separate loading states for initial load and pagination</li>
 *   <li>Debounced scroll-based loading</li>
 *   <li>Better error handling and recovery</li>
 *   <li>Optimized data updates to prevent jank</li>
 * </ul>
 */
public class HomeFeedViewModel extends ViewModel {
    
    private static final String TAG = "HomeFeedViewModel";
    private static final int INITIAL_LOAD_BATCH_SIZE = 3; // Load 3 sections initially
    private static final int PAGINATION_BATCH_SIZE = 2; // Load 2 sections per scroll
    private static final long PAGINATION_DEBOUNCE_MS = 500; // Debounce scroll events
    
    // Loading states
    public enum LoadingState {
        IDLE,           // Not loading
        INITIAL_LOAD,   // First time loading
        PAGINATING,     // Loading more content while scrolling
        REFRESHING      // Pull to refresh
    }
    
    // LiveData
    private final MutableLiveData<List<HomeSectionResult>> homeSections = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ChipResult>> chips = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<YTItemResult>> quickPicks = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<LoadingState> loadingState = new MutableLiveData<>(LoadingState.IDLE);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    // Internal state
    private String continuationToken;
    private final AtomicBoolean isPaginating = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Runnable pendingPaginationRequest;
    private ChipResult currentFilter;
    
    // Getters
    @NonNull
    public LiveData<List<HomeSectionResult>> getHomeSections() {
        return homeSections;
    }
    
    @NonNull
    public LiveData<List<ChipResult>> getChips() {
        return chips;
    }
    
    @NonNull
    public LiveData<List<YTItemResult>> getQuickPicks() {
        return quickPicks;
    }
    
    @NonNull
    public LiveData<LoadingState> getLoadingState() {
        return loadingState;
    }
    
    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Loads initial home feed data with optimized batching.
     */
    public void loadHomeData() {
        if (loadingState.getValue() != LoadingState.IDLE) {
            Log.d(TAG, "Already loading, skipping request");
            return;
        }
        
        Log.d(TAG, "Starting initial home feed load");
        loadingState.setValue(LoadingState.INITIAL_LOAD);
        currentFilter = null;
        
        InnertubeBridge.getHomeAsync(new HomePageCallback() {
            @Override
            public void onSuccess(HomePageResult result) {
                handleInitialLoadSuccess(result);
            }
            
            @Override
            public void onError(BridgeException error) {
                handleInitialLoadError(error);
            }
        });
    }
    
    /**
     * Refreshes the home feed data.
     */
    public void refresh() {
        Log.d(TAG, "Refreshing home feed");
        loadingState.setValue(LoadingState.REFRESHING);
        
        // Clear current data
        homeSections.setValue(new ArrayList<>());
        continuationToken = null;
        currentFilter = null;
        
        InnertubeBridge.getHomeAsync(new HomePageCallback() {
            @Override
            public void onSuccess(HomePageResult result) {
                handleInitialLoadSuccess(result);
            }
            
            @Override
            public void onError(BridgeException error) {
                Log.e(TAG, "Error refreshing: " + error.getMessage());
                errorMessage.setValue("Failed to refresh: " + error.getMessage());
                loadingState.setValue(LoadingState.IDLE);
            }
        });
    }
    
    /**
     * Loads more data with debouncing to prevent excessive API calls.
     */
    public void loadMoreData() {
        if (!canLoadMore()) {
            return;
        }
        
        // Cancel any pending pagination request
        if (pendingPaginationRequest != null) {
            mainHandler.removeCallbacks(pendingPaginationRequest);
        }
        
        // Schedule debounced pagination
        pendingPaginationRequest = this::executePagination;
        mainHandler.postDelayed(pendingPaginationRequest, PAGINATION_DEBOUNCE_MS);
    }
    
    /**
     * Checks if more data can be loaded.
     */
    public boolean canLoadMore() {
        return continuationToken != null 
            && !isPaginating.get() 
            && loadingState.getValue() == LoadingState.IDLE;
    }
    
    /**
     * Filters home feed by chip selection.
     */
    public void filterByChip(@NonNull ChipResult chip) {
        Log.d(TAG, "Filtering by chip: " + chip.getTitle());
        loadingState.setValue(LoadingState.INITIAL_LOAD);
        currentFilter = chip;
        
        executorService.execute(() -> {
            try {
                HomePageResult result = InnertubeBridge.browseWithParamsSync(
                    chip.getBrowseId(), 
                    chip.getParams()
                );
                
                mainHandler.post(() -> {
                    handleFilterSuccess(result);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error filtering by chip", e);
                mainHandler.post(() -> {
                    errorMessage.setValue("Failed to filter: " + e.getMessage());
                    loadingState.setValue(LoadingState.IDLE);
                });
            }
        });
    }
    
    // Private helper methods
    
    private void handleInitialLoadSuccess(@NonNull HomePageResult result) {
        Log.d(TAG, "Initial load successful: " + result.getSections().size() + " sections");
        
        // Extract and set quick picks
        List<YTItemResult> extractedQuickPicks = extractQuickPicks(result.getSections());
        if (!extractedQuickPicks.isEmpty()) {
            quickPicks.setValue(extractedQuickPicks);
            Log.d(TAG, "Extracted " + extractedQuickPicks.size() + " quick picks");
        }
        
        // Set sections
        homeSections.setValue(new ArrayList<>(result.getSections()));
        
        // Set chips if available
        if (result.getChips() != null && !result.getChips().isEmpty()) {
            chips.setValue(result.getChips());
        }
        
        // Store continuation token
        continuationToken = result.getContinuation();
        Log.d(TAG, "Continuation token: " + (continuationToken != null ? "available" : "none"));
        
        // Update loading state
        loadingState.setValue(LoadingState.IDLE);
        errorMessage.setValue(null);
        
        // Auto-load more if we have too few sections for proper scrolling
        if (result.getSections().size() < 5 && continuationToken != null) {
            Log.d(TAG, "Too few sections (" + result.getSections().size() + "), auto-loading more");
            mainHandler.postDelayed(this::loadMoreData, 500);
        }
    }
    
    private void handleInitialLoadError(@NonNull BridgeException error) {
        Log.e(TAG, "Initial load error: " + error.getMessage());
        errorMessage.setValue("Failed to load home feed: " + error.getMessage());
        loadingState.setValue(LoadingState.IDLE);
        
        // Try explore feed as fallback
        loadExploreFallback();
    }
    
    private void loadExploreFallback() {
        Log.d(TAG, "Loading explore feed as fallback");
        
        InnertubeBridge.getExploreAsync(new HomePageCallback() {
            @Override
            public void onSuccess(HomePageResult result) {
                Log.d(TAG, "Explore fallback successful");
                homeSections.setValue(new ArrayList<>(result.getSections()));
                continuationToken = result.getContinuation();
                errorMessage.setValue(null);
            }
            
            @Override
            public void onError(BridgeException error) {
                Log.e(TAG, "Explore fallback failed: " + error.getMessage());
                errorMessage.setValue("Unable to load content");
            }
        });
    }
    
    private void executePagination() {
        if (!canLoadMore()) {
            return;
        }
        
        isPaginating.set(true);
        loadingState.setValue(LoadingState.PAGINATING);
        
        final String token = continuationToken;
        Log.d(TAG, "Loading more data with continuation token");
        
        executorService.execute(() -> {
            try {
                HomePageResult result = InnertubeBridge.getHomeContinuationSync(token);
                mainHandler.post(() -> handlePaginationSuccess(result));
            } catch (Exception e) {
                Log.e(TAG, "Pagination error", e);
                mainHandler.post(() -> handlePaginationError(e));
            }
        });
    }
    
    private void handlePaginationSuccess(@NonNull HomePageResult result) {
        Log.d(TAG, "Pagination successful: " + result.getSections().size() + " new sections");
        
        if (result.getSections().isEmpty()) {
            Log.w(TAG, "Pagination returned empty results");
            continuationToken = null;
        } else {
            // Append new sections to existing ones
            List<HomeSectionResult> current = homeSections.getValue();
            List<HomeSectionResult> updated = new ArrayList<>(current != null ? current : new ArrayList<>());
            updated.addAll(result.getSections());
            
            homeSections.setValue(updated);
            continuationToken = result.getContinuation();
            
            Log.d(TAG, "Total sections: " + updated.size());
        }
        
        isPaginating.set(false);
        loadingState.setValue(LoadingState.IDLE);
    }
    
    private void handlePaginationError(@NonNull Exception error) {
        Log.e(TAG, "Pagination error: " + error.getMessage());
        isPaginating.set(false);
        loadingState.setValue(LoadingState.IDLE);
        // Don't show error for pagination failures, just stop loading
    }
    
    private void handleFilterSuccess(@NonNull HomePageResult result) {
        Log.d(TAG, "Filter successful: " + result.getSections().size() + " sections");
        
        homeSections.setValue(new ArrayList<>(result.getSections()));
        
        // Extract quick picks from filtered results
        List<YTItemResult> extractedQuickPicks = extractQuickPicks(result.getSections());
        quickPicks.setValue(extractedQuickPicks.isEmpty() ? new ArrayList<>() : extractedQuickPicks);
        
        continuationToken = result.getContinuation();
        loadingState.setValue(LoadingState.IDLE);
        errorMessage.setValue(null);
        
        // Auto-load more if we have too few sections for proper scrolling
        if (result.getSections().size() < 5 && continuationToken != null) {
            Log.d(TAG, "Too few sections after filter (" + result.getSections().size() + "), auto-loading more");
            mainHandler.postDelayed(this::loadMoreData, 500);
        }
    }
    
    @NonNull
    private List<YTItemResult> extractQuickPicks(@NonNull List<HomeSectionResult> sections) {
        Log.d(TAG, "Extracting quick picks from " + sections.size() + " sections");
        
        for (HomeSectionResult section : sections) {
            String title = section.getTitle();
            if (title == null) continue;
            
            String lowerTitle = title.toLowerCase();
            if (lowerTitle.contains("quick") || 
                lowerTitle.contains("pick") ||
                lowerTitle.contains("recommend") ||
                lowerTitle.contains("mixed") ||
                lowerTitle.contains("forgotten") ||
                lowerTitle.contains("favorite")) {
                
                List<YTItemResult> items = section.getItems();
                if (items != null && !items.isEmpty()) {
                    // Filter for songs only
                    List<YTItemResult> songs = new ArrayList<>();
                    for (YTItemResult item : items) {
                        String type = item.getType();
                        if (type != null && type.equalsIgnoreCase("SONG")) {
                            songs.add(item);
                            if (songs.size() >= 20) break;
                        }
                    }
                    
                    if (!songs.isEmpty()) {
                        Log.d(TAG, "Found " + songs.size() + " quick picks in: " + title);
                        return songs;
                    }
                }
            }
        }
        
        Log.d(TAG, "No quick picks section found");
        return new ArrayList<>();
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
        if (pendingPaginationRequest != null) {
            mainHandler.removeCallbacks(pendingPaginationRequest);
        }
    }
}
