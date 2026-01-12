package dev.ui.obscura.parallax;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import dev.ui.obscura.R;

/**
 * ParallaxLayout - A LinearLayout with sensor-based parallax effect.
 * 
 * Children are arranged normally (vertically/horizontally based on orientation).
 * The entire layout shifts based on device tilt for a 3D parallax effect.
 * 
 * Usage in XML:
 * <dev.ui.obscura.parallax.ParallaxLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:orientation="vertical"
 *     app:obscura_parallaxFactor="0.3"
 *     app:obscura_parallaxMaxOffset="50dp">
 *     
 *     <ImageView ... />
 *     <TextView ... />
 *     
 * </dev.ui.obscura.parallax.ParallaxLayout>
 */
public class ParallaxLayout extends LinearLayout implements SensorEventListener {
    
    private SensorManager sensorManager;
    private Sensor accelerometer;
    
    private float parallaxFactor = 0.3f;
    private float maxOffset = 50f;
    
    private float offsetX = 0f;
    private float offsetY = 0f;
    private float targetOffsetX = 0f;
    private float targetOffsetY = 0f;
    
    private float centerX, centerY;
    private boolean useSensor = true;
    private boolean enabled = true;
    private static final float SMOOTHING = 0.1f;
    
    public ParallaxLayout(Context context) {
        super(context);
        init(null);
    }
    
    public ParallaxLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public ParallaxLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        setWillNotDraw(false);
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ParallaxLayout);
            parallaxFactor = a.getFloat(R.styleable.ParallaxLayout_obscura_parallaxFactor, 0.3f);
            maxOffset = a.getDimension(R.styleable.ParallaxLayout_obscura_parallaxMaxOffset, 50f);
            a.recycle();
        }
        
        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }
    
    public void setParallaxFactor(float factor) {
        this.parallaxFactor = Math.max(0f, Math.min(1f, factor));
    }
    
    public void setMaxOffset(float offset) {
        this.maxOffset = offset;
    }
    
    public void setUseSensor(boolean useSensor) {
        this.useSensor = useSensor;
        if (useSensor) {
            startListening();
        } else {
            stopListening();
        }
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled && useSensor) {
            startListening();
        } else {
            stopListening();
            offsetX = 0;
            offsetY = 0;
            invalidate();
        }
    }
    
    private void startListening() {
        if (sensorManager != null && accelerometer != null && useSensor) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }
    
    private void stopListening() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (enabled && useSensor) {
            startListening();
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopListening();
    }
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!enabled || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }
        
        float x = event.values[0];
        float y = event.values[1];
        
        // Calculate target offset based on tilt
        targetOffsetX = -x * parallaxFactor * maxOffset;
        targetOffsetY = y * parallaxFactor * maxOffset;
        
        // Clamp to max offset
        targetOffsetX = Math.max(-maxOffset, Math.min(maxOffset, targetOffsetX));
        targetOffsetY = Math.max(-maxOffset, Math.min(maxOffset, targetOffsetY));
        
        // Smooth interpolation
        offsetX += (targetOffsetX - offsetX) * SMOOTHING;
        offsetY += (targetOffsetY - offsetY) * SMOOTHING;
        
        invalidate();
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }
    
    public void updateParallax(float x, float y) {
        if (!useSensor) {
            float deltaX = (x - centerX) / centerX;
            float deltaY = (y - centerY) / centerY;
            
            offsetX = Math.max(-maxOffset, Math.min(maxOffset, deltaX * maxOffset * parallaxFactor));
            offsetY = Math.max(-maxOffset, Math.min(maxOffset, deltaY * maxOffset * parallaxFactor));
            
            invalidate();
        }
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(offsetX, offsetY);
        super.dispatchDraw(canvas);
        canvas.restore();
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!useSensor) {
            updateParallax(event.getX(), event.getY());
        }
        return super.onTouchEvent(event);
    }
}
