package music.resona.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import music.resona.R;
import music.resona.manager.MusicPlaybackManager;
import music.resona.models.Song;
import music.resona.service.MusicService;
import music.resona.ui.QueueBottomSheet;
import music.resona.utils.UiUXUtil;
import xyz.code.blur3.DownscaleScrollableNoiseSuppressor;

/**
 * Full screen player that transforms from mini player with smooth expansion animation.
 * Features blur background, swipe to dismiss, and modern design.
 */
public class FullScreenPlayerActivity extends Activity implements MusicPlaybackManager.PlaybackListener {
    
    private static final String TAG = "FullScreenPlayer";
    
    // Views
    private BlurredContainer rootContainer;
    private FrameLayout contentContainer;
    private ImageView albumArt;
    private ImageView albumArtLarge;
    private TextView songTitle;
    private TextView artistName;
    private SeekBar seekBar;
    private TextView currentTime;
    private TextView totalTime;
    private ImageView shuffleButton;
    private ImageView previousButton;
    private ImageView playPauseButton;
    private ImageView nextButton;
    private ImageView repeatButton;
    private ProgressBar loadingIndicator;
    private View dragHandle;
    
    // State
    private MusicPlaybackManager playbackManager;
    private boolean isSeekBarDragging = false;
    private boolean isAnimating = false;
    
    // Gesture handling for swipe down
    private float initialY;
    private float currentY;
    private boolean isDragging = false;
    
    /**
     * Custom blurred background container matching mini player style.
     */
    private class BlurredContainer extends FrameLayout {
        private DownscaleScrollableNoiseSuppressor noiseSuppressor;
        private Paint paintStrokeTop;
        private Paint paintStrokeBottom;
        private float[] radii = new float[8];
        private int backgroundColor = 0xCC1A1A1A; // Darker for full screen
        private float cornerRadius = 32f;
        
        public BlurredContainer(android.content.Context context) {
            super(context);
            init();
        }
        
        @RequiresApi(api = Build.VERSION_CODES.S)
        private void init() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                noiseSuppressor = new DownscaleScrollableNoiseSuppressor();
                setLayerType(LAYER_TYPE_HARDWARE, null);
            }
            
            // Top stroke
            paintStrokeTop = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintStrokeTop.setStyle(Paint.Style.STROKE);
            paintStrokeTop.setStrokeWidth(2f);
            paintStrokeTop.setColor(0x40FFFFFF);
            
            // Bottom stroke
            paintStrokeBottom = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintStrokeBottom.setStyle(Paint.Style.STROKE);
            paintStrokeBottom.setStrokeWidth(1f);
            paintStrokeBottom.setColor(0x20000000);
            
            // Rounded corners (top only initially, will be all corners when expanded)
            for (int i = 0; i < 8; i++) {
                radii[i] = cornerRadius;
            }
            
