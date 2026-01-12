package music.resona;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import music.resona.adapters.HomeSectionAdapter;
import music.resona.utils.UiUXUtil;
import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.callbacks.HomePageCallback;
import music.resona.online.bridge.exceptions.BridgeException;
import music.resona.online.bridge.models.HomePageResult;
import music.resona.online.bridge.models.HomeSectionResult;

/**
 * MainActivity - YouTube Music Home Feed Display
 * 
 * This activity demonstrates how to:
 * 1. Call YouTube.home() via InnertubeBridge
 * 2. Display home feed sections in RecyclerView
 * 3. Handle continuation tokens for pagination (load more)
 * 4. Manage data loading states and errors
 */
public class DemoActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity"; // For logging debug information

    // UI Components
    private androidx.core.widget.NestedScrollView nestedScrollView;
    private LinearLayout linear3;
    private RecyclerView recyclerview1; // Main RecyclerView for displaying sections
    private ImageView imageview1;
    private TextView textview1;
    private LinearLayout fav;
    private LinearLayout search;
    private ImageView imageview2;
    private ImageView imageview3;

    // Data & Adapter
    private HomeSectionAdapter homeSectionAdapter; // Adapter to manage RecyclerView data
    private List<HomeSectionResult> homeSections = new ArrayList<>(); // Stores all home sections
    
    // For pagination (loading more content)
    private String continuationToken = null; // Token to load next page of content
    private boolean isLoadingMore = false; // Flag to prevent duplicate load requests

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Step 1: Initialize all views
        initializeView(savedInstanceState);
        
        // Step 2: Handle system insets (status bar padding)
        ViewCompat.setOnApplyWindowInsetsListener(nestedScrollView, (v, insets) -> {
            Insets status = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    status.top,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return insets;
        });
        
        // Step 3: Initialize UI styling
        IntializeLoginScreen();
        
        // Step 4: Initialize the bridge (required before any API calls)
        // This also fetches visitor data and triggers loadHomeData() when ready
        initializeBridge();
        
        // Step 5: Setup RecyclerView
        setupRecyclerView();
        
        // Step 6: loadHomeData() is called from initializeBridge() after visitor data is ready
    }

    /**
     * Initialize all view components by finding them in the layout
     */
    private void initializeView(Bundle _savedInstanceState) {
        nestedScrollView = findViewById(R.id.nestedScrollView);
        linear3 = findViewById(R.id.linear3);
        recyclerview1 = findViewById(R.id.recyclerview1);
        imageview1 = findViewById(R.id.imageview1);
        textview1 = findViewById(R.id.textview1);
        fav = findViewById(R.id.fav);
        search = findViewById(R.id.search);
        imageview2 = findViewById(R.id.imageview2);
        imageview3 = findViewById(R.id.imageview3);
    }

    /**
     * Apply UI styling and configurations
     */
    private void IntializeLoginScreen(){
        UiUXUtil.ImmersiveHome(this);
        UiUXUtil.imageradius(imageview1, 360);
        UiUXUtil.typeface(getApplicationContext(), textview1, "akatski.ttf", 0);
        UiUXUtil.radiusGrad(fav , 350, UiUXUtil.accent, UiUXUtil.accent, 0, UiUXUtil.stroke);
        UiUXUtil.radiusGrad(search , 350, UiUXUtil.accent, UiUXUtil.accent, 0, UiUXUtil.stroke);
    }

    /**
     * Initialize the InnertubeBridge with locale settings
     * MUST be called before any bridge API calls
     * 
     * NOTE: App.java now handles visitor data initialization on app startup
     * (following Metrolist's approach). We just wait for it to be ready here.
     */
    private void initializeBridge() {
        Log.d(TAG, "Checking visitor data status...");
        
        // Check if visitor data is ready (set by App.java on startup)
        if (App.isVisitorDataReady()) {
            Log.d(TAG, "Visitor data already ready: " + App.getVisitorDataStatus());
            loadHomeData();
        } else {
            // Wait for visitor data to be ready
            Log.d(TAG, "Waiting for visitor data...");
            
            // Poll until ready (visitor data is being fetched by App.java)
            new Thread(() -> {
                final int[] attemptCounter = {0};
                while (!App.isVisitorDataReady() && attemptCounter[0] < 50) {
                    try {
                        Thread.sleep(100); // Wait 100ms
                        attemptCounter[0]++;
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                
                final int totalAttempts = attemptCounter[0];
                runOnUiThread(() -> {
                    if (App.isVisitorDataReady()) {
                        Log.d(TAG, "Visitor data ready after " + (totalAttempts * 100) + "ms");
                        loadHomeData();
                    } else {
                        Log.e(TAG, "Timeout waiting for visitor data - trying anyway");
                        loadHomeData();
                    }
                });
            }).start();
        }
    }

    /**
     * Setup RecyclerView with adapter and scroll listener
     */
    private void setupRecyclerView() {
        // Create adapter with empty data initially
        homeSectionAdapter = new HomeSectionAdapter(this, homeSections);
        
        // Set vertical LinearLayoutManager (sections stacked vertically)
        recyclerview1.setLayoutManager(new LinearLayoutManager(this));
        
        // Attach adapter to RecyclerView
        recyclerview1.setAdapter(homeSectionAdapter);
        
        // Add scroll listener for infinite scroll (load more when reaching bottom)
        recyclerview1.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // Check if user scrolled down (dy > 0)
                if (dy > 0) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    
                    // Get the position of the last visible item
                    int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                    
                    // Get total number of items in adapter
                    int totalItemCount = layoutManager.getItemCount();
                    
                    // If we're near the end (last 3 items) and have a continuation token
                    // and not already loading, then load more content
                    if (lastVisibleItem >= totalItemCount - 3 && 
                        continuationToken != null && 
                        !isLoadingMore) {
                        loadMoreHomeData();
                    }
                }
            }
        });
        
        Log.d(TAG, "RecyclerView setup complete");
    }

    /**
     * Load initial home page data from YouTube
     * Uses ASYNC method for smooth UI experience
     * 
     * NOTE: Following Metrolist's approach - YouTube.home() endpoint works for 
     * anonymous users without explicit authentication. If it fails, we fallback to explore.
     */
    private void loadHomeData() {
        Log.d(TAG, "Loading home feed data...");
        
        // Verify visitor data is set before making API call
        String currentVisitorData = InnertubeBridge.getVisitorData();
        Log.d(TAG, "Current visitor data in bridge: " + 
            (currentVisitorData != null ? currentVisitorData.substring(0, Math.min(15, currentVisitorData.length())) + "..." : "NULL"));
        
        // Call InnertubeBridge.getHomeAsync() to fetch YouTube home feed
        // This method runs on a background thread and returns results via callback
        // Works for both authenticated and anonymous users
        InnertubeBridge.getHomeAsync(new HomePageCallback() {
            
            /**
             * Called when data is successfully loaded
             * This callback runs on the MAIN THREAD (safe to update UI)
             * 
             * @param result HomePageResult containing sections and continuation token
             */
            @Override
            public void onSuccess(HomePageResult result) {
                // Log success for debugging
                Log.d(TAG, "Home data loaded successfully");
                Log.d(TAG, "Sections received: " + result.getSections().size());
                
                // Clear any existing data (fresh start)
                homeSections.clear();
                
                // Add all sections from the result to our list
                // result.getSections() returns List<HomeSectionResult>
                // Each section has a title and list of items (songs, albums, etc.)
                homeSections.addAll(result.getSections());
                
                // Store continuation token for loading more data later
                // If null, it means there's no more content to load
                continuationToken = result.getContinuation();
                Log.d(TAG, "Continuation token: " + (continuationToken != null ? "Available" : "None"));
                
                // Notify adapter that data has changed
                // This triggers RecyclerView to refresh and display the data
                homeSectionAdapter.notifyDataSetChanged();
                
                // Show success message to user
                Toast.makeText(DemoActivity.this,
                    "Loaded " + homeSections.size() + " sections", 
                    Toast.LENGTH_SHORT).show();
                
                // Auto-load more sections if we have continuation and not enough content yet
                // This ensures there's enough content to scroll (prevents single-section issue)
                if (continuationToken != null && homeSections.size() < 5) {
                    Log.d(TAG, "Auto-loading more sections to fill screen...");
                    loadMoreHomeData();
                }
            }

            /**
             * Called when an error occurs during data loading
             * This callback runs on the MAIN THREAD
             * 
             * @param error BridgeException containing error details
             */
            @Override
            public void onError(BridgeException error) {
                // Log the error for debugging
                Log.e(TAG, "Error loading home data: " + error.getMessage(), error);
                
                // Show error message to user
                Toast.makeText(DemoActivity.this,
                    "Home feed failed, trying explore...", 
                    Toast.LENGTH_SHORT).show();
                
                // Fallback to explore endpoint
                Log.d(TAG, "Trying explore endpoint as fallback...");
                loadExploreData();
            }
        });
    }
    
    /**
     * Load explore page data from YouTube (fallback for home)
     * Explore endpoint is more lenient and works better without authentication
     */
    private void loadExploreData() {
        Log.d(TAG, "Loading explore feed data...");
        
        InnertubeBridge.getExploreAsync(new HomePageCallback() {
            @Override
            public void onSuccess(HomePageResult result) {
                Log.d(TAG, "Explore data loaded successfully");
                Log.d(TAG, "Sections received: " + result.getSections().size());
                
                homeSections.clear();
                homeSections.addAll(result.getSections());
                continuationToken = null; // Explore doesn't have continuation
                homeSectionAdapter.notifyDataSetChanged();
                
                Toast.makeText(DemoActivity.this,
                    "Loaded " + homeSections.size() + " sections (explore)", 
                    Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onError(BridgeException error) {
                Log.e(TAG, "Error loading explore data: " + error.getMessage(), error);
                
                Toast.makeText(DemoActivity.this,
                    "Failed to load content: " + error.getMessage(), 
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Load more home page data using continuation token
     * Called when user scrolls near the bottom
     */
    private void loadMoreHomeData() {
        // Check if continuation token exists
        if (continuationToken == null) {
            Log.d(TAG, "No continuation token - no more data to load");
            return;
        }
        
        // Set loading flag to prevent duplicate requests
        isLoadingMore = true;
        Log.d(TAG, "Loading more home data with continuation token...");
        
        // Call InnertubeBridge.getHomeContinuationSync() in background thread
        // Since there's no async version, we run sync method in background
        // Note: getHomeContinuationSync() blocks, so MUST run on background thread
        new Thread(() -> {
            try {
                // Call sync method (this blocks until data is loaded)
                // This uses the continuation token from the previous response
                HomePageResult result = InnertubeBridge.getHomeContinuationSync(continuationToken);
                
                // Successfully loaded more data
                // Now switch to MAIN thread to update UI
                runOnUiThread(() -> {
                    Log.d(TAG, "More data loaded successfully");
                    Log.d(TAG, "Additional sections: " + result.getSections().size());
                    
                    // Get current size before adding new data
                    int oldSize = homeSections.size();
                    
                    // Add new sections to existing list (append, don't replace)
                    homeSections.addAll(result.getSections());
                    
                    // Update continuation token for next load
                    continuationToken = result.getContinuation();
                    
                    // Notify adapter about new items added
                    // notifyItemRangeInserted is more efficient than notifyDataSetChanged
                    homeSectionAdapter.notifyItemRangeInserted(oldSize, result.getSections().size());
                    
                    // Reset loading flag
                    isLoadingMore = false;
                    
                    Log.d(TAG, "Total sections now: " + homeSections.size());
                    
                    // Keep auto-loading if still not enough content (less than 5 sections)
                    if (continuationToken != null && homeSections.size() < 5) {
                        Log.d(TAG, "Auto-loading more sections to fill screen...");
                        loadMoreHomeData();
                    }
                });
                
            } catch (Exception error) {
                // Error loading more data
                // Switch to MAIN thread to show error
                runOnUiThread(() -> {
                    Log.e(TAG, "Error loading more data: " + error.getMessage(), error);
                    
                    // Reset loading flag so user can try again
                    isLoadingMore = false;
                    
                    Toast.makeText(DemoActivity.this,
                        "Error loading more: " + error.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start(); // Start the background thread
    }
}
