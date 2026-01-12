package dev.ui.obscura.gradient;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import dev.ui.obscura.R;

public class GradientView extends View {
    
    private AnimatedGradient gradient;
    private boolean autoStart = true;
    
    public GradientView(Context context) {
        super(context);
        init(null);
    }
    
    public GradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public GradientView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        setWillNotDraw(false);
        
        int startColor = 0xFF6A11CB;
        int endColor = 0xFF2575FC;
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.GradientView);
            startColor = a.getColor(R.styleable.GradientView_obscura_gradientStartColor, startColor);
            endColor = a.getColor(R.styleable.GradientView_obscura_gradientEndColor, endColor);
            
            if (a.hasValue(R.styleable.GradientView_obscura_gradientCenterColor)) {
                int centerColor = a.getColor(R.styleable.GradientView_obscura_gradientCenterColor, 0);
                gradient = new AnimatedGradient(startColor, centerColor, endColor);
            } else {
                gradient = new AnimatedGradient(startColor, endColor);
            }
            
            gradient.setAngle(a.getFloat(R.styleable.GradientView_obscura_gradientAngle, 0f));
            gradient.setRotating(a.getBoolean(R.styleable.GradientView_obscura_gradientRotating, false));
            gradient.setDuration(a.getInt(R.styleable.GradientView_obscura_gradientDuration, 3000));
            autoStart = a.getBoolean(R.styleable.GradientView_obscura_gradientAutoStart, true);
            
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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        gradient.draw(canvas);
        invalidate();
    }
    
    public void setColors(int... colors) {
        gradient.setColors(colors);
        invalidate();
    }
    
    public void setRotating(boolean rotating) {
        gradient.setRotating(rotating);
        invalidate();
    }
}
