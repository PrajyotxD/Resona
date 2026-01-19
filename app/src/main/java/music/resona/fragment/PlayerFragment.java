package music.resona.fragment;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import music.resona.database.RecommendationDatabase;
import music.resona.manager.MusicPlaybackManager;
import music.resona.models.Song;
import music.resona.service.MusicService;
import music.resona.utils.PlayerHelper;

/**
 * PlayerFragment - WebView-based music player fragment
 * 
 * Full-screen player fragment that hides the bottom navigation bar
 * and mini player when visible.
 * 
 * Features:
 * - Modern WebView-based UI
 * - Full ExoPlayer2 integration via PlayerHelper
 * - Real-time state synchronization
 * - Lyrics display
 * - Queue management
 */
public class PlayerFragment extends Fragment implements MusicPlaybackManager.PlaybackListener {
    
    private static final String TAG = "PlayerFragment";
    
    private WebView webView;
    private PlayerHelper playerHelper;
    private MusicPlaybackManager playbackManager;
    private Handler updateHandler;
    private Runnable updateRunnable;
    
    // Cache to avoid redundant updates
    private String lastSongId = null;
    private boolean lastPlayingState = false;
    
    // Callback for navigation visibility
    public interface PlayerFragmentCallback {
        void onPlayerFragmentVisibilityChanged(boolean isVisible);
    }
    
    private PlayerFragmentCallback callback;
    
    public static PlayerFragment newInstance() {
        return new PlayerFragment();
    }
    
