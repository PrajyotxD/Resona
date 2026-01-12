package dev.ui.obscura.reveal;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RevealEffect {
    
    private static final int PARTICLE_COUNT_PER_CHAR = 20;
    private static final int MAX_PARTICLES = 120;
    private static final float PARTICLE_SIZE = 3.5f;
    private static final int FPS = 30;
    
    private List<Dot> dots = new ArrayList<>();
    private Random random = new Random();
    private Paint dotPaint;
    private int baseColor = Color.WHITE;
    private boolean revealed = false;
    private long lastFrameTime = 0;
    
    private ValueAnimator revealAnimator;
    private float revealProgress = 0f;
    private float centerX, centerY, maxRadius;
    
    public RevealEffect() {
        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);
    }
    
    public void setColor(int color) {
        this.baseColor = color;
    }
    
    public void setBounds(Rect bounds) {
        int targetCount = Math.min(MAX_PARTICLES, 
            (bounds.width() * bounds.height() / 1000) * PARTICLE_COUNT_PER_CHAR / 100);
        
        while (dots.size() < targetCount) {
            Dot dot = new Dot();
            dot.x = bounds.left + random.nextFloat() * bounds.width();
            dot.y = bounds.top + random.nextFloat() * bounds.height();
            dot.vx = (random.nextFloat() - 0.5f) * 2f;
            dot.vy = (random.nextFloat() - 0.5f) * 2f;
            dot.alpha = 0.3f + random.nextFloat() * 0.7f;
            dot.size = PARTICLE_SIZE * (0.6f + random.nextFloat() * 0.4f);
            dots.add(dot);
        }
        
        while (dots.size() > targetCount) {
            dots.remove(dots.size() - 1);
        }
    }
    
    public void draw(Canvas canvas, Rect bounds) {
        long currentTime = System.currentTimeMillis();
        if (lastFrameTime == 0) {
            lastFrameTime = currentTime;
        }
        
        float delta = Math.min((currentTime - lastFrameTime) / 1000f, 0.1f);
        lastFrameTime = currentTime;
        
        if (revealed && revealProgress >= 1f) {
            return;
        }
        
        for (Dot dot : dots) {
            if (revealed) {
                float dx = dot.x - centerX;
                float dy = dot.y - centerY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                
                if (dist < maxRadius * revealProgress) {
                    continue;
                }
            }
            
            dot.x += dot.vx * delta * 60f;
            dot.y += dot.vy * delta * 60f;
            
            if (dot.x < bounds.left) {
                dot.x = bounds.right;
            } else if (dot.x > bounds.right) {
                dot.x = bounds.left;
            }
            
            if (dot.y < bounds.top) {
                dot.y = bounds.bottom;
            } else if (dot.y > bounds.bottom) {
                dot.y = bounds.top;
            }
            
            dotPaint.setColor(baseColor);
            dotPaint.setAlpha((int) (dot.alpha * 255));
            canvas.drawCircle(dot.x, dot.y, dot.size, dotPaint);
        }
    }
    
    public void startReveal(float x, float y, float radius, Runnable onComplete) {
        revealed = true;
        centerX = x;
        centerY = y;
        maxRadius = radius;
        revealProgress = 0f;
        
        if (revealAnimator != null) {
            revealAnimator.cancel();
        }
        
        revealAnimator = ValueAnimator.ofFloat(0f, 1f);
        revealAnimator.setDuration((long) (300 + radius * 0.5f));
        revealAnimator.setInterpolator(new LinearInterpolator());
        revealAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                revealProgress = (float) animation.getAnimatedValue();
            }
        });
        if (onComplete != null) {
            revealAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    onComplete.run();
                }
            });
        }
        revealAnimator.start();
    }
    
    public void reset() {
        revealed = false;
        revealProgress = 0f;
        if (revealAnimator != null) {
            revealAnimator.cancel();
        }
    }
    
    public boolean isRevealed() {
        return revealed && revealProgress >= 1f;
    }
    
    public float getRevealProgress() {
        return revealProgress;
    }
    
    // Methods required by RevealLayout
    private int particleColor = 0xFFFFFFFF;
    private OnRevealCompleteListener listener;
    private android.graphics.Rect mBounds = new android.graphics.Rect();
    
    public interface OnRevealCompleteListener {
        void onRevealComplete();
    }
    
    public void setParticleColor(int color) {
        this.particleColor = color;
        this.baseColor = color;
    }
    
    public void setOnRevealCompleteListener(OnRevealCompleteListener listener) {
        this.listener = listener;
    }
    
    public void start(float x, float y) {
        float radius = (float) Math.sqrt(mBounds.width() * mBounds.width() + mBounds.height() * mBounds.height());
        startReveal(x, y, radius, listener != null ? () -> listener.onRevealComplete() : null);
    }
    
    public void setBounds(int left, int top, int right, int bottom) {
        mBounds.set(left, top, right, bottom);
        setBounds(mBounds);
    }
    
    public boolean isAnimating() {
        return revealAnimator != null && revealAnimator.isRunning();
    }
    
    public void draw(Canvas canvas) {
        draw(canvas, mBounds);
    }
    
    public void drawIdleParticles(Canvas canvas) {
        // Draw slow-moving particles when not animating
        if (!revealed) {
            draw(canvas, mBounds);
        }
    }
    
    public void stop() {
        if (revealAnimator != null) {
            revealAnimator.cancel();
        }
    }
    
    private static class Dot {
        float x, y;
        float vx, vy;
        float alpha;
        float size;
    }
}
