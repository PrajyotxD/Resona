package dev.ui.obscura.blur;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

public class BlurDrawable extends Drawable {
    
    private Paint paint;
    private BitmapShader shader;
    private Matrix matrix;
    private Bitmap blurredBitmap;
    private int tintColor = 0x30000000;
    private RectF bounds = new RectF();
    private float cornerRadius = 0f;
    
    public BlurDrawable() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        matrix = new Matrix();
    }
    
    public void setBlurredBitmap(Bitmap bitmap) {
        if (blurredBitmap != null && blurredBitmap != bitmap && !blurredBitmap.isRecycled()) {
            blurredBitmap.recycle();
        }
        this.blurredBitmap = bitmap;
        if (bitmap != null) {
            shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            paint.setShader(shader);
        } else {
            shader = null;
            paint.setShader(null);
        }
        invalidateSelf();
    }
    
    public void setTintColor(int color) {
        this.tintColor = color;
        invalidateSelf();
    }
    
    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        invalidateSelf();
    }
    
    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        bounds.set(left, top, right, bottom);
        updateMatrix();
    }
    
    private void updateMatrix() {
        if (blurredBitmap == null || bounds.isEmpty()) {
            return;
        }
        
        matrix.reset();
        float scaleX = bounds.width() / blurredBitmap.getWidth();
        float scaleY = bounds.height() / blurredBitmap.getHeight();
        matrix.setScale(scaleX, scaleY);
        matrix.postTranslate(bounds.left, bounds.top);
        
        if (shader != null) {
            shader.setLocalMatrix(matrix);
        }
    }
    
    @Override
    public void draw(Canvas canvas) {
        if (blurredBitmap == null || blurredBitmap.isRecycled()) {
            return;
        }
        
        Rect b = getBounds();
        if (cornerRadius > 0) {
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint);
            if (tintColor != 0) {
                Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                tintPaint.setColor(tintColor);
                canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, tintPaint);
            }
        } else {
            canvas.drawRect(b, paint);
            if (tintColor != 0) {
                Paint tintPaint = new Paint();
                tintPaint.setColor(tintColor);
                canvas.drawRect(b, tintPaint);
            }
        }
    }
    
    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }
    
    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }
    
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
