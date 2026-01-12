package dev.ui.obscura.ripple;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.animation.DecelerateInterpolator;

public class RippleEffect {
    
    private Paint ripplePaint;
    private float centerX, centerY;
    private float maxRadius;
    private float currentRadius = 0f;
    private int rippleColor;
    private ValueAnimator animator;
    
    public RippleEffect() {
        ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ripplePaint.setStyle(Paint.Style.STROKE);
    }
    
    public RippleEffect setColor(int color) {
        this.rippleColor = color;
        return this;
    }
    
    public RippleEffect setStrokeWidth(float width) {
        ripplePaint.setStrokeWidth(width);
        return this;
    }
    
    private int duration = 400;
    private int boundsWidth, boundsHeight;
    
    public void setDuration(int duration) {
        this.duration = duration;
    }
    
    public void setBounds(int left, int top, int right, int bottom) {
        this.boundsWidth = right - left;
        this.boundsHeight = bottom - top;
    }
    
    public void start(float x, float y) {
        float radius = (float) Math.sqrt(boundsWidth * boundsWidth + boundsHeight * boundsHeight);
        start(x, y, radius, duration);
    }
    
    public boolean isAnimating() {
        return animator != null && animator.isRunning();
    }

    public void start(float x, float y, float radius, long duration) {
        this.centerX = x;
        this.centerY = y;
        this.maxRadius = radius;
        
        if (animator != null) {
            animator.cancel();
        }
        
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float progress = (float) animation.getAnimatedValue();
                currentRadius = maxRadius * progress;
                ripplePaint.setAlpha((int) (255 * (1f - progress)));
            }
        });
        animator.start();
    }
    
    public void draw(Canvas canvas) {
        if (currentRadius > 0 && currentRadius <= maxRadius) {
            ripplePaint.setColor(rippleColor);
            canvas.drawCircle(centerX, centerY, currentRadius, ripplePaint);
        }
    }
    
    public boolean isRunning() {
        return animator != null && animator.isRunning();
    }
}
