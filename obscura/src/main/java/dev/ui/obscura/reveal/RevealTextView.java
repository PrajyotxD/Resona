package dev.ui.obscura.reveal;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatTextView;

import dev.ui.obscura.R;

import java.util.ArrayList;
import java.util.List;

public class RevealTextView extends AppCompatTextView {
    
    private List<RevealSpanInfo> revealSpans = new ArrayList<>();
    private Paint coverPaint;
    private int revealCoverColor = Color.LTGRAY;
    private int revealParticleColor = Color.WHITE;
    private boolean revealOnTouch = true;
    private boolean autoCoverApplied = false;
    private boolean isSettingSpannable = false;
    
    public RevealTextView(Context context) {
        super(context);
        init(null);
    }
    
    public RevealTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public RevealTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        setWillNotDraw(false);
        setClickable(true);
        
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.RevealTextView);
            revealCoverColor = a.getColor(R.styleable.RevealTextView_obscura_revealCoverColor, Color.LTGRAY);
            revealParticleColor = a.getColor(R.styleable.RevealTextView_obscura_revealParticleColor, Color.WHITE);
            revealOnTouch = a.getBoolean(R.styleable.RevealTextView_obscura_revealOnTouch, true);
            a.recycle();
        }
        
        coverPaint.setColor(revealCoverColor);
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Apply auto-cover after view is attached and has text
        if (revealOnTouch && !autoCoverApplied) {
            post(new Runnable() {
                @Override
                public void run() {
                    if (!autoCoverApplied && revealSpans.isEmpty()) {
                        coverAll();
                        autoCoverApplied = true;
                    }
                }
            });
        }
    }
    
    /**
     * Cover the entire text with reveal effect
     */
    public void coverAll() {
        CharSequence text = getText();
        if (text == null || text.length() == 0) return;
        
        revealSpans.clear();
        
        // Create new spannable
        SpannableString spannable = new SpannableString(text);
        RevealSpan span = new RevealSpan();
        spannable.setSpan(span, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        RevealSpanInfo info = new RevealSpanInfo();
        info.span = span;
        info.start = 0;
        info.end = text.length();
        revealSpans.add(info);
        
        // Set text without clearing spans
        isSettingSpannable = true;
        setText(spannable, BufferType.SPANNABLE);
        isSettingSpannable = false;
        
        invalidate();
    }
    
    public void addRevealSpan(int start, int end) {
        CharSequence text = getText();
        if (text instanceof Spannable) {
            Spannable spannable = (Spannable) text;
            RevealSpan span = new RevealSpan();
            spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            RevealSpanInfo info = new RevealSpanInfo();
            info.span = span;
            info.start = start;
            info.end = end;
            revealSpans.add(info);
        }
    }
    
    public void setCoverColor(int color) {
        coverPaint.setColor(color);
        invalidate();
    }
    
    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        // Only clear spans if we're not setting our own spannable
        if (revealSpans != null && !isSettingSpannable) {
            revealSpans.clear();
            autoCoverApplied = false;
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        Layout layout = getLayout();
        if (layout == null) {
            return;
        }
        
        canvas.save();
        canvas.translate(getTotalPaddingLeft(), getTotalPaddingTop());
        
        for (RevealSpanInfo info : revealSpans) {
            if (!info.span.isRevealed()) {
                Rect bounds = info.span.getBounds();
                if (bounds != null) {
                    info.span.effect.draw(canvas, bounds);
                }
            }
        }
        
        canvas.restore();
        
        boolean needsRedraw = false;
        for (RevealSpanInfo info : revealSpans) {
            if (!info.span.isRevealed()) {
                needsRedraw = true;
                break;
            }
        }
        
        if (needsRedraw) {
            postInvalidateOnAnimation();
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            Layout layout = getLayout();
            if (layout != null) {
                int x = (int) event.getX() - getTotalPaddingLeft();
                int y = (int) event.getY() - getTotalPaddingTop();
                
                int line = layout.getLineForVertical(y);
                int offset = layout.getOffsetForHorizontal(line, x);
                
                for (RevealSpanInfo info : revealSpans) {
                    if (offset >= info.start && offset <= info.end && !info.span.isRevealed()) {
                        Rect bounds = info.span.getBounds();
                        if (bounds != null) {
                            float cx = bounds.centerX();
                            float cy = bounds.centerY();
                            float radius = (float) Math.sqrt(bounds.width() * bounds.width() + 
                                bounds.height() * bounds.height()) / 2f;
                            
                            info.span.reveal(cx, cy, radius);
                            invalidate();
                            return true;
                        }
                    }
                }
            }
        }
        
        return super.onTouchEvent(event);
    }
    
    private static class RevealSpanInfo {
        RevealSpan span;
        int start;
        int end;
    }
    
    public class RevealSpan extends ReplacementSpan {
        
        private RevealEffect effect;
        private Rect bounds;
        private boolean revealed = false;
        
        public RevealSpan() {
            effect = new RevealEffect();
        }
        
        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return (int) paint.measureText(text, start, end);
        }
        
        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            if (bounds == null) {
                bounds = new Rect();
            }
            
            float width = paint.measureText(text, start, end);
            bounds.set((int) x, top, (int) (x + width), bottom);
            effect.setBounds(bounds);
            
            int textColor = paint.getColor();
            effect.setColor(textColor);
            
            if (!revealed) {
                paint.setColor(Color.TRANSPARENT);
                canvas.drawText(text, start, end, x, y, paint);
                paint.setColor(textColor);
                
                coverPaint.setColor(Color.argb(200, 150, 150, 150));
                canvas.drawRect(bounds, coverPaint);
                
                effect.draw(canvas, bounds);
            } else {
                canvas.drawText(text, start, end, x, y, paint);
            }
        }
        
        public void reveal(float cx, float cy, float radius) {
            effect.startReveal(cx, cy, radius, new Runnable() {
                @Override
                public void run() {
                    revealed = true;
                }
            });
        }
        
        public boolean isRevealed() {
            return effect.isRevealed();
        }
        
        public Rect getBounds() {
            return bounds;
        }
    }
}
