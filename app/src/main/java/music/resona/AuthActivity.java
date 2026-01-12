package music.resona;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import music.resona.online.bridge.InnertubeBridge;

/**
 * Activity for YouTube Music authentication via WebView.
 * 
 * <p>Handles OAuth flow and cookie extraction for authenticated API access.</p>
 */
public class AuthActivity extends AppCompatActivity {
    
    private static final String TAG = "AuthActivity";
    private static final String GOOGLE_LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https://music.youtube.com/&hl=en";
    private static final String YOUTUBE_MUSIC_URL = "https://music.youtube.com";
    private static final String COOKIE_DOMAIN = ".youtube.com";
    private static final String COOKIE_NAME_PATTERN = ".*";
    
    private WebView webView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        
        progressBar = findViewById(R.id.progress_bar);
        setupCloseButton();
        setupWebView();
        loadYouTubeMusic();
    }
    
    /**
     * Configures the close button.
     */
    private void setupCloseButton() {
        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
    }
    
    /**
     * Configures the WebView for authentication.
     */
    private void setupWebView() {
        webView = findViewById(R.id.webview_auth);
        
        // Enable necessary features
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setUserAgentString(getUserAgent());
        
        // Performance optimizations
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
        webView.getSettings().setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH);
        webView.getSettings().setEnableSmoothTransition(true);
        
        // Disable features not needed for authentication
        webView.getSettings().setSupportZoom(false);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(true);
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                Log.d(TAG, "Page loaded: " + url);
                
                if (url.contains("music.youtube.com")) {
                    checkAuthenticationCookies();
                }
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.e(TAG, "WebView error: " + description + " (" + errorCode + ")");
                progressBar.setVisibility(View.GONE);
            }
        });
    }
    
    /**
     * Loads Google Accounts login page for YouTube Music.
     */
    private void loadYouTubeMusic() {
        Log.d(TAG, "Loading Google Accounts authentication page");
        webView.loadUrl(GOOGLE_LOGIN_URL);
    }
    
    /**
     * Checks for authentication cookies and sets them in InnertubeBridge.
     */
    private void checkAuthenticationCookies() {
        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie(YOUTUBE_MUSIC_URL);
        
        if (cookies != null && !cookies.isEmpty()) {
            Log.d(TAG, "Cookies found: " + cookies.substring(0, Math.min(50, cookies.length())) + "...");
            
            // Check if we have essential authentication cookies
            if (hasAuthenticationCookies(cookies)) {
                saveAuthenticationSession(cookies);
            }
        }
    }
    
    /**
     * Checks if cookies contain authentication data.
     * 
     * @param cookies the cookie string
     * @return true if authentication cookies are present
     */
    private boolean hasAuthenticationCookies(@NonNull String cookies) {
        // YouTube authentication typically uses SAPISID, HSID, SSID, etc.
        return cookies.contains("SAPISID") || 
               cookies.contains("__Secure-3PAPISID") ||
               cookies.contains("LOGIN_INFO");
    }
    
    /**
     * Saves authentication session to InnertubeBridge and SharedPreferences.
     * 
     * @param cookies the authentication cookies
     */
    private void saveAuthenticationSession(@NonNull String cookies) {
        Log.d(TAG, "Saving authentication session");
        
        try {
            // Save cookie to SharedPreferences via App
            App.getInstance().saveAuthCookie(cookies);
            
            // Verify authentication
            if (InnertubeBridge.isAuthenticatedSync()) {
                Log.d(TAG, "✓ Authentication successful and saved!");
                Toast.makeText(this, "Successfully signed in to YouTube Music", Toast.LENGTH_SHORT).show();
                
                // Return success result
                setResult(Activity.RESULT_OK);
                finish();
            } else {
                Log.w(TAG, "Authentication verification failed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving authentication session", e);
            Toast.makeText(this, "Authentication error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Returns a Chrome-like user agent for better compatibility.
     * 
     * @return the user agent string
     */
    @NonNull
    private String getUserAgent() {
        return "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
               "Chrome/120.0.0.0 Mobile Safari/537.36";
    }
    
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }
    
    @Override
    protected void onDestroy() {
        // Properly clean up WebView to prevent memory leaks and crashes
        if (webView != null) {
            // Remove from parent view first
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            
            // Stop all loading
            webView.stopLoading();
            
            // Clear WebView
            webView.clearHistory();
            webView.clearCache(true);
            webView.loadUrl("about:blank");
            webView.onPause();
            webView.removeAllViews();
            
            // Destroy WebView
            webView.destroy();
            webView = null;
        }
        
        super.onDestroy();
    }
}
