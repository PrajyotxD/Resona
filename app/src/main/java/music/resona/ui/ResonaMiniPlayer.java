package music.resona.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.res.ResourcesCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import music.resona.R;
import music.resona.models.Song;
import music.resona.service.MusicService;
import music.resona.utils.UiUXUtil;
import xyz.code.blur3.DownscaleScrollableNoiseSuppressor;

/**
 * MiniPlayer with blur effect that expands into full screen player.
 * Supports smooth transformation between mini and full screen modes.
 */
public class ResonaMiniPlayer extends FrameLayout {
    
    private DownscaleScrollableNoiseSuppressor noiseSuppressor;
    private Paint paintStrokeTop;
    private Paint paintStrokeBottom;
    private float[] radii = new float[8];
    
    private int backgroundColor = 0x80252525;
    private float cornerRadius = 24f;
    private boolean blurEnabled = true;
    
    private ImageView albumArt;
    private TextView songTitle;
    private TextView artistName;
    private ImageView playPauseButton;
    private ImageView nextButton;
    private ProgressBar loadingIndicator;
    private View progressBar;
    
    private boolean isPlaying = false;
    private boolean isLoading = false;
    private float progress = 0f;
    
    private OnMiniPlayerListener listener;
    
    public interface OnMiniPlayerListener {
        void onPlayPause();
        void onNext();
        void onMiniPlayerClick();
    }
    
    public ResonaMiniPlayer(Context context) {
        super(context);
        init(context);
    }
    
    public ResonaMiniPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public ResonaMiniPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        // Initialize blur components for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            initBlur();
        }
        
        createUI(context);
        
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
    
    private void createUI(Context context) {
        // Main container
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        
        // Progress bar at top
        progressBar = new View(context);
        progressBar.setBackgroundColor(0xFF1DB954);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(0, dp(3));
        progressParams.gravity = Gravity.TOP;
        addView(progressBar, progressParams);
        
        // Content row
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        
        // Album art with rounded corners
        albumArt = new ImageView(context);
        albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        android.graphics.drawable.GradientDrawable artBg = new android.graphics.drawable.GradientDrawable();
        artBg.setColor(0xFF1DB954);
        artBg.setCornerRadius(dp(8));
        albumArt.setBackground(artBg);
        albumArt.setClipToOutline(true);
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        artParams.setMargins(0, 0, dp(12), 0);
        row.addView(albumArt, artParams);
        
        // Song info container
        LinearLayout infoContainer = new LinearLayout(context);
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        infoContainer.setGravity(Gravity.CENTER_VERTICAL);
        
        songTitle = new TextView(context);
        songTitle.setText("Not Playing");
        songTitle.setTextSize(14);
        songTitle.setTextColor(Color.WHITE);
        songTitle.setSingleLine(true);
        songTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        songTitle.setMarqueeRepeatLimit(-1);
        songTitle.setSelected(true);
        UiUXUtil.typeface(context, songTitle, "akatski.ttf", Typeface.BOLD);
        infoContainer.addView(songTitle);
        
        artistName = new TextView(context);
        artistName.setText("Tap to play music");
        artistName.setTextSize(12);
        artistName.setTextColor(0xFFAAAAAA);
        artistName.setSingleLine(true);
        artistName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        UiUXUtil.typeface(context, artistName, "copy.ttf", Typeface.NORMAL);
        infoContainer.addView(artistName);
        
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(infoContainer, infoParams);
        
        // Loading indicator
        loadingIndicator = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        loadingIndicator.setIndeterminate(true);
        loadingIndicator.setVisibility(GONE);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        loadingParams.setMargins(dp(8), 0, dp(8), 0);
        row.addView(loadingIndicator, loadingParams);
        
        // Play/Pause button
        playPauseButton = new ImageView(context);
        playPauseButton.setImageResource(R.drawable.play);
        playPauseButton.setColorFilter(Color.WHITE);
        playPauseButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        playPauseButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        playPauseButton.setOnClickListener(v -> {
            if (listener != null) listener.onPlayPause();
        });
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        playParams.setMargins(dp(4), 0, dp(4), 0);
        row.addView(playPauseButton, playParams);
        
        // Next button
        nextButton = new ImageView(context);
        nextButton.setImageResource(android.R.drawable.ic_media_next);
        nextButton.setColorFilter(Color.WHITE);
        nextButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        nextButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        nextButton.setOnClickListener(v -> {
            if (listener != null) listener.onNext();
        });
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        row.addView(nextButton, nextParams);
        
        container.addView(row, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        
        // Make entire mini player clickable
        row.setOnClickListener(v -> {
            if (listener != null) listener.onMiniPlayerClick();
        });
        
        addView(container, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ));
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
    
    // Public API
    
    public void setOnMiniPlayerListener(OnMiniPlayerListener listener) {
        this.listener = listener;
    }
    
    public void setSong(@Nullable Song song) {
        if (song == null) {
            songTitle.setText("Not Playing");
            artistName.setText("Tap to play music");
            albumArt.setImageResource(R.drawable.memefi);
            return;
        }
        
        songTitle.setText(song.getTitle());
        artistName.setText(song.getArtist() != null ? song.getArtist() : "Unknown Artist");
        
        // Load album art
        if (song.getThumbnailUrl() != null && !song.getThumbnailUrl().isEmpty()) {
            Glide.with(getContext())
                .load(song.getThumbnailUrl())
                .placeholder(R.drawable.memefi)
                .error(R.drawable.memefi)
                .centerCrop()
                .into(albumArt);
        } else {
            albumArt.setImageResource(R.drawable.memefi);
        }
    }
    
    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        playPauseButton.setImageResource(playing ? android.R.drawable.ic_media_pause : R.drawable.play);
        
        // Add pulse animation when playing
        if (playing) {
            albumArt.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    albumArt.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(500)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
                })
                .start();
        }
    }
    
    public void setLoading(boolean loading) {
        this.isLoading = loading;
        loadingIndicator.setVisibility(loading ? VISIBLE : GONE);
        playPauseButton.setVisibility(loading ? GONE : VISIBLE);
    }
    
    public void setProgress(float progress) {
        this.progress = progress;
        int width = (int) (getWidth() * progress);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) progressBar.getLayoutParams();
        params.width = width;
        progressBar.setLayoutParams(params);
    }
    
    public void setBlurEnabled(boolean enabled) {
        this.blurEnabled = enabled;
        invalidate();
    }
    
    public void setMiniPlayerBackgroundColor(int color) {
        this.backgroundColor = color;
        invalidate();
    }
    
    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        java.util.Arrays.fill(radii, dp(radius));
        invalidate();
    }
    
    public void show() {
        if (getVisibility() == VISIBLE) return;
        
        setVisibility(VISIBLE);
        setAlpha(0f);
        setTranslationY(dp(20));
        
        animate()
            .alpha(1f)
            .translationY(0)
            .setDuration(300)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }
    
    public void hide() {
        if (getVisibility() != VISIBLE) return;
        
        animate()
            .alpha(0f)
            .translationY(dp(20))
            .setDuration(250)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> setVisibility(GONE))
            .start();
    }
    
    public boolean isCurrentlyPlaying() {
        return isPlaying;
    }
    
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
    
    private float dpf(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
