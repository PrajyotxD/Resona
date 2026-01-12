package dev.ui.obscura.blur;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import dev.ui.obscura.R;

public class BlurredFrameLayout extends android.widget.FrameLayout {
    
    private Bitmap blurredBitmap;
    private Paint blurPaint;
    private int blurRadius = 12;
    private int overlayColor = 0x20000000;
    private float cornerRadius = 0f;
    private boolean blurEnabled = true;
    
    private RectF rectF = new RectF();
    
    public BlurredFrameLayout(Context context) {
        super(context);
        init(null);
    }
    
    public BlurredFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public BlurredFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        setWillNotDraw(false);
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.BlurredFrameLayout);
            blurRadius = a.getInt(R.styleable.BlurredFrameLayout_obscura_blurRadius, 12);
            overlayColor = a.getColor(R.styleable.BlurredFrameLayout_obscura_overlayColor, 0x20000000);
            cornerRadius = a.getDimension(R.styleable.BlurredFrameLayout_obscura_cornerRadius, 0f);
            blurEnabled = a.getBoolean(R.styleable.BlurredFrameLayout_obscura_blurEnabled, true);
            a.recycle();
        }
    }
    
    public void setBlurRadius(int radius) {
        this.blurRadius = radius;
    }
    
    public void setOverlayColor(int color) {
        this.overlayColor = color;
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
        if (blurEnabled && blurredBitmap != null && !blurredBitmap.isRecycled()) {
            canvas.save();
            
            if (cornerRadius > 0) {
                rectF.set(0, 0, getWidth(), getHeight());
                canvas.drawBitmap(blurredBitmap, null, rectF, blurPaint);
                
                if (overlayColor != 0) {
                    Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    overlayPaint.setColor(overlayColor);
                    canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, overlayPaint);
                }
            } else {
                canvas.drawBitmap(blurredBitmap, 0, 0, blurPaint);
                
                if (overlayColor != 0) {
                    canvas.drawColor(overlayColor);
                }
            }
            
            canvas.restore();
        }
        
        super.dispatchDraw(canvas);
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
