package music.resona.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.RequiresApi;

import xyz.code.blur3.DownscaleScrollableNoiseSuppressor;
import xyz.code.navigationbar.NavigationBar;

/**
 * Container that holds both ResonaMiniPlayer and NavigationBar with unified blur effect.
 * Provides smooth animations for showing/hiding the mini player.
 */
public class NavBarContainer extends FrameLayout {
    
    private DownscaleScrollableNoiseSuppressor noiseSuppressor;
    private Paint paintStrokeTop;
    private Paint paintStrokeBottom;
    private float[] radii = new float[8];
    
    private int backgroundColor = 0x80252525;
    private float cornerRadius = 28f;
    private boolean blurEnabled = true;
    
    private LinearLayout contentContainer;
    private ResonaMiniPlayer miniPlayer;
    private NavigationBar navigationBar;
    
    private boolean isMiniPlayerVisible = false;
    
    public NavBarContainer(Context context) {
        super(context);
        init(context);
    }
    
    public NavBarContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public NavBarContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        // Initialize blur components for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            initBlur();
        }
        
        // Create vertical container for mini player and navbar
        contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        
        addView(contentContainer, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL
        ));
        
        setWillNotDraw(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEnabled) {
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }
    }
    
    @RequiresApi(api = Build.VERSION_CODES.S)
    private void initBlur() {
        noiseSuppressor = new DownscaleScrollableNoiseSuppressor();
        
        paintStrokeTop = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintStrokeTop.setStyle(Paint.Style.STROKE);
        paintStrokeTop.setStrokeWidth(dpf(1));
        paintStrokeTop.setColor(0x40FFFFFF);
        
        paintStrokeBottom = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintStrokeBottom.setStyle(Paint.Style.STROKE);
        paintStrokeBottom.setStrokeWidth(dpf(0.67f));
        paintStrokeBottom.setColor(0x30FFFFFF);
        
        java.util.Arrays.fill(radii, dp(cornerRadius));
    }
    
    public void setMiniPlayer(ResonaMiniPlayer miniPlayer) {
        this.miniPlayer = miniPlayer;
        // Disable mini player's own blur - container handles it
        miniPlayer.setBlurEnabled(false);
        miniPlayer.setMiniPlayerBackgroundColor(0x00000000);
        // Don't add to layout yet - will be shown/hidden dynamically
    }
    
    public void setNavigationBar(NavigationBar navigationBar) {
        this.navigationBar = navigationBar;
        // Disable navbar's own blur - container handles it
        navigationBar.setBlurEnabled(false);
        navigationBar.setNavBarBackgroundColor(0x00000000);
        
        contentContainer.addView(navigationBar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }
    
    public void showMiniPlayer() {
        if (miniPlayer == null || isMiniPlayerVisible) return;
        
        isMiniPlayerVisible = true;
        
        // Measure mini player height before adding
        miniPlayer.measure(
            MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        final int targetHeight = miniPlayer.getMeasuredHeight();
        
        // Add mini player to top of container with 0 height initially
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0
        );
        contentContainer.addView(miniPlayer, 0, params);
        
        // Animate height expansion
        ValueAnimator heightAnimator = ValueAnimator.ofInt(0, targetHeight);
        heightAnimator.setDuration(350);
        heightAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        heightAnimator.addUpdateListener(animation -> {
            params.height = (int) animation.getAnimatedValue();
            miniPlayer.setLayoutParams(params);
            
            // Update blur during animation
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && noiseSuppressor != null) {
                noiseSuppressor.setupRenderNodes(new java.util.ArrayList<>(), 0);
                invalidate();
            }
        });
        
        // Fade in mini player
        miniPlayer.setAlpha(0f);
        miniPlayer.animate()
            .alpha(1f)
            .setDuration(300)
            .setStartDelay(50)
            .setInterpolator(new android.view.animation.LinearInterpolator())
            .start();
        
        heightAnimator.start();
        
        // Reset blur for expanded container
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && noiseSuppressor != null) {
            postDelayed(() -> {
                noiseSuppressor.setupRenderNodes(new java.util.ArrayList<>(), 0);
                invalidate();
            }, 100);
        }
    }
    
    public void hideMiniPlayer() {
        if (miniPlayer == null || !isMiniPlayerVisible) return;
        
        isMiniPlayerVisible = false;
        
        final int startHeight = miniPlayer.getHeight();
        final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) miniPlayer.getLayoutParams();
        
        // Fade out mini player
        miniPlayer.animate()
            .alpha(0f)
            .setDuration(250)
            .setInterpolator(new android.view.animation.LinearInterpolator())
            .start();
        
        // Animate height collapse
        ValueAnimator heightAnimator = ValueAnimator.ofInt(startHeight, 0);
        heightAnimator.setDuration(350);
        heightAnimator.setInterpolator(new AccelerateInterpolator(1.5f));
        heightAnimator.addUpdateListener(animation -> {
            params.height = (int) animation.getAnimatedValue();
            miniPlayer.setLayoutParams(params);
            
            // Update blur during animation
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && noiseSuppressor != null) {
                noiseSuppressor.setupRenderNodes(new java.util.ArrayList<>(), 0);
                invalidate();
            }
        });
        heightAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                contentContainer.removeView(miniPlayer);
                miniPlayer.setAlpha(1f); // Reset alpha for next show
                // Reset blur after hiding
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && noiseSuppressor != null) {
                    post(() -> {
                        noiseSuppressor.setupRenderNodes(new java.util.ArrayList<>(), 0);
                        invalidate();
                    });
                }
            }
        });
        
        heightAnimator.start();
    }
    
    public boolean isMiniPlayerVisible() {
        return isMiniPlayerVisible;
    }
    
    public ResonaMiniPlayer getMiniPlayer() {
        return miniPlayer;
    }
    
    public NavigationBar getNavigationBar() {
        return navigationBar;
    }
    
    public void setNavBarBackgroundColor(int color) {
        this.backgroundColor = color;
        invalidate();
    }
    
    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        java.util.Arrays.fill(radii, dp(radius));
        invalidate();
    }
    
    public void setBlurEnabled(boolean enabled) {
        this.blurEnabled = enabled;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEnabled) {
            setLayerType(LAYER_TYPE_HARDWARE, null);
        } else {
            setLayerType(LAYER_TYPE_NONE, null);
        }
        invalidate();
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEnabled && noiseSuppressor != null) {
            drawBlurBackground(canvas);
        } else {
            drawFallbackBackground(canvas);
        }
        
        super.dispatchDraw(canvas);
    }
    
    private void drawFallbackBackground(Canvas canvas) {
        Path path = new Path();
        RectF rect = new RectF(0, 0, getWidth(), getHeight());
        path.addRoundRect(rect, dp(cornerRadius), dp(cornerRadius), Path.Direction.CW);
        canvas.save();
        canvas.clipPath(path);
        canvas.drawColor(backgroundColor);
        canvas.restore();
    }
    
    @RequiresApi(api = Build.VERSION_CODES.S)
    private void drawBlurBackground(Canvas canvas) {
        if (!canvas.isHardwareAccelerated()) {
            drawFallbackBackground(canvas);
            return;
        }
        
        // Setup render nodes if needed
        if (noiseSuppressor.getRenderNodesCount() == 0) {
            java.util.ArrayList<RectF> positions = new java.util.ArrayList<>();
            positions.add(new RectF(0, 0, getWidth(), getHeight()));
            noiseSuppressor.setupRenderNodes(positions, 1);
        }
        
        // Record content to blur
        try {
            android.graphics.RecordingCanvas recordingCanvas = noiseSuppressor.beginRecordingRect(0);
            recordingCanvas.save();
            recordingCanvas.translate(-getLeft() - getTranslationX(), -getTop() - getTranslationY());
            
            View parent = (View) getParent();
            if (parent != null) {
                int oldVisibility = getVisibility();
                setVisibility(INVISIBLE);
                parent.draw(recordingCanvas);
                setVisibility(oldVisibility);
            }
            
            recordingCanvas.restore();
            noiseSuppressor.endRecordingRect();
        } catch (Exception e) {
            drawFallbackBackground(canvas);
            return;
        }
        
        // Clip to rounded rect
        Path clipPath = new Path();
        RectF clipRect = new RectF(0, 0, getWidth(), getHeight());
        clipPath.addRoundRect(clipRect, radii, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);
        
        // Draw blurred background
        noiseSuppressor.invalidateResultRenderNodes(getWidth(), getHeight());
        noiseSuppressor.draw(canvas, DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS);
        
        // Draw overlay
        canvas.drawColor(backgroundColor);
        
        // Draw strokes
        try {
            xyz.code.blur3.drawable.BlurredBackgroundDrawable.drawStroke(
                canvas, 0, 0, getWidth(), getHeight(), radii,
                dpf(1), true, paintStrokeTop
            );
            
            xyz.code.blur3.drawable.BlurredBackgroundDrawable.drawStroke(
                canvas, 0, 0, getWidth(), getHeight(), radii,
                dpf(0.67f), false, paintStrokeBottom
            );
        } catch (Exception ignored) {}
        
        canvas.restore();
    }
    
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
    
    private float dpf(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
