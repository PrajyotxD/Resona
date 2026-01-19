package music.resona.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import music.resona.R;
import music.resona.manager.MusicPlaybackManager;
import music.resona.models.Song;
import music.resona.service.MusicService;
import music.resona.utils.PlayerHelper;

/**
 * PlayerBottomSheet - BottomSheet-based WebView music player
 * 
 * This provides a modern bottom sheet player UI that:
 * - Opens faster than a full activity
 * - Can be dismissed by swiping down
 * - Maintains full playback functionality via PlayerHelper
 * - Uses the same WebView-based custom UI as PlayerHybrid
 */
public class PlayerBottomSheet extends BottomSheetDialogFragment implements MusicPlaybackManager.PlaybackListener {
    
    private static final String TAG = "PlayerBottomSheet";
    
    private WebView webView;
    private PlayerHelper playerHelper;
    private MusicPlaybackManager playbackManager;
    private Handler updateHandler;
    private Runnable updateRunnable;
    private BottomSheetBehavior<FrameLayout> bottomSheetBehavior;
    private int navigationBarHeight = 0; // Store nav bar height for WebView
    
    public static PlayerBottomSheet newInstance() {
        return new PlayerBottomSheet();
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use a custom theme for full height
        setStyle(STYLE_NORMAL, R.style.PlayerBottomSheetStyle);
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
                
                // Get display metrics (excluding navigation bar for content area)
                DisplayMetrics displayMetrics = new DisplayMetrics();
                requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                int screenHeight = displayMetrics.heightPixels; // This excludes nav bar
                int screenWidth = displayMetrics.widthPixels;
                
                // Set to expanded state by default
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                
                // Set peek height to full screen height
                bottomSheetBehavior.setPeekHeight(screenHeight);
                
                // IMPORTANT: Disable dragging to prevent accidental dismissal
                bottomSheetBehavior.setDraggable(false);
                bottomSheetBehavior.setHideable(true);
                bottomSheetBehavior.setSkipCollapsed(true);
                
                // Set dark background color (NOT transparent - that was the bug!)
                bottomSheet.setBackgroundColor(Color.parseColor("#121212"));
                
                // Set the bottom sheet to full screen height
                ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
                layoutParams.height = screenHeight;
                layoutParams.width = screenWidth;
                bottomSheet.setLayoutParams(layoutParams);
                
                // Also ensure the parent coordinator is full screen
                View parent = bottomSheet.getParent() instanceof View ? (View) bottomSheet.getParent() : null;
                if (parent != null) {
                    ViewGroup.LayoutParams parentParams = parent.getLayoutParams();
                    parentParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    parentParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    parent.setLayoutParams(parentParams);
                }
                
                Log.d(TAG, "BottomSheet configured: " + layoutParams.width + "x" + layoutParams.height);
                
                // Add callback for state changes
                bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        Log.d(TAG, "BottomSheet state changed: " + newState);
                        if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                            dismiss();
                        }
                    }
                    
                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                        // Optional: Add slide animations here
                    }
                });
            } else {
                Log.e(TAG, "Could not find design_bottom_sheet!");
            }
        });
        
        return dialog;
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Create root layout with explicit size
        FrameLayout rootLayout = new FrameLayout(requireContext());
        
        // Get REAL full screen height (including nav bar)
        DisplayMetrics realMetrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getRealMetrics(realMetrics);
        int realScreenHeight = realMetrics.heightPixels;
        
        // Get usable screen height (excluding nav bar)
        DisplayMetrics displayMetrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int usableScreenHeight = displayMetrics.heightPixels;
        int screenWidth = displayMetrics.widthPixels;
        
        // Calculate navigation bar height
        navigationBarHeight = realScreenHeight - usableScreenHeight;
        if (navigationBarHeight < 0) navigationBarHeight = 0;
        
        Log.d(TAG, "Navigation bar height: " + navigationBarHeight + "px");
        
        // Use full screen height - the WebView CSS will handle the nav bar padding
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
            screenWidth,
            realScreenHeight
        ));
        rootLayout.setBackgroundColor(Color.parseColor("#121212")); // Dark background
        rootLayout.setMinimumHeight(realScreenHeight);
        rootLayout.setMinimumWidth(screenWidth);
        
        Log.d(TAG, "Created root layout: " + screenWidth + "x" + realScreenHeight + ", navBar: " + navigationBarHeight);
        
        return rootLayout;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize playback manager
        playbackManager = MusicPlaybackManager.getInstance(requireContext());
        playbackManager.addListener(this);
        
        // Setup WebView
        setupWebView((FrameLayout) view);
        
        // Initialize PlayerHelper bridge
        playerHelper = new PlayerHelper(requireContext(), playbackManager);
        playerHelper.setActivity(requireActivity()); // Set activity for UI operations
        webView.addJavascriptInterface(playerHelper, "PlayerHelper");
        
        // Load custom player UI and inject navigation bar height
        webView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject navigation bar height as CSS variable
                String js = "document.documentElement.style.setProperty('--nav-bar-height', '" + navigationBarHeight + "px');";
                webView.evaluateJavascript(js, null);
                Log.d(TAG, "Injected nav bar height: " + navigationBarHeight + "px");
            }
        });
        webView.loadUrl("file:///android_asset/player-ui/index.html");
        
        // Setup periodic UI updates
        setupPeriodicUpdates();
        
        Log.d(TAG, "PlayerBottomSheet initialized with WebView UI");
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(FrameLayout container) {
        webView = new WebView(requireContext());
        // Set WebView background to dark (not transparent) to ensure visibility
        webView.setBackgroundColor(Color.parseColor("#121212"));
        
        // WebView settings
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        
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
        
        // Add WebView to container with explicit match parent
        FrameLayout.LayoutParams webViewParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        container.addView(webView, webViewParams);
        
        Log.d(TAG, "WebView added to container");
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
     */
    private void updateWebViewUI() {
        if (webView != null && playbackManager != null && isAdded()) {
            try {
                // Get current state
                Song currentSong = playbackManager.getCurrentSong();
                boolean isPlaying = playbackManager.isPlaying();
                int currentPos = playbackManager.getCurrentPosition();
                int duration = playbackManager.getDuration();
                
                // Build JavaScript update command
                String js = buildUpdateScript(currentSong, isPlaying, currentPos, duration);
                
                // Execute JavaScript on UI thread
                requireActivity().runOnUiThread(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript(js, null);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error updating WebView UI", e);
            }
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
        if (isAdded() && webView != null && song != null) {
            requireActivity().runOnUiThread(() -> {
                if (webView != null) {
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
        if (isAdded() && webView != null) {
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
        // Handled by periodic updates to avoid excessive calls
    }
    
    @Override
    public void onShuffleChanged(boolean shuffle) {
        Log.d(TAG, "Shuffle changed: " + shuffle);
        if (isAdded() && webView != null) {
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
        if (isAdded() && webView != null) {
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
        if (isAdded() && webView != null) {
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
        if (isAdded() && webView != null) {
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
            webView = null;
        }
        
        Log.d(TAG, "PlayerBottomSheet destroyed");
    }
    
    /**
     * Minimize the bottom sheet
     */
    public void minimize() {
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }
    
    /**
     * Expand the bottom sheet
     */
    public void expand() {
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }
}
