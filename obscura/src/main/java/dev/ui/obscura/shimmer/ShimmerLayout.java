package dev.ui.obscura.shimmer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import dev.ui.obscura.R;

/**
 * ShimmerLayout - A LinearLayout with shimmer loading effect.
 * 
 * Children are arranged normally (vertically/horizontally based on orientation).
 * The shimmer effect is drawn over children automatically.
 * 
 * Usage in XML:
 * <dev.ui.obscura.shimmer.ShimmerLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:orientation="vertical"
 *     app:obscura_shimmerAutoStart="true"
 *     app:obscura_shimmerBaseColor="#E0E0E0"
 *     app:obscura_shimmerHighlightColor="#F5F5F5">
 *     
 *     <!-- Placeholder views -->
 *     <View android:layout_width="match_parent" android:layout_height="100dp" android:background="#E0E0E0" />
 *     
 * </dev.ui.obscura.shimmer.ShimmerLayout>
 */
public class ShimmerLayout extends LinearLayout {
    
    private ShimmerEffect shimmerEffect;
    private boolean shimmerEnabled = true;
    private boolean autoStart = true;
    
    public ShimmerLayout(Context context) {
        super(context);
        init(null);
    }
    
    public ShimmerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public ShimmerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        setWillNotDraw(false);
        shimmerEffect = new ShimmerEffect();
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ShimmerLayout);
            shimmerEnabled = a.getBoolean(R.styleable.ShimmerLayout_obscura_shimmerEnabled, true);
            autoStart = a.getBoolean(R.styleable.ShimmerLayout_obscura_shimmerAutoStart, true);
            
            if (a.hasValue(R.styleable.ShimmerLayout_obscura_shimmerBaseColor)) {
                shimmerEffect.setBaseColor(a.getColor(R.styleable.ShimmerLayout_obscura_shimmerBaseColor, 0xFFE0E0E0));
            }
            if (a.hasValue(R.styleable.ShimmerLayout_obscura_shimmerHighlightColor)) {
                shimmerEffect.setHighlightColor(a.getColor(R.styleable.ShimmerLayout_obscura_shimmerHighlightColor, 0xFFF5F5F5));
            }
            shimmerEffect.setAngle(a.getFloat(R.styleable.ShimmerLayout_obscura_shimmerAngle, 20f));
            shimmerEffect.setWidth(a.getFloat(R.styleable.ShimmerLayout_obscura_shimmerWidth, 0.3f));
            shimmerEffect.setDuration(a.getInt(R.styleable.ShimmerLayout_obscura_shimmerDuration, 1500));
            
            a.recycle();
        }
    }
    
    public void setShimmerEnabled(boolean enabled) {
        this.shimmerEnabled = enabled;
        if (!enabled) {
            invalidate();
        }
    }
    
    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }
    
    public void setBaseColor(int color) {
        shimmerEffect.setBaseColor(color);
    }
    
    public void setHighlightColor(int color) {
        shimmerEffect.setHighlightColor(color);
    }
    
    public void setShimmerAngle(float angle) {
        shimmerEffect.setAngle(angle);
    }
    
    public void setShimmerWidth(float width) {
        shimmerEffect.setWidth(width);
    }
    
    public void setDuration(long duration) {
        shimmerEffect.setDuration(duration);
    }
    
    public void start() {
        shimmerEffect.start();
        invalidate();
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        shimmerEffect.setBounds(0, 0, w, h);
        if (autoStart) {
            start();
        }
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        
        if (shimmerEnabled) {
            shimmerEffect.draw(canvas);
            invalidate();
        }
    }
}
