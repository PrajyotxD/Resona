package dev.ui.obscura.wave;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;

import dev.ui.obscura.R;

public class WaveView extends View {
    
    private WaveEffect waveEffect;
    private boolean autoStart = true;
    
    public WaveView(Context context) {
        super(context);
        init(null);
    }
    
    public WaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public WaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        setWillNotDraw(false);
        waveEffect = new WaveEffect();
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.WaveView);
            
            float amplitude = a.getDimension(R.styleable.WaveView_obscura_waveAmplitude, 20f);
            waveEffect.setAmplitude(amplitude);
            waveEffect.setFrequency(a.getFloat(R.styleable.WaveView_obscura_waveFrequency, 1f));
            waveEffect.setSpeed(a.getFloat(R.styleable.WaveView_obscura_waveSpeed, 1f));
            waveEffect.setColor(a.getColor(R.styleable.WaveView_obscura_waveColor, Color.BLUE));
            
            float strokeWidth = a.getDimension(R.styleable.WaveView_obscura_waveStrokeWidth, 5f);
            waveEffect.setStrokeWidth(strokeWidth);
            autoStart = a.getBoolean(R.styleable.WaveView_obscura_waveAutoStart, true);
            
            a.recycle();
        }
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        waveEffect.setSize(w, h);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (autoStart) {
            waveEffect.draw(canvas, getHeight() / 2f);
            invalidate();
        }
    }
}