    public void setCallback(PlayerFragmentCallback callback) {
        this.callback = callback;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Create root layout
        FrameLayout rootLayout = new FrameLayout(requireContext());
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        // Don't set background - let CSS handle it
        
        return rootLayout;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Notify callback that player is now visible - hide nav bar and mini player
        if (callback != null) {
            callback.onPlayerFragmentVisibilityChanged(true);
        }
        
        // Initialize playback manager
        playbackManager = MusicPlaybackManager.getInstance(requireContext());
        playbackManager.addListener(this);
        
        // Setup WebView
        setupWebView((FrameLayout) view);
        
        // Initialize PlayerHelper bridge
        playerHelper = new PlayerHelper(requireContext(), playbackManager);
        playerHelper.setActivity(requireActivity()); // Set activity for UI operations
        webView.addJavascriptInterface(playerHelper, "PlayerHelper");
        
        // Load custom player UI
        webView.loadUrl("file:///android_asset/player-ui/index.html");
        
        // Setup periodic UI updates
        setupPeriodicUpdates();
        
        Log.d(TAG, "PlayerFragment initialized with WebView UI");
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(FrameLayout container) {
        webView = new WebView(requireContext());
        webView.setBackgroundColor(Color.TRANSPARENT);
        // Let CSS handle all styling and colors
        
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
        
        webView.setWebViewClient(new WebViewClient());
        
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
        
        // Add WebView to layout with full size
        container.addView(webView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
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
     * Only sends updates when values actually change to reduce overhead
     */
    private void updateWebViewUI() {
        if (webView != null && playbackManager != null && isAdded()) {
            requireActivity().runOnUiThread(() -> {
                try {
                    Song currentSong = playbackManager.getCurrentSong();
                    boolean isPlaying = playbackManager.isPlaying();
                    int currentPos = playbackManager.getCurrentPosition();
                    int duration = playbackManager.getDuration();
                    
                    // Check if song changed
                    String currentSongId = currentSong != null ? currentSong.getVideoId() : null;
                    boolean songChanged = (currentSongId != null && !currentSongId.equals(lastSongId)) ||
                                         (currentSongId == null && lastSongId != null);
                    
                    // Check if playback state changed
                    boolean playStateChanged = isPlaying != lastPlayingState;
                    
                    // Build optimized update script (only changed values)
                    String js = buildOptimizedUpdateScript(currentSong, isPlaying, currentPos, duration, 
                                                           songChanged, playStateChanged);
                    if (js != null && !js.isEmpty()) {
                        webView.evaluateJavascript(js, null);
                    }
                    
                    // Update cache
                    lastSongId = currentSongId;
                    lastPlayingState = isPlaying;
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error updating WebView UI", e);
                }
            });
        }
    }
    
    /**
     * Build optimized JavaScript update script - only updates changed values
     */
    private String buildOptimizedUpdateScript(Song song, boolean isPlaying, int currentPos, int duration,
                                               boolean songChanged, boolean playStateChanged) {
        StringBuilder js = new StringBuilder();
        js.append("(function() {");
        js.append("  try {");
        
        // Only update song info if song actually changed
        if (songChanged && song != null) {
            String title = escapeJs(song.getTitle());
            String artist = escapeJs(song.getArtist() != null ? song.getArtist() : "Unknown Artist");
            String thumbnail = escapeJs(song.getThumbnailUrl() != null ? song.getThumbnailUrl() : "");
            
            // Check if song is liked
            boolean isLiked = false;
            if (getContext() != null) {
                RecommendationDatabase db = RecommendationDatabase.getInstance(getContext());
                isLiked = db.isLiked(song.getVideoId());
            }
            
            js.append("    if (window.updateSongInfo) {");
            js.append("      window.updateSongInfo('").append(title).append("', '")
              .append(artist).append("', '").append(thumbnail).append("', ").append(isLiked).append(");");
            js.append("    }");
        }
        
        // Only update playback state if it changed
        if (playStateChanged) {
            js.append("    if (window.updatePlaybackState) {");
            js.append("      window.updatePlaybackState(").append(isPlaying).append(");");
            js.append("    }");
        }
        
        // Always update progress (this is expected to change frequently)
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
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                if (webView != null && song != null) {
                    String js = String.format("if (window.onSongChanged) window.onSongChanged(%s);", 
                        escapeJs(playerHelper.getCurrentSong()));
                    webView.evaluateJavascript(js, null);
                }
            });
        }
    }
    
    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        Log.d(TAG, "Playback state changed: " + isPlaying);
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                if (webView != null) {
                    String js = String.format("if (window.onPlaybackStateChanged) window.onPlaybackStateChanged(%s);", 
                        isPlaying);
                    webView.evaluateJavascript(js, null);
                }
            });
        }
    }
    
    @Override
    public void onProgressChanged(int currentMs, int durationMs) {
        // Handled by periodic updates
    }
    
    @Override
    public void onShuffleChanged(boolean shuffle) {
        Log.d(TAG, "Shuffle changed: " + shuffle);
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                if (webView != null) {
                    String js = String.format("if (window.onShuffleChanged) window.onShuffleChanged(%s);", 
                        shuffle);
                    webView.evaluateJavascript(js, null);
                }
            });
        }
    }
    
    @Override
    public void onRepeatModeChanged(MusicService.RepeatMode mode) {
        Log.d(TAG, "Repeat mode changed: " + mode.name());
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                if (webView != null) {
                    String js = String.format("if (window.onRepeatModeChanged) window.onRepeatModeChanged('%s');", 
                        mode.name());
                    webView.evaluateJavascript(js, null);
                }
            });
        }
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, "Playback error: " + message);
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "Error: " + message, Toast.LENGTH_SHORT).show();
                if (webView != null) {
                    String js = String.format("if (window.onError) window.onError('%s');", 
                        escapeJs(message));
                    webView.evaluateJavascript(js, null);
                }
            });
        }
    }
    
    @Override
    public void onLoadingStateChanged(boolean isLoading) {
        Log.d(TAG, "Loading state changed: " + isLoading);
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                if (webView != null) {
                    String js = String.format("if (window.onLoadingStateChanged) window.onLoadingStateChanged(%s);", 
                        isLoading);
                    webView.evaluateJavascript(js, null);
                }
            });
        }
    }
    
    @Override
    public void onQueueChanged() {
        Log.d(TAG, "Queue changed");
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                if (webView != null) {
                    String js = String.format("if (window.onQueueChanged) window.onQueueChanged(%s);", 
                        escapeJs(playerHelper.getQueue()));
                    webView.evaluateJavascript(js, null);
                }
            });
        }
    }
    
    // ========== LIFECYCLE ==========
    
    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.post(updateRunnable);
        }
        // Ensure nav bar is hidden when resuming
        if (callback != null) {
            callback.onPlayerFragmentVisibilityChanged(true);
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Notify callback that player is closing - show nav bar and mini player
        if (callback != null) {
            callback.onPlayerFragmentVisibilityChanged(false);
        }
        
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
        
        Log.d(TAG, "PlayerFragment destroyed");
    }
    
    /**
     * Close the player fragment (called from JavaScript via PlayerHelper)
     */
    public void closePlayer() {
        if (isAdded() && getParentFragmentManager() != null) {
            getParentFragmentManager().popBackStack();
        }
    }
}
