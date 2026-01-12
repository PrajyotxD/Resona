package dev.ui.obscura.shimmer;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;

public class ShimmerEffect {
    
    private Paint shimmerPaint;
    private LinearGradient gradient;
    private Matrix gradientMatrix;
    
    private int baseColor = 0xFFE0E0E0;
    private int highlightColor = 0xFFF5F5F5;
    private float shimmerAngle = 20f;
    private float shimmerWidth = 0.3f;
    private long duration = 1500;
    
    private long startTime = 0;
    private Rect bounds = new Rect();
    
    public ShimmerEffect() {
        shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradientMatrix = new Matrix();
    }
    
    public ShimmerEffect setBaseColor(int color) {
        this.baseColor = color;
        updateGradient();
        return this;
    }
    
    public ShimmerEffect setHighlightColor(int color) {
        this.highlightColor = color;
        updateGradient();
        return this;
    }
    
    public ShimmerEffect setAngle(float angle) {
        this.shimmerAngle = angle;
        updateGradient();
        return this;
    }
    
    public ShimmerEffect setWidth(float width) {
        this.shimmerWidth = Math.max(0.1f, Math.min(1f, width));
        updateGradient();
        return this;
    }
    
    public ShimmerEffect setDuration(long duration) {
        this.duration = duration;
        return this;
    }
    
    public void setBounds(Rect bounds) {
        this.bounds.set(bounds);
        updateGradient();
    }
    
    public void setBounds(int left, int top, int right, int bottom) {
        this.bounds.set(left, top, right, bottom);
        updateGradient();
    }
    
    private void updateGradient() {
        if (bounds.isEmpty()) {
            return;
        }
        
        float width = bounds.width();
        float shimmerSize = width * shimmerWidth;
        
        gradient = new LinearGradient(
            0, 0, shimmerSize, 0,
            new int[]{baseColor, highlightColor, baseColor},
            new float[]{0f, 0.5f, 1f},
            Shader.TileMode.CLAMP
        );
        
        shimmerPaint.setShader(gradient);
    }
    
    public void start() {
        startTime = System.currentTimeMillis();
    }
    
    public void draw(Canvas canvas) {
        if (gradient == null || bounds.isEmpty()) {
            return;
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        float progress = (elapsed % duration) / (float) duration;
        
        float width = bounds.width();
        float shimmerSize = width * shimmerWidth;
        float translateX = -shimmerSize + (width + shimmerSize) * progress;
        
        gradientMatrix.reset();
        gradientMatrix.setRotate(shimmerAngle, 0, 0);
        gradientMatrix.postTranslate(translateX, 0);
        gradient.setLocalMatrix(gradientMatrix);
        
        canvas.drawRect(bounds, shimmerPaint);
    }
}
