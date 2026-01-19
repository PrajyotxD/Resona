package music.resona.activity.player;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import music.resona.R;
import music.resona.manager.MusicPlaybackManager;
import music.resona.models.Song;
import music.resona.service.MusicService;
import music.resona.utils.PlayerHelper;

/**
 * PlayerHybrid - WebView-based music player with native integration
 * 
 * This activity uses a custom HTML/CSS/JS UI (player-ui) with native Android
 * playback functionality through the PlayerHelper JavaScript bridge.
 * 
 * Features:
 * - Modern WebView-based UI
 * - Full ExoPlayer2 integration via PlayerHelper
 * - Real-time state synchronization
 * - Lyrics display
 * - Queue management
 */
public class PlayerHybrid extends AppCompatActivity implements MusicPlaybackManager.PlaybackListener {
    
    private static final String TAG = "PlayerHybrid";
    
    private WebView webView;
    private PlayerHelper playerHelper;
    private MusicPlaybackManager playbackManager;
    private Handler updateHandler;
    private Runnable updateRunnable;
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_player_hybrid);
        
        // Setup window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Initialize playback manager
        playbackManager = MusicPlaybackManager.getInstance(this);
        playbackManager.addListener(this);
        
        // Setup WebView
        setupWebView();
        
        // Initialize PlayerHelper bridge
        playerHelper = new PlayerHelper(this, playbackManager);
        playerHelper.setActivity(this); // Set activity for UI operations like queue dialog
        webView.addJavascriptInterface(playerHelper, "PlayerHelper");
        
        // Load custom player UI
        webView.loadUrl("file:///android_asset/player-ui/index.html");
        
        // Setup periodic UI updates
        setupPeriodicUpdates();
        
        Log.d(TAG, "PlayerHybrid initialized with WebView UI");
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        
        // WebView settings
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // Enable debugging
        WebView.setWebContentsDebuggingEnabled(true);
        
        // Console logging for debugging
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "WebView Console: " + consoleMessage.message() + 
                      " -- From line " + consoleMessage.lineNumber() + 
                      " of " + consoleMessage.sourceId());
                return true;
            }
        });
        
        // Add WebView to layout
        androidx.constraintlayout.widget.ConstraintLayout mainLayout = 
            findViewById(R.id.main);
        mainLayout.addView(webView, new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
        ));
    }
    
    /**
     * Setup periodic UI updates to sync WebView with native player state
     */
    private void setupPeriodicUpdates() {
        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateWebViewUI();
                updateHandler.postDelayed(this, 500); // Update every 500ms
            }
        };
        updateHandler.post(updateRunnable);
    }
    
    /**
     * Update WebView UI with current player state
     * Calls JavaScript functions to sync the UI
     */
    private void updateWebViewUI() {
        if (webView != null && playbackManager != null) {
            runOnUiThread(() -> {
                try {
                    // Get current state
                    Song currentSong = playbackManager.getCurrentSong();
                    boolean isPlaying = playbackManager.isPlaying();
                    int currentPos = playbackManager.getCurrentPosition();
                    int duration = playbackManager.getDuration();
                    
                    // Build JavaScript update command
                    String js = buildUpdateScript(currentSong, isPlaying, currentPos, duration);
                    
                    // Execute JavaScript
                    webView.evaluateJavascript(js, null);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error updating WebView UI", e);
                }
            });
        }
    }
    
    /**
     * Build JavaScript update script
     */
    private String buildUpdateScript(Song song, boolean isPlaying, int currentPos, int duration) {
        StringBuilder js = new StringBuilder();
        js.append("(function() {");
        js.append("  try {");
        
        // Update song info if available
        if (song != null) {
            String title = escapeJs(song.getTitle());
            String artist = escapeJs(song.getArtist() != null ? song.getArtist() : "Unknown Artist");
            String thumbnail = escapeJs(song.getThumbnailUrl() != null ? song.getThumbnailUrl() : "");
            
            js.append("    if (window.updateSongInfo) {");
            js.append("      window.updateSongInfo('").append(title).append("', '")
              .append(artist).append("', '").append(thumbnail).append("');");
            js.append("    }");
        }
        
        // Update playback state
        js.append("    if (window.updatePlaybackState) {");
        js.append("      window.updatePlaybackState(").append(isPlaying).append(");");
        js.append("    }");
        
        // Update progress
        if (duration > 0) {
            float percentage = (currentPos / (float) duration) * 100;
            js.append("    if (window.updateProgress) {");
            js.append("      window.updateProgress(").append(currentPos).append(", ")
              .append(duration).append(", ").append(percentage).append(");");
            js.append("    }");
        }
        
        js.append("  } catch (e) { console.error('Update error:', e); }");
        js.append("})();");
        
        return js.toString();
    }
    
    /**
     * Escape string for JavaScript
     */
    private String escapeJs(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("'", "\\'")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }
    
    // ========== PLAYBACK LISTENER CALLBACKS ==========
    
    @Override
    public void onSongChanged(Song song) {
        Log.d(TAG, "Song changed: " + (song != null ? song.getTitle() : "null"));
        runOnUiThread(() -> {
            if (webView != null && song != null) {
                String js = String.format("if (window.onSongChanged) window.onSongChanged(%s);", 
                    escapeJs(playerHelper.getCurrentSong()));
                webView.evaluateJavascript(js, null);
            }
        });
    }
    
    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        Log.d(TAG, "Playback state changed: " + isPlaying);
        runOnUiThread(() -> {
            if (webView != null) {
                String js = String.format("if (window.onPlaybackStateChanged) window.onPlaybackStateChanged(%s);", 
                    isPlaying);
                webView.evaluateJavascript(js, null);
            }
        });
    }
    
    @Override
    public void onProgressChanged(int currentMs, int durationMs) {
        // Handled by periodic updates to avoid excessive calls
    }
    
    @Override
    public void onShuffleChanged(boolean shuffle) {
        Log.d(TAG, "Shuffle changed: " + shuffle);
        runOnUiThread(() -> {
            if (webView != null) {
                String js = String.format("if (window.onShuffleChanged) window.onShuffleChanged(%s);", 
                    shuffle);
                webView.evaluateJavascript(js, null);
            }
        });
    }
    
    @Override
    public void onRepeatModeChanged(MusicService.RepeatMode mode) {
        Log.d(TAG, "Repeat mode changed: " + mode.name());
        runOnUiThread(() -> {
            if (webView != null) {
                String js = String.format("if (window.onRepeatModeChanged) window.onRepeatModeChanged('%s');", 
                    mode.name());
                webView.evaluateJavascript(js, null);
            }
        });
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, "Playback error: " + message);
        runOnUiThread(() -> {
            Toast.makeText(this, "Error: " + message, Toast.LENGTH_SHORT).show();
            if (webView != null) {
                String js = String.format("if (window.onError) window.onError('%s');", 
                    escapeJs(message));
                webView.evaluateJavascript(js, null);
            }
        });
    }
    
    @Override
    public void onLoadingStateChanged(boolean isLoading) {
        Log.d(TAG, "Loading state changed: " + isLoading);
        runOnUiThread(() -> {
            if (webView != null) {
                String js = String.format("if (window.onLoadingStateChanged) window.onLoadingStateChanged(%s);", 
                    isLoading);
                webView.evaluateJavascript(js, null);
            }
        });
    }
    
    @Override
    public void onQueueChanged() {
        Log.d(TAG, "Queue changed");
        runOnUiThread(() -> {
            if (webView != null) {
                String js = String.format("if (window.onQueueChanged) window.onQueueChanged(%s);", 
                    escapeJs(playerHelper.getQueue()));
                webView.evaluateJavascript(js, null);
            }
        });
    }
    
    // ========== LIFECYCLE ==========
    
    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.post(updateRunnable);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cleanup
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
        
        if (playerHelper != null) {
            playerHelper.destroy();
        }
        
        if (playbackManager != null) {
            playbackManager.removeListener(this);
        }
        
        if (webView != null) {
            webView.removeJavascriptInterface("PlayerHelper");
            webView.destroy();
        }
        
        Log.d(TAG, "PlayerHybrid destroyed");
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}