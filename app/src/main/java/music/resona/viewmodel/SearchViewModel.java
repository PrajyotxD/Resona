package music.resona.viewmodel;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.callbacks.SearchCallback;
import music.resona.online.bridge.exceptions.BridgeException;
import music.resona.online.bridge.models.SearchResult;
import music.resona.online.bridge.models.YTItemResult;

/**
 * ViewModel for managing search state and operations.
 * Handles real-time search with debouncing, loading states, and error handling.
 */
public class SearchViewModel extends ViewModel {

    private static final long SEARCH_DEBOUNCE_DELAY = 300; // milliseconds

    private final MutableLiveData<List<YTItemResult>> searchResults = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> currentQuery = new MutableLiveData<>("");
    private final MutableLiveData<String> activeFilter = new MutableLiveData<>("songs");
    
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearchRunnable;
    @Nullable
    private String continuationToken;
    
    // Cache last search results for later retrieval
    private List<YTItemResult> cachedSearchResults = new ArrayList<>();

    @NonNull
    public LiveData<List<YTItemResult>> getSearchResults() {
        return searchResults;
    }

    @NonNull
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @NonNull
    public LiveData<String> getCurrentQuery() {
        return currentQuery;
    }

    @NonNull
    public LiveData<String> getActiveFilter() {
        return activeFilter;
    }

    /**
     * Performs a debounced search to avoid excessive API calls.
     * Cancels pending search if user is still typing.
     */
    public void searchWithDebounce(@NonNull String query) {
        // Cancel pending search
        if (pendingSearchRunnable != null) {
            searchHandler.removeCallbacks(pendingSearchRunnable);
        }

        // Update current query
        currentQuery.setValue(query);

        // If query is empty, clear results
        if (query.trim().isEmpty()) {
            searchResults.setValue(new ArrayList<>());
            continuationToken = null;
            return;
        }

        // Schedule new search after debounce delay
        pendingSearchRunnable = () -> performSearch(query, activeFilter.getValue());
        searchHandler.postDelayed(pendingSearchRunnable, SEARCH_DEBOUNCE_DELAY);
    }

    /**
     * Performs immediate search without debouncing.
     * Use this when user taps search button or filter.
     */
    public void searchImmediately(@NonNull String query) {
        currentQuery.setValue(query);
        if (query.trim().isEmpty()) {
            searchResults.setValue(new ArrayList<>());
            continuationToken = null;
            return;
        }
        performSearch(query, activeFilter.getValue());
    }

    /**
     * Changes the search filter (songs, albums, artists, playlists).
     */
    public void setFilter(@NonNull String filter) {
        activeFilter.setValue(filter);
        String query = currentQuery.getValue();
        if (query != null && !query.trim().isEmpty()) {
            performSearch(query, filter);
        }
    }

    /**
     * Loads more search results using continuation token.
     */
    public void loadMore() {
        if (continuationToken == null || Boolean.TRUE.equals(isLoading.getValue())) {
            return;
        }

        isLoading.setValue(true);
        InnertubeBridge.searchWithContinuationAsync(continuationToken, new SearchCallback() {
            @Override
            public void onSuccess(@NonNull SearchResult result) {
                List<YTItemResult> currentResults = searchResults.getValue();
                if (currentResults == null) {
                    currentResults = new ArrayList<>();
                }
                List<YTItemResult> updatedResults = new ArrayList<>(currentResults);
                updatedResults.addAll(result.getItems());
                searchResults.setValue(updatedResults);
                continuationToken = result.getContinuation();
                isLoading.setValue(false);
            }

            @Override
            public void onError(@NonNull BridgeException error) {
                errorMessage.setValue(error.getMessage());
                isLoading.setValue(false);
            }
        });
    }

    /**
     * Get cached search results for a specific video ID.
     * Used to retrieve artist info from search results.
     */
    @Nullable
    public YTItemResult getItemByVideoId(@NonNull String videoId) {
        for (YTItemResult item : cachedSearchResults) {
            if (videoId.equals(item.getId())) {
                return item;
            }
        }
        return null;
    }
    
    /**
     * Get all cached search results.
     */
    @NonNull
    public List<YTItemResult> getCachedResults() {
        return new ArrayList<>(cachedSearchResults);
    }
    
    /**
     * Clears search results and resets state.
     */
    public void clearSearch() {
        currentQuery.setValue("");
        searchResults.setValue(new ArrayList<>());
        errorMessage.setValue(null);
        continuationToken = null;
    }

    /**
     * Performs the actual search based on filter type.
     */
    private void performSearch(@NonNull String query, @Nullable String filter) {
        isLoading.setValue(true);
        errorMessage.setValue(null);
        continuationToken = null;

        SearchCallback callback = new SearchCallback() {
            @Override
            public void onSuccess(@NonNull SearchResult result) {
                List<YTItemResult> items = result.getItems();
                searchResults.setValue(new ArrayList<>(items));
                // Cache results for later retrieval
                cachedSearchResults = new ArrayList<>(items);
                continuationToken = result.getContinuation();
                isLoading.setValue(false);
            }

            @Override
            public void onError(@NonNull BridgeException error) {
                errorMessage.setValue(error.getMessage());
                isLoading.setValue(false);
            }
        };

        // Call appropriate search method based on filter
        if (filter == null || filter.equals("songs")) {
            InnertubeBridge.searchSongsAsync(query, callback);
        } else if (filter.equals("albums")) {
            InnertubeBridge.searchAlbumsAsync(query, callback);
        } else if (filter.equals("artists")) {
            InnertubeBridge.searchArtistsAsync(query, callback);
        } else if (filter.equals("playlists")) {
            InnertubeBridge.searchPlaylistsAsync(query, callback);
        } else {
            InnertubeBridge.searchAsync(query, callback);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Clean up pending searches
        if (pendingSearchRunnable != null) {
            searchHandler.removeCallbacks(pendingSearchRunnable);
        }
    }
}
