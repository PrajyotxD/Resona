package music.resona;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.callbacks.StringCallback;
import music.resona.online.bridge.exceptions.BridgeException;

/**
 * Main Application class for Resona.
 * 
 * <p>Handles initialization of the InnertubeBridge and manages visitor data
 * with a fast-loading cached strategy for optimal performance.</p>
 * 
 * <p>Strategy:</p>
 * <ul>
 *   <li>Authenticated users: Use session cookie (instant)</li>
 *   <li>Anonymous users: Load cached visitor data (instant), refresh in background</li>
 * </ul>
 */
public class App extends Application {
    
    private static final String TAG = "App";
    private static final String PREFS_NAME = "resona_prefs";
    private static final String KEY_VISITOR_DATA = "visitor_data";
    private static final String KEY_AUTH_COOKIE = "auth_cookie";
    private static final String DEFAULT_LOCALE_COUNTRY = "US";
    private static final String DEFAULT_LOCALE_LANGUAGE = "en-US";
    private static final int VISITOR_DATA_LOG_LENGTH = 10;
    
    private static App instance;
    private static volatile boolean visitorDataReady = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        Log.d(TAG, "App initializing...");
        
        initializeInnertubeBridge();
        loadSavedCookie();
        loadCachedVisitorData();
        
        // Set useLoginForBrowse based on authentication status
        if (isUserAuthenticated()) {
            InnertubeBridge.setUseLoginForBrowse(true);
            Log.d(TAG, "✓ Authenticated - using login for browse");
        } else {
            InnertubeBridge.setUseLoginForBrowse(false);
            Log.d(TAG, "Anonymous - using visitor data for browse");
        }
        
        Log.d(TAG, "App initialization complete");
    }
    
    /**
     * Initializes the InnertubeBridge with default locale settings.
     */
    private void initializeInnertubeBridge() {
        InnertubeBridge.initialize(DEFAULT_LOCALE_COUNTRY, DEFAULT_LOCALE_LANGUAGE);
        Log.d(TAG, "InnertubeBridge initialized with locale: " + 
                   DEFAULT_LOCALE_COUNTRY + ", " + DEFAULT_LOCALE_LANGUAGE);
    }
    
    /**
     * Loads saved authentication cookie from SharedPreferences.
     */
    private void loadSavedCookie() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedCookie = prefs.getString(KEY_AUTH_COOKIE, null);
        
        if (savedCookie != null && !savedCookie.isEmpty()) {
            InnertubeBridge.setCookieSync(savedCookie);
            Log.d(TAG, "✓ Loaded saved authentication cookie");
        } else {
            Log.d(TAG, "No saved cookie found");
        }
    }
    
    /**
     * Saves authentication cookie to SharedPreferences.
     * Call this after successful authentication.
     */
    public void saveAuthCookie(@NonNull String cookie) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_AUTH_COOKIE, cookie).apply();
        InnertubeBridge.setCookieSync(cookie);
        Log.d(TAG, "✓ Authentication cookie saved");
    }
    
    /**
     * Clears saved authentication cookie.
     */
    public void clearAuthCookie() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().remove(KEY_AUTH_COOKIE).apply();
        InnertubeBridge.setCookieSync(null);
        Log.d(TAG, "✓ Authentication cookie cleared");
    }
    
    /**
     * Checks if the user is authenticated.
     * 
     * @return true if user has valid authentication
     */
    public boolean isUserAuthenticated() {
        boolean authenticated = InnertubeBridge.isAuthenticatedSync();
        Log.d(TAG, "User authenticated: " + authenticated);
        return authenticated;
    }
    
    /**
     * Loads cached visitor data from SharedPreferences.
     * Visitor data is always loaded for both authenticated and anonymous users.
     */
    private void loadCachedVisitorData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String cachedData = prefs.getString(KEY_VISITOR_DATA, null);
        
        if (cachedData != null) {
            logVisitorData("Using cached visitor data", cachedData);
            InnertubeBridge.setVisitorDataSync(cachedData);
            visitorDataReady = true;
            Log.d(TAG, "✓ Visitor data loaded from cache (instant)");
        } else {
            Log.d(TAG, "No cached visitor data, fetching new visitor data asynchronously...");
            fetchVisitorDataAsync(prefs);
        }
    }
    
    /**
     * Asynchronously fetches new visitor data with automatic retry on failure.
     * Caches the fetched data and marks visitor data as ready.
     * 
     * @param prefs SharedPreferences for caching visitor data
     */
    private void fetchVisitorDataAsync(@NonNull final SharedPreferences prefs) {
        InnertubeBridge.fetchVisitorDataAsync(new StringCallback() {
            @Override
            public void onSuccess(@NonNull String visitorData) {
                logVisitorData("✓ Fresh visitor data fetched and cached", visitorData);
                prefs.edit().putString(KEY_VISITOR_DATA, visitorData).apply();
                visitorDataReady = true;
                Log.d(TAG, "✓ Visitor data initialization complete");
            }
            
            @Override
            public void onError(@NonNull BridgeException exception) {
                Log.e(TAG, "Failed to fetch visitor data: " + exception.getMessage());
                
                // Retry once after a short delay
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "Retrying visitor data fetch...");
                        InnertubeBridge.fetchVisitorDataAsync(new StringCallback() {
                            @Override
                            public void onSuccess(@NonNull String visitorData) {
                                logVisitorData("✓ Fresh visitor data fetched and cached (retry)", visitorData);
                                prefs.edit().putString(KEY_VISITOR_DATA, visitorData).apply();
                                visitorDataReady = true;
                                Log.d(TAG, "✓ Visitor data initialization complete (retry)");
                            }
                            
                            @Override
                            public void onError(@NonNull BridgeException retryException) {
                                Log.e(TAG, "✗ Failed to fetch visitor data after retry: " + retryException.getMessage());
                                visitorDataReady = false;
                            }
                        });
                    }
                }, 2000); // Retry after 2 seconds
            }
        });
    }
    
    /**
     * Logs visitor data with truncation for security.
     * 
     * @param message the log message prefix
     * @param visitorData the visitor data to log
     */
    private void logVisitorData(@NonNull String message, @Nullable String visitorData) {
        if (visitorData != null && visitorData.length() > VISITOR_DATA_LOG_LENGTH) {
            Log.d(TAG, message + ": " + visitorData.substring(0, VISITOR_DATA_LOG_LENGTH) + "...");
        } else {
            Log.d(TAG, message + ": " + visitorData);
        }
    }
    
    /**
     * Returns the singleton Application instance.
     * 
     * @return the App instance
     */
    @NonNull
    public static App getInstance() {
        return instance;
    }
    
    /**
     * Checks if visitor data has been initialized and is ready for use.
     * 
     * @return true if visitor data is ready
     */
    public static boolean isVisitorDataReady() {
        return visitorDataReady;
    }
    
    /**
     * Returns the current visitor data status for debugging.
     * 
     * @return a formatted status string
     */
    @NonNull
    public static String getVisitorDataStatus() {
        String visitorData = InnertubeBridge.getVisitorDataSync();
        String dataPreview = visitorData != null 
            ? visitorData.substring(0, Math.min(VISITOR_DATA_LOG_LENGTH, visitorData.length())) + "..." 
            : "null";
        return "Ready: " + visitorDataReady + ", Data: " + dataPreview;
    }
}
