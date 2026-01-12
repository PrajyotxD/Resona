package dev.ui.obscura.blur;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import dev.ui.obscura.R;

/**
 * BlurLayout - A LinearLayout with blur effect as background.
 * 
 * Children are arranged normally (vertically/horizontally based on orientation).
 * The blur effect is drawn behind children automatically.
 * 
 * Usage in XML:
 * <dev.ui.obscura.blur.BlurLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:orientation="vertical"
 *     app:obscura_blurRadius="12"
 *     app:obscura_overlayColor="#20000000"
 *     app:obscura_cornerRadius="16dp">
 *     
 *     <TextView ... />
 *     <Button ... />
 *     
 * </dev.ui.obscura.blur.BlurLayout>
 */
public class BlurLayout extends LinearLayout {
    
    private Bitmap blurredBitmap;
    private Paint blurPaint;
    private Paint overlayPaint;
    private int blurRadius = 12;
    private int overlayColor = 0x20000000;
    private float cornerRadius = 0f;
    private boolean blurEnabled = true;
    
    private RectF rectF = new RectF();
    private Path clipPath = new Path();
    
    public BlurLayout(Context context) {
        super(context);
        init(null);
    }
    
    public BlurLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public BlurLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        setWillNotDraw(false);
        
        blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.BlurLayout);
            blurRadius = a.getInt(R.styleable.BlurLayout_obscura_blurRadius, 12);
            overlayColor = a.getColor(R.styleable.BlurLayout_obscura_overlayColor, 0x20000000);
            cornerRadius = a.getDimension(R.styleable.BlurLayout_obscura_cornerRadius, 0f);
            blurEnabled = a.getBoolean(R.styleable.BlurLayout_obscura_blurEnabled, true);
            a.recycle();
        }
        
        overlayPaint.setColor(overlayColor);
    }
    
    public void setBlurRadius(int radius) {
        this.blurRadius = Math.max(1, Math.min(25, radius));
        invalidate();
    }
    
    public void setOverlayColor(int color) {
        this.overlayColor = color;
        overlayPaint.setColor(color);
        invalidate();
    }
    
    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        invalidate();
    }
    
    public void setBlurEnabled(boolean enabled) {
        this.blurEnabled = enabled;
        invalidate();
    }
    
    public void updateBlur(Bitmap sourceBitmap) {
        if (sourceBitmap == null || sourceBitmap.isRecycled()) {
            return;
        }
        
        if (blurredBitmap != null && !blurredBitmap.isRecycled()) {
            blurredBitmap.recycle();
        }
        
        blurredBitmap = FastBlur.apply(sourceBitmap.copy(Bitmap.Config.ARGB_8888, true), blurRadius);
        invalidate();
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw blur background first
        if (blurEnabled) {
            drawBlurBackground(canvas);
        }
        
        // Then draw children on top (arranged by LinearLayout)
        super.dispatchDraw(canvas);
    }
    
    private void drawBlurBackground(Canvas canvas) {
        canvas.save();
        
        rectF.set(0, 0, getWidth(), getHeight());
        
        // Apply corner radius clipping
        if (cornerRadius > 0) {
            clipPath.reset();
            clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);
        }
        
        // Draw blurred bitmap if available
        if (blurredBitmap != null && !blurredBitmap.isRecycled()) {
            canvas.drawBitmap(blurredBitmap, null, rectF, blurPaint);
        }
        
        // Draw overlay
        if (overlayColor != 0) {
            if (cornerRadius > 0) {
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, overlayPaint);
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
