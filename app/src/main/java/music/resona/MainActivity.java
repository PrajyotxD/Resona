package music.resona;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import xyz.code.navigationbar.NavigationBar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import music.resona.activity.AuthActivity;
import music.resona.app.App;
import music.resona.fragment.HomeFeed;
import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.models.AccountInfoResult;
import music.resona.utils.UiUXUtil;
import music.resona.viewmodel.AccountInfoViewModel;

/**
 * Main activity hosting the primary navigation and fragments.
 * 
 * <p>Manages bottom navigation and fragment transactions for the main app screens.</p>
 */
public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "main_activity_prefs";
    private static final String KEY_CACHED_USERNAME = "cached_username";
    private static final String KEY_CACHED_THUMBNAIL = "cached_thumbnail";
    
    private NavigationBar bottomNavigation;
    private AccountInfoViewModel accountInfoViewModel;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    private final ActivityResultLauncher<Intent> authLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Log.d(TAG, "Authentication successful");
                    updateAuthenticationState();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main2);
        
        setupWindowInsets();
        setupBottomNavigation();
        accountInfoViewModel = new ViewModelProvider(this).get(AccountInfoViewModel.class);
        updateAuthenticationState();
        
        if (savedInstanceState == null) {
            loadFragment(HomeFeed.newInstance());
        }
    }

    /**
     * Allows fragments to trigger the authentication flow via the activity.
     */
    public void requestAuthentication() {
        Intent intent = new Intent(this, AuthActivity.class);
        authLauncher.launch(intent);
    }
    
    /**
     * Updates UI based on current authentication state.
     * Fetches user account info and updates profile picture and name.
     * Uses cached data for instant display, then updates in background.
     */
    private void updateAuthenticationState() {
        App app = (App) getApplication();
        boolean isAuthenticated = app.isUserAuthenticated();
        
        Log.d(TAG, "User authenticated: " + isAuthenticated);
        
        if (isAuthenticated) {
            loadCachedAccountInfo();

            executorService.execute(() -> {
                try {
                    AccountInfoResult accountInfo = InnertubeBridge.getAccountDetailsSync();
                    cacheAccountInfo(accountInfo.name, accountInfo.thumbnailUrl);
                    pushAccountInfo(accountInfo.name, accountInfo.thumbnailUrl, true);
                    Log.d(TAG, "Account info updated: " + accountInfo.name);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to fetch account info", e);
                    pushAccountInfo("User", null, true);
                }
            });
        } else {
            pushAccountInfo("Guest", null, false);
        }
    }
    
    /**
     * Loads cached account info for instant display.
     */
    private void loadCachedAccountInfo() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String cachedName = prefs.getString(KEY_CACHED_USERNAME, null);
        String cachedThumbnail = prefs.getString(KEY_CACHED_THUMBNAIL, null);
        
        if (cachedName != null || cachedThumbnail != null) {
            pushAccountInfo(cachedName, cachedThumbnail, true);
        }
    }
    
    /**
     * Caches account info for instant display on next launch.
     */
    private void cacheAccountInfo(String name, String thumbnailUrl) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_CACHED_USERNAME, name)
                .putString(KEY_CACHED_THUMBNAIL, thumbnailUrl)
                .apply();
    }

    private void pushAccountInfo(String name, String thumbnailUrl, boolean authenticated) {
        if (accountInfoViewModel != null) {
            accountInfoViewModel.updateAccountInfo(name, thumbnailUrl, authenticated);
        }
    }
    
    /**
     * Configures window insets for edge-to-edge display.
     */
    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            UiUXUtil.TStatusBar(this);
            
            // Update bottom margin for NavigationBar to account for system bars
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bottomNavigation = findViewById(R.id.bottom_navigation);
                android.widget.FrameLayout.LayoutParams params = 
                    (android.widget.FrameLayout.LayoutParams) bottomNavigation.getLayoutParams();
                params.bottomMargin = systemBars.bottom + dpToPx(16);
                bottomNavigation.setLayoutParams(params);
            }
            
            return insets;
        });
    }
    
    /**
     * Configures bottom navigation with custom NavigationBar.
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    private void setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottom_navigation);
        
        // Configure styling to match app theme
        bottomNavigation.setNavBarBackgroundColor(0x80252525); // Dark semi-transparent
        bottomNavigation.setActiveBackgroundColor(0xFFFFFFFF); // White indicator
        bottomNavigation.setInactiveIconColor(0xFFFFFFFF); // White icons
        bottomNavigation.setActiveIconColor(0xFF000000); // Black active icon
        bottomNavigation.setCornerRadius(100f); // Pill shape
        bottomNavigation.setBlurEnabled(true);
        bottomNavigation.setAnimationCurve(NavigationBar.AnimationCurve.OVERSHOOT);
        bottomNavigation.setAnimationDuration(400);
        
        // Add tabs with custom icons
        bottomNavigation
            .addTab(R.drawable.ic_home, "Home")
            .addTab(R.drawable.ic_search, "Search")
            .addTab(R.drawable.ic_library, "Library")
            .addTab(R.drawable.ic_settings, "Settings")
            .addTab(R.drawable.ic_plugin, "Plugin");
        
        // Set selection listener
        bottomNavigation.setOnTabSelectedListener((position, tab) -> {
            Fragment fragment = getFragmentForNavPosition(position);
            if (fragment != null) {
                loadFragment(fragment);
            }
        });
        
        // Set initial tab
        bottomNavigation.setActiveTab(0);
    }
    
    /**
     * Returns the appropriate fragment for the given navigation position.
     * 
     * @param position the navigation tab position
     * @return the corresponding fragment, or null if not found
     */
    private Fragment getFragmentForNavPosition(int position) {
        switch (position) {
            case 0: // Home
                return HomeFeed.newInstance();
            case 1: // Search
                Log.d(TAG, "Search fragment not yet implemented");
                return HomeFeed.newInstance();
            case 2: // Library
                Log.d(TAG, "Library fragment not yet implemented");
                return HomeFeed.newInstance();
            case 3: // Settings
                Log.d(TAG, "Settings fragment not yet implemented");
                return HomeFeed.newInstance();
            case 4: // Plugin
                Log.d(TAG, "Plugin fragment not yet implemented");
                return HomeFeed.newInstance();
            default:
                return null;
        }
    }
    
    /**
     * Converts dp to pixels.
     */
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
    
    /**
     * Loads the specified fragment into the fragment container.
     * 
     * @param fragment the fragment to load
     * @return true if the fragment was loaded successfully
     */
    private boolean loadFragment(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        return true;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}