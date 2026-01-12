package dev.ui.obscura.reveal;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import dev.ui.obscura.R;

/**
 * RevealLayout - A LinearLayout with reveal/spoiler effect overlay.
 * 
 * Children are arranged normally (vertically/horizontally based on orientation).
 * The reveal effect is drawn over children, hiding them until user taps.
 * 
 * Usage in XML:
 * <dev.ui.obscura.reveal.RevealLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:orientation="vertical"
 *     app:obscura_revealOnTouch="true"
 *     app:obscura_revealBackgroundColor="#808080">
 *     
 *     <!-- Hidden content -->
 *     <ImageView ... />
 *     <TextView ... />
 *     
 * </dev.ui.obscura.reveal.RevealLayout>
 */
public class RevealLayout extends LinearLayout {
    
    private RevealEffect revealEffect;
    private Paint coverPaint;
    private int coverColor = 0xFF808080;
    private int particleColor = 0xFFFFFFFF;
    private boolean revealed = false;
    private boolean revealOnTouch = true;
    private float cornerRadius = 0f;
    
    private RectF rectF = new RectF();
    private Path clipPath = new Path();
    
    public RevealLayout(Context context) {
        super(context);
        init(null);
    }
    
    public RevealLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public RevealLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        setWillNotDraw(false);
        
        coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        revealEffect = new RevealEffect();
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.RevealLayout);
            coverColor = a.getColor(R.styleable.RevealLayout_obscura_revealBackgroundColor, 0xFF808080);
            particleColor = a.getColor(R.styleable.RevealLayout_obscura_revealParticleColor, 0xFFFFFFFF);
            revealOnTouch = a.getBoolean(R.styleable.RevealLayout_obscura_revealOnTouch, true);
            cornerRadius = a.getDimension(R.styleable.RevealLayout_obscura_cornerRadius, 0f);
            a.recycle();
        }
        
        coverPaint.setColor(coverColor);
        revealEffect.setParticleColor(particleColor);
        
        revealEffect.setOnRevealCompleteListener(new RevealEffect.OnRevealCompleteListener() {
            @Override
            public void onRevealComplete() {
                revealed = true;
                invalidate();
            }
        });
    }
    
    public void setCoverColor(int color) {
        this.coverColor = color;
        coverPaint.setColor(color);
        invalidate();
    }
    
    public void setParticleColor(int color) {
        this.particleColor = color;
        revealEffect.setParticleColor(color);
    }
    
    public void setRevealOnTouch(boolean enabled) {
        this.revealOnTouch = enabled;
    }
    
    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        invalidate();
    }
    
    public void reveal() {
        if (!revealed) {
            revealEffect.start(getWidth() / 2f, getHeight() / 2f);
            invalidate();
        }
    }
    
    public void reveal(float x, float y) {
        if (!revealed) {
            revealEffect.start(x, y);
            invalidate();
        }
    }
    
    public void hide() {
        revealed = false;
        revealEffect.reset();
        invalidate();
    }
    
    public boolean isRevealed() {
        return revealed;
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        revealEffect.setBounds(0, 0, w, h);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (revealOnTouch && !revealed && event.getAction() == MotionEvent.ACTION_DOWN) {
            reveal(event.getX(), event.getY());
            return true;
        }
        return super.onTouchEvent(event);
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw children first (arranged by LinearLayout)
        super.dispatchDraw(canvas);
        
        // Draw cover/reveal effect on top if not revealed
        if (!revealed) {
            drawCoverEffect(canvas);
        }
    }
    
    private void drawCoverEffect(Canvas canvas) {
        canvas.save();
        
        rectF.set(0, 0, getWidth(), getHeight());
        
        // Apply corner radius clipping
        if (cornerRadius > 0) {
            clipPath.reset();
            clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);
        }
        
        if (revealEffect.isAnimating()) {
            // Draw reveal animation
            revealEffect.draw(canvas);
            invalidate();
        } else {
            // Draw solid cover
            if (cornerRadius > 0) {
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, coverPaint);
            } else {
                canvas.drawRect(rectF, coverPaint);
            }
            
            // Draw particles for "alive" look
            revealEffect.drawIdleParticles(canvas);
            invalidate();
        }
        
        canvas.restore();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        revealEffect.stop();
    }
}