            setWillNotDraw(false);
        }
        
        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && noiseSuppressor != null) {
                noiseSuppressor.invalidateResultRenderNodes(getWidth(), getHeight());
                noiseSuppressor.draw(canvas, DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS);
            } else {
                // Fallback for older devices
                canvas.drawColor(backgroundColor);
            }
            super.dispatchDraw(canvas);
        }
        
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            
            // Draw rounded rect with blur
            Path path = new Path();
            RectF rect = new RectF(0, 0, getWidth(), getHeight());
            path.addRoundRect(rect, radii, Path.Direction.CW);
            
            // Draw background
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(backgroundColor);
            canvas.drawPath(path, bgPaint);
            
            // Draw strokes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    float width = getWidth();
                    float height = getHeight();
                    xyz.code.blur3.drawable.BlurredBackgroundDrawable.drawStroke(
                        canvas, 0, 0, width, height, radii,
                        2f, true, paintStrokeTop
                    );
                    xyz.code.blur3.drawable.BlurredBackgroundDrawable.drawStroke(
                        canvas, 0, 0, width, height, radii,
                        1f, false, paintStrokeBottom
                    );
                } catch (Exception ignored) {}
            }
        }
    }
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Make activity transparent for overlay effect
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | 
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
        
        setupUI();
        
        playbackManager = MusicPlaybackManager.getInstance(this);
        playbackManager.addListener(this);
        
        // Initial state update
        updateSongInfo(playbackManager.getCurrentSong());
        updatePlaybackState(playbackManager.isPlaying());
        updateShuffleState(playbackManager.isShuffleEnabled());
        updateRepeatState(playbackManager.getRepeatMode());
        
        // Play expand animation from mini player position
        playExpandAnimation();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playbackManager != null) {
            playbackManager.removeListener(this);
        }
    }
    
    // Removed @RequiresApi(S) as we handle checks internally
    private void setupUI() {
        // Root frame (transparent background)
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x80000000); // Semi-transparent scrim
        root.setOnClickListener(v -> {
            // Click outside to dismiss
            if (!isDragging && !isAnimating) {
                playCollapseAnimation();
            }
        });
        
        // Blurred container that transforms from mini player
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rootContainer = new BlurredContainer(this);
        } else {
            // Fallback for older Android  
            rootContainer = new BlurredContainer(this) {
                @Override
                protected void dispatchDraw(Canvas canvas) {
                    canvas.drawColor(0xCC1A1A1A);
                    super.dispatchDraw(canvas);
                }
            };
        }
        
        rootContainer.setClickable(true); // Prevent clicks from going through
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            rootContainer.setElevation(dp(16));
        }
        
        // Content container
        contentContainer = new FrameLayout(this);
        
        createPlayerContent();
        
        rootContainer.addView(contentContainer);
        
        // Position at bottom initially (mini player position)
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        containerParams.setMargins(0, 0, 0, 0);
        root.addView(rootContainer, containerParams);
        
        setContentView(root);
        
        // Setup touch handling for swipe down
        setupGestureHandling();
    }
    
    private void createPlayerContent() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(dp(20), dp(16), dp(20), dp(24));
        
        // Drag handle at top
        createDragHandle(mainLayout);
        
        // Spacer
        View topSpacer = new View(this);
        mainLayout.addView(topSpacer, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.5f
        ));
        
        // Large album art
        createLargeAlbumArt(mainLayout);
        
        // Spacer
        View middleSpacer = new View(this);
        mainLayout.addView(middleSpacer, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.5f
        ));
        
        // Song info
        createSongInfo(mainLayout);
        
        // Seekbar
        createSeekBar(mainLayout);
        
        // Main controls
        createControls(mainLayout);
        
        // Additional controls (shuffle, repeat, etc.)
        createAdditionalControls(mainLayout);
        
        contentContainer.addView(mainLayout);
    }
    
    private void createDragHandle(LinearLayout parent) {
        FrameLayout handleContainer = new FrameLayout(this);
        handleContainer.setPadding(0, dp(8), 0, dp(16));
        
        dragHandle = new View(this);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0x40FFFFFF);
        handleBg.setCornerRadius(dp(3));
        dragHandle.setBackground(handleBg);
        
        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(dp(40), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleContainer.addView(dragHandle, handleParams);
        
        parent.addView(handleContainer);
    }
    
    private void createLargeAlbumArt(LinearLayout parent) {
        FrameLayout artContainer = new FrameLayout(this);
        
        int size = Math.min(
            getResources().getDisplayMetrics().widthPixels - dp(80),
            dp(340)
        );
        
        albumArtLarge = new ImageView(this);
        albumArtLarge.setScaleType(ImageView.ScaleType.CENTER_CROP);
        albumArtLarge.setClipToOutline(true);
        albumArtLarge.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            albumArtLarge.setElevation(dp(12));
        }
        
        FrameLayout.LayoutParams artParams = new FrameLayout.LayoutParams(size, size);
        artParams.gravity = Gravity.CENTER;
        artContainer.addView(albumArtLarge, artParams);
        
        // Loading indicator
        loadingIndicator = new ProgressBar(this);
        loadingIndicator.setVisibility(View.GONE);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        loadingParams.gravity = Gravity.CENTER;
        artContainer.addView(loadingIndicator, loadingParams);
        
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        parent.addView(artContainer, containerParams);
    }
    
    private void createSongInfo(LinearLayout parent) {
        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setOrientation(LinearLayout.HORIZONTAL);
        infoLayout.setGravity(Gravity.CENTER_VERTICAL);
        infoLayout.setPadding(dp(8), dp(24), dp(8), dp(8));
        
        // Text container
        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        
        songTitle = new TextView(this);
        songTitle.setText("Song Title");
        songTitle.setTextSize(22);
        songTitle.setTextColor(Color.WHITE);
        songTitle.setSingleLine(true);
        songTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        songTitle.setMarqueeRepeatLimit(-1);
        songTitle.setSelected(true);
        UiUXUtil.typeface(this, songTitle, "akatski.ttf", Typeface.BOLD);
        textContainer.addView(songTitle);
        
        artistName = new TextView(this);
        artistName.setText("Artist Name");
        artistName.setTextSize(15);
        artistName.setTextColor(0xAAFFFFFF);
        artistName.setSingleLine(true);
        artistName.setPadding(0, dp(2), 0, 0);
        UiUXUtil.typeface(this, artistName, "medium.ttf", Typeface.NORMAL);
        textContainer.addView(artistName);
        
        infoLayout.addView(textContainer, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ));
        
        // Like button
        ImageView likeButton = new ImageView(this);
        likeButton.setImageResource(android.R.drawable.star_off);
        likeButton.setColorFilter(Color.WHITE);
        likeButton.setAlpha(0.8f);
        likeButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams likeParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        likeParams.setMargins(dp(12), 0, 0, 0);
        infoLayout.addView(likeButton, likeParams);
        
        parent.addView(infoLayout);
    }
    
    private void createSeekBar(LinearLayout parent) {
        LinearLayout seekContainer = new LinearLayout(this);
        seekContainer.setOrientation(LinearLayout.VERTICAL);
        seekContainer.setPadding(dp(8), dp(12), dp(8), dp(8));
        
        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setProgress(0);
        seekBar.getProgressDrawable().setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        seekBar.getThumb().setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && playbackManager != null) {
                    int duration = playbackManager.getDuration();
                    int newPosition = (int) ((progress / 1000f) * duration);
                    currentTime.setText(formatTime(newPosition));
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeekBarDragging = true;
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isSeekBarDragging = false;
                if (playbackManager != null) {
                    int duration = playbackManager.getDuration();
                    int newPosition = (int) ((seekBar.getProgress() / 1000f) * duration);
                    playbackManager.seekTo(newPosition);
                }
            }
        });
        
        seekContainer.addView(seekBar);
        
        // Time labels
        LinearLayout timeLayout = new LinearLayout(this);
        timeLayout.setOrientation(LinearLayout.HORIZONTAL);
        timeLayout.setPadding(dp(4), dp(8), dp(4), 0);
        
        currentTime = new TextView(this);
        currentTime.setText("0:00");
        currentTime.setTextSize(12);
        currentTime.setTextColor(0x99FFFFFF);
        UiUXUtil.typeface(this, currentTime, "medium.ttf", Typeface.NORMAL);
        timeLayout.addView(currentTime, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ));
        
        totalTime = new TextView(this);
        totalTime.setText("0:00");
        totalTime.setTextSize(12);
        totalTime.setTextColor(0x99FFFFFF);
        totalTime.setGravity(Gravity.END);
        UiUXUtil.typeface(this, totalTime, "medium.ttf", Typeface.NORMAL);
        timeLayout.addView(totalTime, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ));
        
        seekContainer.addView(timeLayout);
        parent.addView(seekContainer);
    }
    
    private void createControls(LinearLayout parent) {
        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setGravity(Gravity.CENTER);
        controlsRow.setPadding(dp(8), dp(20), dp(8), dp(16));
        
        // Previous
        previousButton = createIconButton(android.R.drawable.ic_media_previous, dp(52));
        previousButton.setOnClickListener(v -> {
            if (playbackManager != null) playbackManager.previous();
        });
        controlsRow.addView(previousButton);
        
        // Spacer
        View spacer1 = new View(this);
        controlsRow.addView(spacer1, new LinearLayout.LayoutParams(0, 0, 1f));
        
        // Play/Pause (large circular button)
        FrameLayout playContainer = new FrameLayout(this);
        GradientDrawable playBg = new GradientDrawable();
        playBg.setColor(Color.WHITE);
        playBg.setShape(GradientDrawable.OVAL);
        playContainer.setBackground(playBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            playContainer.setElevation(dp(8));
        }
        
        playPauseButton = new ImageView(this);
        playPauseButton.setImageResource(R.drawable.play);
        playPauseButton.setColorFilter(Color.BLACK);
        playPauseButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        playPauseButton.setPadding(dp(20), dp(20), dp(20), dp(20));
        playContainer.addView(playPauseButton, new FrameLayout.LayoutParams(dp(72), dp(72)));
        
        playContainer.setOnClickListener(v -> {
            if (playbackManager != null) playbackManager.togglePlayPause();
        });
        
        controlsRow.addView(playContainer, new LinearLayout.LayoutParams(dp(72), dp(72)));
        
        // Spacer
        View spacer2 = new View(this);
        controlsRow.addView(spacer2, new LinearLayout.LayoutParams(0, 0, 1f));
        
        // Next
        nextButton = createIconButton(android.R.drawable.ic_media_next, dp(52));
        nextButton.setOnClickListener(v -> {
            if (playbackManager != null) playbackManager.next();
        });
        controlsRow.addView(nextButton);
        
        parent.addView(controlsRow);
    }
    
    private void createAdditionalControls(LinearLayout parent) {
        LinearLayout additionalRow = new LinearLayout(this);
        additionalRow.setOrientation(LinearLayout.HORIZONTAL);
        additionalRow.setGravity(Gravity.CENTER);
        additionalRow.setPadding(dp(16), 0, dp(16), dp(8));
        
        // Shuffle
        shuffleButton = createIconButton(R.drawable.shuffle, dp(40));
        shuffleButton.setAlpha(0.7f);
        shuffleButton.setOnClickListener(v -> {
            if (playbackManager != null) playbackManager.toggleShuffle();
        });
        additionalRow.addView(shuffleButton);
        
        // Spacer
        View spacer1 = new View(this);
        additionalRow.addView(spacer1, new LinearLayout.LayoutParams(0, 0, 1f));
        
        // Lyrics
        ImageView lyricsButton = createIconButton(android.R.drawable.ic_menu_edit, dp(40));
        lyricsButton.setAlpha(0.6f);
        additionalRow.addView(lyricsButton);
        
        // Spacer
        View spacer2 = new View(this);
        additionalRow.addView(spacer2, new LinearLayout.LayoutParams(0, 0, 1f));
        
        // Queue
        ImageView queueButton = createIconButton(R.drawable.queue, dp(40));
        queueButton.setAlpha(0.6f);
        queueButton.setOnClickListener(v -> showQueueBottomSheet());
        additionalRow.addView(queueButton);
        
        // Spacer
        View spacer3 = new View(this);
        additionalRow.addView(spacer3, new LinearLayout.LayoutParams(0, 0, 1f));
        
        // Repeat
        repeatButton = createIconButton(android.R.drawable.ic_menu_rotate, dp(40));
        repeatButton.setAlpha(0.7f);
        repeatButton.setOnClickListener(v -> {
            if (playbackManager != null) playbackManager.toggleRepeat();
        });
        additionalRow.addView(repeatButton);
        
        parent.addView(additionalRow);
    }
    
    private ImageView createIconButton(int resId, int size) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(resId);
        iv.setColorFilter(Color.WHITE);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(dp(8), dp(8), dp(8), dp(8));
        iv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return iv;
    }
    
    private void setupGestureHandling() {
        rootContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = event.getRawY();
                        currentY = initialY;
                        isDragging = false;
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        currentY = event.getRawY();
                        float deltaY = currentY - initialY;
                        
                        // Only allow downward dragging
                        if (deltaY > dp(20)) {
                            isDragging = true;
                            rootContainer.setTranslationY(deltaY);
                            
                            // Fade out as dragging down
                            float alpha = 1f - Math.min(deltaY / (getResources().getDisplayMetrics().heightPixels * 0.5f), 1f);
                            rootContainer.setAlpha(alpha);
                        }
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float finalDelta = currentY - initialY;
                        
                        // If dragged more than 30% of screen height, dismiss
                        if (finalDelta > getResources().getDisplayMetrics().heightPixels * 0.3f) {
                            playCollapseAnimation();
                        } else {
                            // Snap back
                            rootContainer.animate()
                                .translationY(0)
                                .alpha(1f)
                                .setDuration(250)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        }
                        
                        isDragging = false;
                        return true;
                }
                return false;
            }
        });
    }
    
    // Animations
    
    private void playExpandAnimation() {
        isAnimating = true;
        
        // Get screen height for animation from bottom
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        
        // Start from bottom (mini player position)
        rootContainer.setTranslationY(screenHeight);
        rootContainer.setAlpha(0f);
        
        // Animate sliding up from bottom
        rootContainer.animate()
            .translationY(0)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(new DecelerateInterpolator(2f))
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    isAnimating = false;
                }
            })
            .start();
    }
    
    private void playCollapseAnimation() {
        if (isAnimating) return;
        isAnimating = true;
        
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        
        // Animate sliding down to bottom
        rootContainer.animate()
            .translationY(screenHeight)
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    finish();
                    overridePendingTransition(0, 0);
                }
            })
            .start();
    }
    
    // State updates
    
    private void updateSongInfo(@Nullable Song song) {
        if (song == null) {
            songTitle.setText("Not Playing");
            artistName.setText("Select a song");
            // Reset seekbar for no song
            seekBar.setProgress(0);
            currentTime.setText("0:00");
            totalTime.setText("0:00");
            return;
        }
        
        songTitle.setText(song.getTitle());
        artistName.setText(song.getArtist() != null ? song.getArtist() : "Unknown Artist");
        
        // Reset seekbar immediately when song changes
        seekBar.setProgress(0);
        currentTime.setText("0:00");
        totalTime.setText("--:--"); // Show loading state until duration is available
        
        // Load album art
        String url = song.getThumbnailUrl();
        if (url != null && !url.isEmpty()) {
            Glide.with(this)
                .load(url)
                .centerCrop()
                .transform(new RoundedCorners(dp(16)))
                .placeholder(R.drawable.memefi)
                .into(albumArtLarge);
        } else {
            albumArtLarge.setImageResource(R.drawable.memefi);
        }
    }
    
    private void updatePlaybackState(boolean isPlaying) {
        playPauseButton.setImageResource(isPlaying ? android.R.drawable.ic_media_pause : R.drawable.play);
    }
    
    private void updateProgress(int currentMs, int durationMs) {
        if (!isSeekBarDragging && durationMs > 0) {
            int progress = (int) ((currentMs / (float) durationMs) * 1000);
            seekBar.setProgress(progress);
            currentTime.setText(formatTime(currentMs));
            totalTime.setText(formatTime(durationMs));
        }
    }
    
    private void updateShuffleState(boolean shuffle) {
        shuffleButton.setAlpha(shuffle ? 1.0f : 0.7f);
        if (shuffle) {
            shuffleButton.setColorFilter(0xFF1DB954); // Accent color
        } else {
            shuffleButton.setColorFilter(Color.WHITE);
        }
    }
    
    private void updateRepeatState(MusicService.RepeatMode mode) {
        switch (mode) {
            case OFF:
                repeatButton.setColorFilter(Color.WHITE);
                repeatButton.setAlpha(0.7f);
                break;
            case ALL:
                repeatButton.setColorFilter(0xFF1DB954);
                repeatButton.setAlpha(1.0f);
                break;
            case ONE:
                repeatButton.setColorFilter(0xFF1DB954); // Maybe add "1" indicator logic
                repeatButton.setAlpha(1.0f);
                break;
        }
    }
    
    private void updateLoadingState(boolean isLoading) {
        loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        playPauseButton.setEnabled(!isLoading);
        playPauseButton.setAlpha(isLoading ? 0.5f : 1f);
    }
    
    private String formatTime(int ms) {
        int seconds = ms / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds);
    }
    
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
    
    @Override
    public void onBackPressed() {
        playCollapseAnimation();
    }
    
    // PlaybackListener implementation
    
    @Override
    public void onSongChanged(Song song) {
        runOnUiThread(() -> updateSongInfo(song));
    }
    
    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        runOnUiThread(() -> updatePlaybackState(isPlaying));
    }
    
    @Override
    public void onProgressChanged(int currentMs, int durationMs) {
        runOnUiThread(() -> updateProgress(currentMs, durationMs));
    }
    
    @Override
    public void onShuffleChanged(boolean shuffle) {
        runOnUiThread(() -> updateShuffleState(shuffle));
    }
    
    @Override
    public void onRepeatModeChanged(MusicService.RepeatMode mode) {
        runOnUiThread(() -> updateRepeatState(mode));
    }
    
    @Override
    public void onError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
    
    @Override
    public void onLoadingStateChanged(boolean isLoading) {
        runOnUiThread(() -> updateLoadingState(isLoading));
    }
    
    @Override
    public void onQueueChanged() {
        runOnUiThread(() -> {
            // Update queue display if needed
            android.util.Log.d("FullScreenPlayerActivity", "Queue changed");
        });
    }
    
    public void onNeedsStreamUrl(Song song) {
        // Handled by MusicPlaybackManager
    }
    
    /**
     * Show bottom sheet with current queue.
     */
    private void showQueueBottomSheet() {
        if (playbackManager == null) return;
        
        List<Song> queueSongs = playbackManager.getQueue();
        int currentIndex = playbackManager.getCurrentQueueIndex();
        
        android.util.Log.d(TAG, "showQueueBottomSheet - Queue size: " + queueSongs.size() + ", Current index: " + currentIndex);
        
        if (queueSongs.isEmpty()) {
            Toast.makeText(this, "Queue is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create an array to hold the bottom sheet reference for use in callbacks
        final QueueBottomSheet[] sheetHolder = new QueueBottomSheet[1];
        
        QueueBottomSheet queueSheet = new QueueBottomSheet(
            this,
            queueSongs,
            currentIndex,
            new QueueBottomSheet.OnQueueItemClickListener() {
                @Override
                public void onSongClick(int position) {
                    // Jump to selected song
                    if (playbackManager != null) {
                        playbackManager.skipToPosition(position);
                    }
                    // Optionally dismiss the sheet after selection
                    // if (sheetHolder[0] != null) sheetHolder[0].dismiss();
                }
                
                @Override
                public void onRemoveClick(int position) {
                    // Remove song from queue
                    if (playbackManager != null) {
                        playbackManager.removeFromQueue(position);
                        
                        // Update the bottom sheet
                        List<Song> updatedQueue = playbackManager.getQueue();
                        int updatedIndex = playbackManager.getCurrentQueueIndex();
                        if (sheetHolder[0] != null) {
                            sheetHolder[0].updateQueue(updatedQueue, updatedIndex);
                        }
                        
                        if (updatedQueue.isEmpty()) {
                            if (sheetHolder[0] != null) {
                                sheetHolder[0].dismiss();
                            }
                            Toast.makeText(FullScreenPlayerActivity.this, 
                                "Queue is now empty", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        );
        
        sheetHolder[0] = queueSheet;
        queueSheet.show();
    }
}
