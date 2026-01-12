package dev.ui.obscura.wave;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import dev.ui.obscura.R;

/**
 * WaveLayout - A LinearLayout with animated wave effect as background.
 * 
 * Children are arranged normally (vertically/horizontally based on orientation).
 * The wave effect is drawn behind children automatically.
 * 
 * Usage in XML:
 * <dev.ui.obscura.wave.WaveLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:orientation="vertical"
 *     app:obscura_waveColor="#2196F3"
 *     app:obscura_waveAmplitude="20dp"
 *     app:obscura_waveAutoStart="true">
 *     
 *     <TextView ... />
 *     
 * </dev.ui.obscura.wave.WaveLayout>
 */
public class WaveLayout extends LinearLayout {
    
    private WaveEffect waveEffect;
    private boolean autoStart = true;
    
    public WaveLayout(Context context) {
        super(context);
        init(null);
    }
    
    public WaveLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public WaveLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        setWillNotDraw(false);
        waveEffect = new WaveEffect();
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.WaveLayout);
            
            float amplitude = a.getDimension(R.styleable.WaveLayout_obscura_waveAmplitude, 30f);
            float frequency = a.getFloat(R.styleable.WaveLayout_obscura_waveFrequency, 1.5f);
            float speed = a.getFloat(R.styleable.WaveLayout_obscura_waveSpeed, 1f);
            int color = a.getColor(R.styleable.WaveLayout_obscura_waveColor, 0xFF2196F3);
            float strokeWidth = a.getDimension(R.styleable.WaveLayout_obscura_waveStrokeWidth, 8f);
            autoStart = a.getBoolean(R.styleable.WaveLayout_obscura_waveAutoStart, true);
            
            waveEffect.setAmplitude(amplitude);
            waveEffect.setFrequency(frequency);
            waveEffect.setSpeed(speed);
            waveEffect.setColor(color);
            waveEffect.setStrokeWidth(strokeWidth);
            
            a.recycle();
        }
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        waveEffect.setBounds(0, 0, w, h);
        if (autoStart) {
            waveEffect.start();
        }
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw wave background first
        waveEffect.draw(canvas);
        
        // Then draw children on top (arranged by LinearLayout)
        super.dispatchDraw(canvas);
        
        // Request next frame for animation
        if (waveEffect.isRunning()) {
            invalidate();
        }
    }
    
    public void setWaveColor(int color) {
        waveEffect.setColor(color);
        invalidate();
    }
    
    public void setAmplitude(float amplitude) {
        waveEffect.setAmplitude(amplitude);
        invalidate();
    }
    
    public void setFrequency(float frequency) {
        waveEffect.setFrequency(frequency);
        invalidate();
    }
    
    public void setSpeed(float speed) {
        waveEffect.setSpeed(speed);
    }
    
    public void start() {
        waveEffect.start();
        invalidate();
    }
    
    public void stop() {
        waveEffect.stop();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        waveEffect.stop();
    }
}
