package dev.ui.obscura.gradient;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import dev.ui.obscura.R;

/**
 * GradientLayout - A LinearLayout with animated gradient background.
 * 
 * Children are arranged normally (vertically/horizontally based on orientation).
 * The gradient effect is drawn behind children automatically.
 * 
 * Usage in XML:
 * <dev.ui.obscura.gradient.GradientLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:orientation="vertical"
 *     android:padding="16dp"
 *     app:obscura_gradientStartColor="#6A11CB"
 *     app:obscura_gradientEndColor="#2575FC"
 *     app:obscura_gradientRotating="true"
 *     app:obscura_gradientAutoStart="true">
 *     
 *     <TextView ... />
 *     <Button ... />
 *     
 * </dev.ui.obscura.gradient.GradientLayout>
 */
public class GradientLayout extends LinearLayout {
    
    private AnimatedGradient gradient;
    private boolean autoStart = true;
    
    public GradientLayout(Context context) {
        super(context);
        init(null);
    }
    
    public GradientLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public GradientLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        setWillNotDraw(false);
        
        int startColor = 0xFF6A11CB;
        int endColor = 0xFF2575FC;
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.GradientLayout);
            startColor = a.getColor(R.styleable.GradientLayout_obscura_gradientStartColor, startColor);
            endColor = a.getColor(R.styleable.GradientLayout_obscura_gradientEndColor, endColor);
            
            if (a.hasValue(R.styleable.GradientLayout_obscura_gradientCenterColor)) {
                int centerColor = a.getColor(R.styleable.GradientLayout_obscura_gradientCenterColor, 0);
                gradient = new AnimatedGradient(startColor, centerColor, endColor);
            } else {
                gradient = new AnimatedGradient(startColor, endColor);
            }
            
            gradient.setAngle(a.getFloat(R.styleable.GradientLayout_obscura_gradientAngle, 0f));
            gradient.setRotating(a.getBoolean(R.styleable.GradientLayout_obscura_gradientRotating, false));
            gradient.setDuration(a.getInt(R.styleable.GradientLayout_obscura_gradientDuration, 3000));
            autoStart = a.getBoolean(R.styleable.GradientLayout_obscura_gradientAutoStart, true);
            
            a.recycle();
        } else {
            gradient = new AnimatedGradient(startColor, endColor);
        }
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        gradient.setBounds(0, 0, w, h);
        if (autoStart) {
            gradient.start();
        }
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw gradient background first
        gradient.draw(canvas);
        
        // Then draw children on top (arranged by LinearLayout)
        super.dispatchDraw(canvas);
        
        // Request next frame for animation
        if (gradient.isRotating()) {
            invalidate();
        }
    }
    
    public void setColors(int... colors) {
        gradient.setColors(colors);
        invalidate();
    }
    
    public void setAngle(float angle) {
        gradient.setAngle(angle);
        invalidate();
    }
    
    public void setRotating(boolean rotating) {
        gradient.setRotating(rotating);
        invalidate();
    }
    
    public void start() {
        gradient.start();
        invalidate();
    }
    
    public void stop() {
        gradient.stop();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        gradient.stop();
    }
}
