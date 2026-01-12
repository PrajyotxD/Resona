package dev.ui.obscura.blur;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import dev.ui.obscura.R;

public class BlurImageView extends androidx.appcompat.widget.AppCompatImageView {
    
    private int blurRadius = 12;
    private boolean autoBlur = true;
    private boolean blurOnLoad = true;
    private Bitmap originalBitmap;
    
    public BlurImageView(Context context) {
        super(context);
        init(null);
    }
    
    public BlurImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public BlurImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.BlurImageView);
            blurRadius = a.getInt(R.styleable.BlurImageView_obscura_blurRadius, 12);
            autoBlur = a.getBoolean(R.styleable.BlurImageView_obscura_autoBlur, true);
            blurOnLoad = a.getBoolean(R.styleable.BlurImageView_obscura_blurOnLoad, true);
            a.recycle();
        }
    }
    
    @Override
    public void setImageBitmap(Bitmap bm) {
        if (bm != null && autoBlur) {
            originalBitmap = bm;
            if (blurOnLoad) {
                applyBlur();
            } else {
                super.setImageBitmap(bm);
            }
        } else {
            super.setImageBitmap(bm);
        }
    }
    
    @Override
    public void setImageDrawable(Drawable drawable) {
        if (drawable instanceof BitmapDrawable && autoBlur) {
            Bitmap bm = ((BitmapDrawable) drawable).getBitmap();
            setImageBitmap(bm);
        } else {
            super.setImageDrawable(drawable);
        }
    }
    
    public void setBlurRadius(int radius) {
        this.blurRadius = radius;
        if (originalBitmap != null && autoBlur) {
            applyBlur();
        }
    }
    
    public void applyBlur() {
        if (originalBitmap != null) {
            Bitmap blurred = FastBlur.apply(originalBitmap.copy(Bitmap.Config.ARGB_8888, true), blurRadius);
            super.setImageBitmap(blurred);
        }
    }
    
    public void clearBlur() {
        if (originalBitmap != null) {
            super.setImageBitmap(originalBitmap);
        }
    }
}
