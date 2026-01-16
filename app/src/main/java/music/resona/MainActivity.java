package music.resona;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import music.resona.fragment.SearchFragment;
import xyz.code.navigationbar.NavigationBar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import music.resona.activity.AuthActivity;
import music.resona.activity.FullScreenPlayerActivity;
import music.resona.app.App;
import music.resona.fragment.HomeFeed;
import music.resona.manager.MusicPlaybackManager;
import music.resona.models.Song;
import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.models.AccountInfoResult;
import music.resona.service.MusicService;
import music.resona.ui.NavBarContainer;
import music.resona.ui.ResonaMiniPlayer;
import music.resona.utils.UiUXUtil;
import music.resona.viewmodel.AccountInfoViewModel;

/**
 * Main activity hosting the primary navigation and fragments.
 * 
 * <p>Manages bottom navigation, mini player, and fragment transactions for the main app screens.</p>
 */
public class MainActivity extends AppCompatActivity implements MusicPlaybackManager.PlaybackListener {
    
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "main_activity_prefs";
    private static final String KEY_CACHED_USERNAME = "cached_username";
    private static final String KEY_CACHED_THUMBNAIL = "cached_thumbnail";
    
    private NavBarContainer navBarContainer;
    private NavigationBar bottomNavigation;
    private ResonaMiniPlayer miniPlayer;
    private MusicPlaybackManager playbackManager;
    private AccountInfoViewModel accountInfoViewModel;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    // Fragment instances - cached for state retention
    private HomeFeed homeFeedFragment;
    private SearchFragment searchFragment;
    private Fragment currentFragment;
    
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
        
