package dev.ui.obscura.reveal;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.style.ReplacementSpan;

public class RevealableSpan extends ReplacementSpan {
    
    private RevealEffect effect;
    private Rect bounds;
    private int textColor;
    private int coverColor;
    private boolean revealed = false;
    
    public RevealableSpan(int textColor, int coverColor) {
        this.effect = new RevealEffect();
        this.textColor = textColor;
        this.coverColor = coverColor;
    }
    
    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return (int) paint.measureText(text, start, end);
    }
    
    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, 
                     float x, int top, int y, int bottom, Paint paint) {
        if (bounds == null) {
            bounds = new Rect();
        }
        
        float width = paint.measureText(text, start, end);
        bounds.set((int) x, top, (int) (x + width), bottom);
        effect.setBounds(bounds);
        effect.setColor(textColor);
        
        if (!revealed || !effect.isRevealed()) {
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(coverColor);
            canvas.drawRect(bounds, bgPaint);
            
            effect.draw(canvas, bounds);
        } else {
            int oldColor = paint.getColor();
            paint.setColor(textColor);
            canvas.drawText(text, start, end, x, y, paint);
            paint.setColor(oldColor);
        }
    }
    
    public void startReveal(float x, float y, float radius, Runnable onComplete) {
        revealed = true;
        effect.startReveal(x, y, radius, onComplete);
    }
    
    public void reset() {
        revealed = false;
        effect.reset();
    }
    
    public boolean isRevealed() {
        return effect.isRevealed();
    }
    
    public Rect getBounds() {
        return bounds;
    }
}
