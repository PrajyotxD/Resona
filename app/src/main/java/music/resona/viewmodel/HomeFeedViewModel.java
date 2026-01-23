package music.resona.viewmodel;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import music.resona.models.Song;
import music.resona.cache.SongPrefetchHelper;
import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.callbacks.HomePageCallback;
import music.resona.online.bridge.exceptions.BridgeException;
import music.resona.online.bridge.models.ChipResult;
import music.resona.online.bridge.models.HomePageResult;
import music.resona.online.bridge.models.HomeSectionResult;
import music.resona.online.bridge.models.YTItemResult;
import music.resona.playback.QuickPicksManager;

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
    private static final int QUICK_PICKS_PAGE_SIZE = 20; // Load 20 Quick Picks at a time
    
    private Context context; // For QuickPicksManager
    private PersonalizedHomeFeed personalizedHomeFeed; // For recommendation sections
    
    // Loading states
    public enum LoadingState {
        IDLE,           // Not loading
        INITIAL_LOAD,   // First time loading
        PAGINATING,     // Loading more content while scrolling
        REFRESHING      // Pull to refresh
    }
    
    // LiveData
    private final MutableLiveData<List<HomeSectionResult>> homeSections = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<HomeSectionResult>> personalizedSections = new MutableLiveData<>(new ArrayList<>());
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
    
    // Quick Picks pagination state
    private List<YTItemResult> allQuickPicks = new ArrayList<>(); // Full list from backend
    private int quickPicksCurrentPage = 0;
    private final AtomicBoolean isLoadingQuickPicks = new AtomicBoolean(false);
    
    // Getters
    @NonNull
    public LiveData<List<HomeSectionResult>> getHomeSections() {
        return homeSections;
    }
    
    @NonNull
    public LiveData<List<HomeSectionResult>> getPersonalizedSections() {
        return personalizedSections;
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
     * Loads more Quick Picks items (pagination for horizontal scroll).
     * Loads next batch of 20 items from the cached full list.
     */
    public void loadMoreQuickPicks() {
        if (isLoadingQuickPicks.get()) {
            Log.d(TAG, "Already loading Quick Picks, skipping");
            return;
        }
        
        if (allQuickPicks.isEmpty()) {
            Log.d(TAG, "No Quick Picks data available for pagination");
            return;
        }
        
        int currentSize = quickPicks.getValue() != null ? quickPicks.getValue().size() : 0;
        if (currentSize >= allQuickPicks.size()) {
            Log.d(TAG, "All Quick Picks already loaded (" + currentSize + "/" + allQuickPicks.size() + ")");
            return;
        }
        
        isLoadingQuickPicks.set(true);
        Log.d(TAG, "Loading more Quick Picks: page " + (quickPicksCurrentPage + 1));
        
        executorService.execute(() -> {
            try {
                // Calculate next batch
                int startIndex = currentSize;
                int endIndex = Math.min(startIndex + QUICK_PICKS_PAGE_SIZE, allQuickPicks.size());
                List<YTItemResult> nextBatch = allQuickPicks.subList(startIndex, endIndex);
                
                Log.d(TAG, "Loading Quick Picks batch: " + startIndex + " to " + endIndex + " (" + nextBatch.size() + " items)");
                
                // Add to existing list
                List<YTItemResult> updatedList = new ArrayList<>(quickPicks.getValue());
                updatedList.addAll(nextBatch);
                
                // Prefetch the new songs
                if (context != null) {
                    SongPrefetchHelper.getInstance().prefetchFromHomeFeed(nextBatch, nextBatch.size());
                }
                
                mainHandler.post(() -> {
                    quickPicks.setValue(updatedList);
                    quickPicksCurrentPage++;
                    isLoadingQuickPicks.set(false);
                    Log.d(TAG, "Quick Picks updated: now showing " + updatedList.size() + "/" + allQuickPicks.size());
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading more Quick Picks", e);
                mainHandler.post(() -> isLoadingQuickPicks.set(false));
            }
        });
    }
    
    /**
     * Checks if more Quick Picks can be loaded.
     */
    public boolean canLoadMoreQuickPicks() {
        int currentSize = quickPicks.getValue() != null ? quickPicks.getValue().size() : 0;
        return !isLoadingQuickPicks.get() && currentSize < allQuickPicks.size();
    }
    
    /**
     * Manually update Quick Picks with a new list (used for refresh).
     */
    public void updateQuickPicks(@NonNull List<YTItemResult> picks) {
        Log.d(TAG, "Manually updating Quick Picks: " + picks.size() + " items");
        
        // Store full list for pagination
        allQuickPicks = new ArrayList<>(picks);
        quickPicksCurrentPage = 0;
        
        // Return only first page initially (20 items)
        int initialSize = Math.min(QUICK_PICKS_PAGE_SIZE, picks.size());
        List<YTItemResult> firstPage = picks.subList(0, initialSize);
        
        quickPicks.setValue(firstPage);
        Log.d(TAG, "Set Quick Picks to first page: " + firstPage.size() + " items (total: " + allQuickPicks.size() + ")");
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
        
        // Check if we have personalized sections to fall back to
        List<HomeSectionResult> personalizedList = personalizedSections.getValue();
        if (personalizedList != null && !personalizedList.isEmpty()) {
            Log.d(TAG, "Using offline mode with " + personalizedList.size() + " personalized sections");
            homeSections.setValue(new ArrayList<>(personalizedList));
            
            // Add offline fallback content after delay
            createOfflineFallbackSections();
        } else {
            // Try explore feed as fallback
            loadExploreFallback();
        }
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
                
                // Final fallback - show empty message
                List<HomeSectionResult> fallback = new ArrayList<>();
                fallback.add(new HomeSectionResult("Offline Mode", new ArrayList<>()));
                homeSections.setValue(fallback);
            }
        });
    }
    
    /**
     * Create offline fallback sections when no internet is available.
     */
    private void createOfflineFallbackSections() {
        mainHandler.postDelayed(() -> {
            List<HomeSectionResult> personalizedList = personalizedSections.getValue();
            if (personalizedList != null && !personalizedList.isEmpty()) {
                // Add some context sections
                List<HomeSectionResult> fallbackSections = new ArrayList<>();
                
                // Create a combined library section from all personalized content
                List<YTItemResult> allLibraryItems = new ArrayList<>();
                for (HomeSectionResult section : personalizedList) {
                    allLibraryItems.addAll(section.getItems());
                }
                
                // Limit to avoid duplicates and improve performance
                if (allLibraryItems.size() > 15) {
                    allLibraryItems = allLibraryItems.subList(0, 15);
                }
                
                if (!allLibraryItems.isEmpty()) {
                    fallbackSections.add(new HomeSectionResult("Your Library", allLibraryItems));
                }
                
                // Add info section
                fallbackSections.add(new HomeSectionResult("Offline Mode - Connect to internet for more content", new ArrayList<>()));
                
                // Append to current sections
                List<HomeSectionResult> current = homeSections.getValue();
                if (current != null) {
                    List<HomeSectionResult> updated = new ArrayList<>(current);
                    updated.addAll(fallbackSections);
                    homeSections.setValue(updated);
                    
                    // Set offline continuation token
                    continuationToken = "offline_mode";
                }
            }
        }, 1500); // Give time for personalized sections to load
    }
    
    private void executePagination() {
        if (!canLoadMore()) {
            return;
        }
        
        // Handle offline mode
        if ("offline_mode".equals(continuationToken)) {
            Log.d(TAG, "Offline mode - no more content available");
            continuationToken = null; // Stop further pagination
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
        Log.d(TAG, "===== EXTRACTING QUICK PICKS FROM HOME FEED =====");
        Log.d(TAG, "Scanning " + sections.size() + " sections");
        
        List<YTItemResult> extractedPicks = new ArrayList<>();
        Set<String> addedIds = new HashSet<>();
        
        // Collect from ALL matching sections, not just the first one
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
                    Log.d(TAG, "Found potential Quick Picks section: '" + title + "' with " + items.size() + " items");
                    
                    // Filter for songs only and avoid duplicates
                    int addedFromThisSection = 0;
                    for (YTItemResult item : items) {
                        String type = item.getType();
                        if (type != null && type.equalsIgnoreCase("SONG") && addedIds.add(item.getId())) {
                            extractedPicks.add(item);
                            addedFromThisSection++;
                            if (extractedPicks.size() >= 100) break; // Match QuickPicksRepository limit
                        }
                    }
                    
                    Log.d(TAG, "  → Added " + addedFromThisSection + " unique songs from this section");
                }
            }
            
            if (extractedPicks.size() >= 100) {
                Log.d(TAG, "Reached 100 Quick Picks limit, stopping section scan");
                break;
            }
        }
        
        Log.d(TAG, "===== QUICK PICKS EXTRACTION COMPLETE: " + extractedPicks.size() + " songs =====");
        
        // Store full list for pagination
        allQuickPicks = new ArrayList<>(extractedPicks);
        quickPicksCurrentPage = 0;
        
        // Return only first page initially (20 items)
        int initialSize = Math.min(QUICK_PICKS_PAGE_SIZE, extractedPicks.size());
        List<YTItemResult> firstPage = extractedPicks.subList(0, initialSize);
        
        Log.d(TAG, "Returning initial Quick Picks page: " + firstPage.size() + " items (total available: " + allQuickPicks.size() + ")");
        
        // If we have very few picks, supplement with QuickPicksManager
        if (extractedPicks.size() < 20 && context != null) {
            Log.d(TAG, "Only " + extractedPicks.size() + " picks from home feed, calling QuickPicksManager for more...");
            generateLocalQuickPicks();
        }
        
        return firstPage;
    }
    
    /**
     * Generate Quick Picks using enhanced QuickPicksManager with cold start prevention.
     */
    private void generateLocalQuickPicks() {
        executorService.execute(() -> {
            QuickPicksManager manager = new QuickPicksManager(context);
            
            // Preload for future cold starts
            manager.preloadForColdStart();
            
            manager.generateQuickPicks(new QuickPicksManager.QuickPicksCallback() {
                @Override
                public void onQuickPicksGenerated(List<YTItemResult> picks, boolean personalized) {
                    // Update on main thread
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!picks.isEmpty()) {
                            Log.d(TAG, "Generated " + picks.size() + " quick picks (personalized: " + personalized + ")");
                            
                            // Store full list for pagination
                            allQuickPicks = new ArrayList<>(picks);
                            quickPicksCurrentPage = 0;
                            
                            // Return only first page initially
                            int initialSize = Math.min(QUICK_PICKS_PAGE_SIZE, picks.size());
                            List<YTItemResult> firstPage = picks.subList(0, initialSize);
                            
                            quickPicks.setValue(firstPage);
                            Log.d(TAG, "Set initial Quick Picks page: " + firstPage.size() + " items (total: " + allQuickPicks.size() + ")");
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Failed to generate local quick picks: " + error);
                }
            });
        });
    }
    
    /**
     * Set context for QuickPicksManager fallback.
     * Must be called before loading initial data.
     */
    public void setContext(@NonNull Context context) {
        this.context = context.getApplicationContext();
        if (personalizedHomeFeed == null) {
            personalizedHomeFeed = new PersonalizedHomeFeed(context);
            // Observe personalized sections from PersonalizedHomeFeed
            personalizedHomeFeed.getPersonalizedSections().observeForever(sections -> {
                personalizedSections.postValue(sections);
                Log.d(TAG, "Personalized sections updated: " + sections.size());
            });
        }
    }
    
    /**
     * Loads personalized recommendation sections based on user's listening history.
     */
    public void loadPersonalizedSections() {
        if (personalizedHomeFeed == null) {
            Log.w(TAG, "PersonalizedHomeFeed not initialized, call setContext() first");
            return;
        }
        
        Log.d(TAG, "Loading personalized recommendation sections...");
        executorService.execute(() -> {
            try {
                personalizedHomeFeed.generatePersonalizedFeed();
                mainHandler.post(() -> {
                    Log.d(TAG, "Started generating personalized feed");
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading personalized sections", e);
                mainHandler.post(() -> {
                    personalizedSections.setValue(new ArrayList<>());
                });
            }
        });
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
