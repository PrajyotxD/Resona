package dev.ui.obscura.reveal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RevealDrawable extends Drawable {
    
    private static final int DEFAULT_PARTICLE_COUNT = 80;
    private static final float PARTICLE_BASE_SIZE = 2.5f;
    private static final float PARTICLE_SPEED = 50f;
    
    private List<Particle> particles = new ArrayList<>();
    private Paint particlePaint;
    private Paint coverPaint;
    private Random random = new Random();
    
    private int particleColor = Color.WHITE;
    private int backgroundColor = Color.LTGRAY;
    private boolean revealed = false;
    
    private ValueAnimator revealAnimator;
    private float revealProgress = 0f;
    private float revealCenterX, revealCenterY, revealRadius;
    
    private long lastUpdateTime = 0;
    private Rect bounds = new Rect();
    
    public RevealDrawable() {
        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }
    
    public void setParticleColor(int color) {
        this.particleColor = color;
    }
    
    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
        coverPaint.setColor(color);
    }
    
    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        bounds.set(left, top, right, bottom);
        initializeParticles();
    }
    
    private void initializeParticles() {
        particles.clear();
        
        int width = bounds.width();
        int height = bounds.height();
        
        if (width <= 0 || height <= 0) {
            return;
        }
        
        for (int i = 0; i < DEFAULT_PARTICLE_COUNT; i++) {
            Particle p = new Particle();
            p.x = bounds.left + random.nextFloat() * width;
            p.y = bounds.top + random.nextFloat() * height;
            p.vx = (random.nextFloat() - 0.5f) * PARTICLE_SPEED;
            p.vy = (random.nextFloat() - 0.5f) * PARTICLE_SPEED;
            p.size = PARTICLE_BASE_SIZE * (0.5f + random.nextFloat() * 0.5f);
            p.alpha = 0.4f + random.nextFloat() * 0.6f;
            particles.add(p);
        }
    }
    
    @Override
    public void draw(Canvas canvas) {
        if (revealed && revealProgress >= 1f) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime == 0) {
            lastUpdateTime = currentTime;
        }
        float deltaTime = (currentTime - lastUpdateTime) / 1000f;
        lastUpdateTime = currentTime;
        
        canvas.drawRect(bounds, coverPaint);
        
        for (Particle p : particles) {
            if (revealed) {
                float dx = p.x - revealCenterX;
                float dy = p.y - revealCenterY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                
                if (dist < revealRadius * revealProgress) {
                    continue;
                }
            }
            
            p.x += p.vx * deltaTime;
            p.y += p.vy * deltaTime;
            
            if (p.x < bounds.left) p.x = bounds.right;
            if (p.x > bounds.right) p.x = bounds.left;
            if (p.y < bounds.top) p.y = bounds.bottom;
            if (p.y > bounds.bottom) p.y = bounds.top;
            
            particlePaint.setColor(particleColor);
            particlePaint.setAlpha((int) (p.alpha * 255));
            canvas.drawCircle(p.x, p.y, p.size, particlePaint);
        }
        
        if (!revealed || revealProgress < 1f) {
            invalidateSelf();
        }
    }
    
    public void startReveal(float x, float y, float radius, final Runnable onComplete) {
        revealed = true;
        revealCenterX = x;
        revealCenterY = y;
        revealRadius = radius;
        
        if (revealAnimator != null) {
            revealAnimator.cancel();
        }
        
        revealAnimator = ValueAnimator.ofFloat(0f, 1f);
        revealAnimator.setDuration(400);
        revealAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                revealProgress = (float) animation.getAnimatedValue();
                invalidateSelf();
            }
        });
        
        if (onComplete != null) {
            revealAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
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
        initializeParticles();
        invalidateSelf();
    }
    
    public boolean isRevealed() {
        return revealed && revealProgress >= 1f;
    }
    
    @Override
    public void setAlpha(int alpha) {
        coverPaint.setAlpha(alpha);
    }
    
    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        particlePaint.setColorFilter(colorFilter);
    }
    
    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
    
    private static class Particle {
        float x, y;
        float vx, vy;
        float size;
        float alpha;
    }
}
