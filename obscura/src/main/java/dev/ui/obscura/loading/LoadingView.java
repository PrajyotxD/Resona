package dev.ui.obscura.loading;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;

import dev.ui.obscura.R;

public class LoadingView extends View {
    
    private LoadingIndicator indicator;
    private boolean autoStart = true;
    
    public LoadingView(Context context) {
        super(context);
        init(null);
    }
    
    public LoadingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public LoadingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        setWillNotDraw(false);
        indicator = new LoadingIndicator();
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.LoadingView);
            
            indicator.setColor(a.getColor(R.styleable.LoadingView_obscura_loadingColor, Color.BLUE));
            
            float strokeWidth = a.getDimension(R.styleable.LoadingView_obscura_loadingStrokeWidth, 8f);
            indicator.setStrokeWidth(strokeWidth);
            autoStart = a.getBoolean(R.styleable.LoadingView_obscura_loadingAutoStart, true);
            
            a.recycle();
        }
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int size = Math.min(w, h);
        int padding = size / 4;
        indicator.setBounds(padding, padding, size - padding, size - padding);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (autoStart) {
            indicator.draw(canvas);
            invalidate();
        }
    }
}
