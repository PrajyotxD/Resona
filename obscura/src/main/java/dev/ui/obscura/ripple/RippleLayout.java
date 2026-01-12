package dev.ui.obscura.ripple;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import dev.ui.obscura.R;

/**
 * RippleLayout - A LinearLayout with touch ripple effect.
 * 
 * Children are arranged normally (vertically/horizontally based on orientation).
 * The ripple effect is drawn over children on touch.
 * 
 * Usage in XML:
 * <dev.ui.obscura.ripple.RippleLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:orientation="vertical"
 *     app:obscura_rippleColor="#40FFFFFF"
 *     app:obscura_rippleDuration="400">
 *     
 *     <TextView ... />
 *     <Button ... />
 *     
 * </dev.ui.obscura.ripple.RippleLayout>
 */
public class RippleLayout extends LinearLayout {
    
    private RippleEffect rippleEffect;
    private int rippleColor = 0x40FFFFFF;
    private int rippleDuration = 400;
    
    public RippleLayout(Context context) {
        super(context);
        init(null);
    }
    
    public RippleLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public RippleLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        setWillNotDraw(false);
        setClickable(true);
        
        rippleEffect = new RippleEffect();
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.RippleLayout);
            rippleColor = a.getColor(R.styleable.RippleLayout_obscura_rippleColor, 0x40FFFFFF);
            rippleDuration = a.getInt(R.styleable.RippleLayout_obscura_rippleDuration, 400);
            a.recycle();
        }
        
        rippleEffect.setColor(rippleColor);
        rippleEffect.setDuration(rippleDuration);
    }
    
    public void setRippleColor(int color) {
        this.rippleColor = color;
        rippleEffect.setColor(color);
    }
    
    public void setRippleDuration(int duration) {
        this.rippleDuration = duration;
        rippleEffect.setDuration(duration);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rippleEffect.setBounds(0, 0, w, h);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            rippleEffect.start(event.getX(), event.getY());
            invalidate();
        }
        return super.onTouchEvent(event);
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw children first (arranged by LinearLayout)
        super.dispatchDraw(canvas);
        
        // Draw ripple effect on top
        if (rippleEffect.isAnimating()) {
            rippleEffect.draw(canvas);
            invalidate();
        }
    }
}
