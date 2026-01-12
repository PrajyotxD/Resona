package dev.ui.obscura.loading;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public class LoadingIndicator {
    
    private Paint paint;
    private RectF bounds = new RectF();
    
    private float startAngle = 0f;
    private float sweepAngle = 90f;
    private float rotation = 0f;
    
    private int color;
    private float strokeWidth = 8f;
    private long lastUpdateTime = 0;
    
    public LoadingIndicator() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }
    
    public LoadingIndicator setColor(int color) {
        this.color = color;
        paint.setColor(color);
        return this;
    }
    
    public LoadingIndicator setStrokeWidth(float width) {
        this.strokeWidth = width;
        paint.setStrokeWidth(width);
        return this;
    }
    
    public void setBounds(float left, float top, float right, float bottom) {
        bounds.set(left + strokeWidth, top + strokeWidth, right - strokeWidth, bottom - strokeWidth);
    }
    
    public void draw(Canvas canvas) {
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime != 0) {
            float delta = (currentTime - lastUpdateTime) / 1000f;
            rotation += delta * 360f;
            
            if (rotation >= 360f) {
                rotation -= 360f;
            }
        }
        lastUpdateTime = currentTime;
        
        canvas.drawArc(bounds, startAngle + rotation, sweepAngle, false, paint);
    }
}