        // Enable shared element transitions
        getWindow().requestFeature(android.view.Window.FEATURE_CONTENT_TRANSITIONS);
        
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main2);
        
        setupWindowInsets();
        setupNavBarContainer();
        accountInfoViewModel = new ViewModelProvider(this).get(AccountInfoViewModel.class);
        updateAuthenticationState();
        
        // Initialize playback manager
        playbackManager = MusicPlaybackManager.getInstance(this);
        playbackManager.addListener(this);
        
        if (savedInstanceState == null) {
            // Initialize cached fragments
            homeFeedFragment = HomeFeed.newInstance();
            searchFragment = new SearchFragment();
            
            // Load home feed as initial fragment
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment_container, homeFeedFragment, "HOME")
                    .commit();
            currentFragment = homeFeedFragment;
        } else {
            // Restore cached fragments after configuration change
            homeFeedFragment = (HomeFeed) getSupportFragmentManager().findFragmentByTag("HOME");
            searchFragment = (SearchFragment) getSupportFragmentManager().findFragmentByTag("SEARCH");
            currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        }
        
        // Restore mini player state if song is playing
        if (playbackManager.hasSong()) {
            showMiniPlayer(playbackManager.getCurrentSong());
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playbackManager != null) {
            playbackManager.removeListener(this);
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
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
            
            // Update bottom margin for NavBarContainer to account for system bars
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && navBarContainer != null) {
                android.widget.FrameLayout.LayoutParams params = 
                    (android.widget.FrameLayout.LayoutParams) navBarContainer.getLayoutParams();
                params.bottomMargin = systemBars.bottom + dpToPx(16);
                navBarContainer.setLayoutParams(params);
            }
            
            return insets;
        });
    }
    
    /**
     * Sets up the NavBarContainer with MiniPlayer and NavigationBar.
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    private void setupNavBarContainer() {
        navBarContainer = findViewById(R.id.nav_bar_container);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Create and configure NavigationBar
            bottomNavigation = new NavigationBar(this);
            bottomNavigation.setNavBarBackgroundColor(0x00000000); // Transparent - container handles blur
            bottomNavigation.setActiveBackgroundColor(0xFFFFFFFF); // White indicator
            bottomNavigation.setInactiveIconColor(0xFFFFFFFF); // White icons
            bottomNavigation.setActiveIconColor(0xFF000000); // Black active icon
            bottomNavigation.setCornerRadius(25f); // Pill shape
            bottomNavigation.setBlurEnabled(false); // Container handles blur
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
                if(position==0){
                    switchToFragment(homeFeedFragment, "HOME");
                } else if (position==1) {
                    switchToFragment(searchFragment, "SEARCH");
                }
            });
            
            // Set initial tab
            bottomNavigation.setActiveTab(0);
            
            // Create and configure MiniPlayer
            miniPlayer = new ResonaMiniPlayer(this);
            miniPlayer.setOnMiniPlayerListener(new ResonaMiniPlayer.OnMiniPlayerListener() {
                @Override
                public void onPlayPause() {
                    if (playbackManager != null) {
                        playbackManager.togglePlayPause();
                    }
                }
                
                @Override
                public void onNext() {
                    if (playbackManager != null) {
                        playbackManager.next();
                    }
                }
                
                @Override
                public void onMiniPlayerClick() {
                    openFullScreenPlayer();
                }
            });
            
            // Configure container
            navBarContainer.setNavBarBackgroundColor(0x80252525);
            navBarContainer.setCornerRadius(28f);
            navBarContainer.setMiniPlayer(miniPlayer);
            navBarContainer.setNavigationBar(bottomNavigation);
        }
    }
    
    /**
     * Shows the mini player with song info.
     */
    public void showMiniPlayer(@Nullable Song song) {
        if (navBarContainer != null && miniPlayer != null) {
            miniPlayer.setSong(song);
            if (!navBarContainer.isMiniPlayerVisible()) {
                navBarContainer.showMiniPlayer();
            }
        }
    }
    
    /**
     * Hides the mini player.
     */
    public void hideMiniPlayer() {
        if (navBarContainer != null) {
            navBarContainer.hideMiniPlayer();
        }
    }
    
    /**
     * Opens the full screen player activity.
     */
    private void openFullScreenPlayer() {
        Intent intent = new Intent(this, FullScreenPlayerActivity.class);
        startActivity(intent);
        overridePendingTransition(0, 0); // Custom animation handled in activity
    }
    
    /**
     * Plays a song and shows the mini player.
     * Called from adapters when user clicks on a song.
     */
    public void playSong(@NonNull Song song) {
        if (playbackManager != null) {
            showMiniPlayer(song);
            miniPlayer.setLoading(true);
            
            playbackManager.playSong(song, new MusicPlaybackManager.PlaybackCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        miniPlayer.setLoading(false);
                        miniPlayer.setPlaying(true);
                    });
                }
                
                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        miniPlayer.setLoading(false);
                        android.widget.Toast.makeText(MainActivity.this, 
                            "Error: " + message, android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }
    
    /**
     * Returns the playback manager for fragments to use.
     */
    public MusicPlaybackManager getPlaybackManager() {
        return playbackManager;
    }
    
    // PlaybackListener implementation
    
    @Override
    public void onSongChanged(Song song) {
        runOnUiThread(() -> {
            if (miniPlayer != null) {
                miniPlayer.setSong(song);
                if (!navBarContainer.isMiniPlayerVisible()) {
                    showMiniPlayer(song);
                }
            }
        });
    }
    
    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        runOnUiThread(() -> {
            if (miniPlayer != null) {
                miniPlayer.setPlaying(isPlaying);
            }
        });
    }
    
    @Override
    public void onProgressChanged(int currentMs, int durationMs) {
        runOnUiThread(() -> {
            if (miniPlayer != null && durationMs > 0) {
                float progress = (float) currentMs / durationMs;
                miniPlayer.setProgress(progress);
            }
        });
    }
    
    @Override
    public void onShuffleChanged(boolean shuffle) {
        // Not used in mini player
    }
    
    @Override
    public void onRepeatModeChanged(MusicService.RepeatMode mode) {
        // Not used in mini player
    }
    
    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onLoadingStateChanged(boolean isLoading) {
        runOnUiThread(() -> {
            if (miniPlayer != null) {
                miniPlayer.setLoading(isLoading);
            }
        });
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
                return new  SearchFragment();
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
     * Switches to the specified fragment using show/hide to preserve state.
     * 
     * @param fragment the fragment to switch to
     * @param tag the fragment tag
     */
    private void switchToFragment(@NonNull Fragment fragment, @NonNull String tag) {
        if (currentFragment == fragment) {
            return; // Already showing this fragment
        }
        
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        
        // Hide current fragment if exists
        if (currentFragment != null) {
            transaction.hide(currentFragment);
        }
        
        // Show or add the target fragment
        if (fragment.isAdded()) {
            transaction.show(fragment);
        } else {
            transaction.add(R.id.fragment_container, fragment, tag);
        }
        
        transaction.commit();
        currentFragment = fragment;
    }
}