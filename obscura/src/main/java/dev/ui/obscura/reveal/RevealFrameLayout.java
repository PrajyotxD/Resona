package dev.ui.obscura.reveal;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import dev.ui.obscura.R;

public class RevealFrameLayout extends FrameLayout {
    
    private RevealDrawable revealDrawable;
    private boolean revealOnTouch = true;
    private OnRevealListener revealListener;
    
    public interface OnRevealListener {
        void onRevealStarted();
        void onRevealCompleted();
    }
    
    public RevealFrameLayout(Context context) {
        super(context);
        init(null);
    }
    
    public RevealFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public RevealFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        revealDrawable = new RevealDrawable();
        setWillNotDraw(false);
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.RevealFrameLayout);
            revealOnTouch = a.getBoolean(R.styleable.RevealFrameLayout_obscura_revealOnTouch, true);
            
            int particleColor = a.getColor(R.styleable.RevealFrameLayout_obscura_revealParticleColor, Color.WHITE);
            revealDrawable.setParticleColor(particleColor);
            
            int bgColor = a.getColor(R.styleable.RevealFrameLayout_obscura_revealBackgroundColor, Color.LTGRAY);
            revealDrawable.setBackgroundColor(bgColor);
            
            a.recycle();
        }
    }
    
    public void setRevealOnTouch(boolean enabled) {
        this.revealOnTouch = enabled;
    }
    
    public void setParticleColor(int color) {
        revealDrawable.setParticleColor(color);
    }
    
    public void setRevealBackgroundColor(int color) {
        revealDrawable.setBackgroundColor(color);
    }
    
    public void setOnRevealListener(OnRevealListener listener) {
        this.revealListener = listener;
    }
    
    public void startReveal(float x, float y) {
        float radius = (float) Math.sqrt(getWidth() * getWidth() + getHeight() * getHeight());
        
        if (revealListener != null) {
            revealListener.onRevealStarted();
        }
        
        revealDrawable.startReveal(x, y, radius, new Runnable() {
            @Override
            public void run() {
                if (revealListener != null) {
                    revealListener.onRevealCompleted();
                }
            }
        });
    }
    
    public void reset() {
        revealDrawable.reset();
    }
    
    public boolean isRevealed() {
        return revealDrawable.isRevealed();
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        revealDrawable.setBounds(0, 0, w, h);
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        
        if (!revealDrawable.isRevealed()) {
            revealDrawable.draw(canvas);
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (revealOnTouch && event.getAction() == MotionEvent.ACTION_DOWN) {
            if (!revealDrawable.isRevealed()) {
                startReveal(event.getX(), event.getY());
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
