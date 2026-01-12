package dev.ui.obscura.gradient;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;

public class AnimatedGradient {
    
    private Paint gradientPaint;
    private LinearGradient gradient;
    private Matrix gradientMatrix;
    
    private int[] colors;
    private float[] positions;
    private float angle = 0f;
    private long duration = 3000;
    private boolean rotating = false;
    
    private long startTime = 0;
    private Rect bounds = new Rect();
    
    public AnimatedGradient(int... colors) {
        this.colors = colors;
        generatePositions();
        gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradientMatrix = new Matrix();
    }
    
    private void generatePositions() {
        positions = new float[colors.length];
        for (int i = 0; i < colors.length; i++) {
            positions[i] = i / (float) (colors.length - 1);
        }
    }
    
    public AnimatedGradient setColors(int... colors) {
        this.colors = colors;
        generatePositions();
        updateGradient();
        return this;
    }
    
    public AnimatedGradient setAngle(float angle) {
        this.angle = angle;
        updateGradient();
        return this;
    }
    
    public AnimatedGradient setDuration(long duration) {
        this.duration = duration;
        return this;
    }
    
    public AnimatedGradient setRotating(boolean rotating) {
        this.rotating = rotating;
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
        if (bounds.isEmpty() || colors == null || colors.length < 2) {
            return;
        }
        
        float diagonal = (float) Math.sqrt(bounds.width() * bounds.width() + bounds.height() * bounds.height());
        
        gradient = new LinearGradient(
            0, 0, diagonal, 0,
            colors, positions,
            Shader.TileMode.CLAMP
        );
        
        gradientPaint.setShader(gradient);
    }
    
    public void start() {
        startTime = System.currentTimeMillis();
    }
    
    public void stop() {
        // No animation to stop, but reset state
        startTime = 0;
    }
    
    public boolean isRotating() {
        return rotating;
    }
    
    public void draw(Canvas canvas) {
        if (gradient == null || bounds.isEmpty()) {
            return;
        }
        
        float currentAngle = angle;
        
        if (rotating) {
            long elapsed = System.currentTimeMillis() - startTime;
            float progress = (elapsed % duration) / (float) duration;
            currentAngle = progress * 360f;
        }
        
        gradientMatrix.reset();
        gradientMatrix.setRotate(currentAngle, bounds.centerX(), bounds.centerY());
        gradientMatrix.postTranslate(bounds.left, bounds.top);
        gradient.setLocalMatrix(gradientMatrix);
        
        canvas.drawRect(bounds, gradientPaint);
    }
}
