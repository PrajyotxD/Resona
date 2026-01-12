package dev.ui.obscura.wave;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

public class WaveEffect {
    
    private Paint wavePaint;
    private Path wavePath;
    
    private float amplitude = 20f;
    private float frequency = 1f;
    private float speed = 1f;
    private int waveColor;
    
    private float phase = 0f;
    private long lastUpdateTime = 0;
    
    private int width, height;
    
    public WaveEffect() {
        wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePath = new Path();
    }
    
    public WaveEffect setAmplitude(float amplitude) {
        this.amplitude = amplitude;
        return this;
    }
    
    public WaveEffect setFrequency(float frequency) {
        this.frequency = frequency;
        return this;
    }
    
    public WaveEffect setSpeed(float speed) {
        this.speed = speed;
        return this;
    }
    
    public WaveEffect setColor(int color) {
        this.waveColor = color;
        wavePaint.setColor(color);
        return this;
    }
    
    public WaveEffect setStrokeWidth(float width) {
        wavePaint.setStrokeWidth(width);
        return this;
    }
    
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }
    
    public void setBounds(int left, int top, int right, int bottom) {
        this.width = right - left;
        this.height = bottom - top;
    }
    
    private boolean running = false;
    
    public void start() {
        running = true;
        lastUpdateTime = System.currentTimeMillis();
    }
    
    public void stop() {
        running = false;
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public void draw(Canvas canvas) {
        draw(canvas, height / 2f);
    }

    public void draw(Canvas canvas, float baselineY) {
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime != 0) {
            float delta = (currentTime - lastUpdateTime) / 1000f;
            phase += speed * delta * 2f * (float) Math.PI;
        }
        lastUpdateTime = currentTime;
        
        wavePath.reset();
        
        float step = 5f;
        boolean firstPoint = true;
        
        for (float x = 0; x <= width; x += step) {
            float y = baselineY + amplitude * (float) Math.sin(frequency * x * 2f * Math.PI / width + phase);
            
            if (firstPoint) {
                wavePath.moveTo(x, y);
                firstPoint = false;
            } else {
                wavePath.lineTo(x, y);
            }
        }
        
        canvas.drawPath(wavePath, wavePaint);
    }
}
