package dev.ui.obscura.blur;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import dev.ui.obscura.R;

/**
 * GlassView - A LinearLayout with frosted glass blur effect as background.
 * 
 * Children are arranged normally (vertically/horizontally based on orientation).
 * The blur effect is drawn behind children automatically.
 * 
 * Usage in XML:
 * <dev.ui.obscura.blur.GlassView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:orientation="vertical"
 *     android:padding="16dp"
 *     app:obscura_blurRadius="15"
 *     app:obscura_opacity="0.85"
 *     app:obscura_overlayColor="#30FFFFFF"
 *     app:obscura_cornerRadius="16dp">
 *     
 *     <TextView ... />
 *     <Button ... />
 *     
 * </dev.ui.obscura.blur.GlassView>
 */
public class GlassView extends LinearLayout {
    
    private Paint blurPaint;
    private Paint overlayPaint;
    private Bitmap blurredBitmap;
    private int blurRadius = 15;
    private float opacity = 0.85f;
    private int overlayColor = 0x30FFFFFF;
    private float cornerRadius = 0f;
    
    private View sourceView;
    private Rect sourceRect = new Rect();
    private RectF cornerRect = new RectF();
    private Path clipPath = new Path();
    private boolean autoUpdate = true;
    private boolean needsBlurUpdate = true;
    
    public GlassView(Context context) {
        super(context);
        init(null);
    }
    
    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public GlassView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        setWillNotDraw(false);
        
        blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.GlassView);
            blurRadius = a.getInt(R.styleable.GlassView_obscura_blurRadius, 15);
            opacity = a.getFloat(R.styleable.GlassView_obscura_opacity, 0.85f);
            overlayColor = a.getColor(R.styleable.GlassView_obscura_overlayColor, 0x30FFFFFF);
            cornerRadius = a.getDimension(R.styleable.GlassView_obscura_cornerRadius, 0f);
            autoUpdate = a.getBoolean(R.styleable.GlassView_obscura_autoUpdate, true);
            a.recycle();
        }
        
        blurPaint.setAlpha((int) (opacity * 255));
        overlayPaint.setColor(overlayColor);
        
        // Auto-capture parent as source
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (sourceView == null && getParent() instanceof View) {
                    View parent = (View) getParent();
                    if (parent.getParent() instanceof View) {
                        setSourceView((View) parent.getParent());
                    } else {
                        setSourceView(parent);
                    }
                }
            }
        });
    }
    
    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        invalidate();
    }
    
    public void setBlurRadius(int radius) {
        this.blurRadius = Math.max(1, Math.min(25, radius));
        updateBlur();
    }
    
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0f, Math.min(1f, opacity));
        blurPaint.setAlpha((int) (this.opacity * 255));
        invalidate();
    }
    
    public void setOverlayColor(int color) {
        this.overlayColor = color;
        invalidate();
    }
    
    public void setSourceView(View view) {
        this.sourceView = view;
        if (view != null && autoUpdate) {
            view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    updateBlur();
                    return true;
                }
            });
        }
    }
    
    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }
    
    public void updateBlur() {
        if (sourceView == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        
        post(new Runnable() {
            @Override
            public void run() {
                captureAndBlur();
                needsBlurUpdate = false;
            }
        });
    }
    
    private void captureAndBlur() {
        try {
            int width = getWidth();
            int height = getHeight();
            
            if (width <= 0 || height <= 0 || sourceView == null) {
                return;
            }
            
            float scale = 0.25f;
            int scaledWidth = Math.max(1, (int) (width * scale));
            int scaledHeight = Math.max(1, (int) (height * scale));
            
            Bitmap source = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(source);
            canvas.scale(scale, scale);
            
            int[] location = new int[2];
            getLocationInWindow(location);
            int[] sourceLocation = new int[2];
            sourceView.getLocationInWindow(sourceLocation);
            
            canvas.translate(sourceLocation[0] - location[0], sourceLocation[1] - location[1]);
            sourceView.draw(canvas);
            
            if (blurredBitmap != null && !blurredBitmap.isRecycled()) {
                blurredBitmap.recycle();
            }
            blurredBitmap = FastBlur.apply(source, blurRadius);
            invalidate();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw blur background first
        drawBlurBackground(canvas);
        
        // Then draw children on top (arranged by LinearLayout)
        super.dispatchDraw(canvas);
    }
    
    private void drawBlurBackground(Canvas canvas) {
        canvas.save();
        
        // Apply corner radius clipping
        if (cornerRadius > 0) {
            clipPath.reset();
            cornerRect.set(0, 0, getWidth(), getHeight());
            clipPath.addRoundRect(cornerRect, cornerRadius, cornerRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);
        }
        
        // Draw blurred bitmap
        if (blurredBitmap != null && !blurredBitmap.isRecycled()) {
            sourceRect.set(0, 0, blurredBitmap.getWidth(), blurredBitmap.getHeight());
            Rect destRect = new Rect(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(blurredBitmap, sourceRect, destRect, blurPaint);
        }
        
        // Draw overlay color
        if (overlayColor != 0) {
            if (cornerRadius > 0) {
                cornerRect.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(cornerRect, cornerRadius, cornerRadius, overlayPaint);
            } else {
                canvas.drawColor(overlayColor);
            }
        }
        
        canvas.restore();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (blurredBitmap != null && !blurredBitmap.isRecycled()) {
            blurredBitmap.recycle();
            blurredBitmap = null;
        }
    }
}
