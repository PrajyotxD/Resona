package music.resona;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    
    private BottomNavigationView bottomNavigation;
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
            
            bottomNavigation = findViewById(R.id.bottom_navigation);
            bottomNavigation.setPadding(0, 0, 0, systemBars.bottom);
            
            return insets;
        });
    }
    
    /**
     * Configures bottom navigation with item selection handling.
     */
    private void setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottom_navigation);
        bottomNavigation.setOnItemSelectedListener(this::handleNavigationItemSelected);
    }
    
    /**
     * Handles bottom navigation item selection.
     * 
     * @param item the selected menu item
     * @return true if the item was handled successfully
     */
    private boolean handleNavigationItemSelected(@NonNull android.view.MenuItem item) {
        Fragment fragment = getFragmentForNavItem(item.getItemId());
        return fragment != null && loadFragment(fragment);
    }
    
    /**
     * Returns the appropriate fragment for the given navigation item ID.
     * 
     * @param itemId the navigation item ID
     * @return the corresponding fragment, or null if not found
     */
    private Fragment getFragmentForNavItem(int itemId) {
        if (itemId == R.id.nav_home) {
            return HomeFeed.newInstance();
        } else if (itemId == R.id.nav_explore) {
            // TODO: Create ExploreFragment
            Log.d(TAG, "Explore fragment not yet implemented");
            return HomeFeed.newInstance();
        } else if (itemId == R.id.nav_library) {
            // TODO: Create LibraryFragment
            Log.d(TAG, "Library fragment not yet implemented");
            return HomeFeed.newInstance();
        }
        return null;
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