package dev.ui.obscura.elastic;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.OvershootInterpolator;

public class ElasticAnimator {
    
    public static void scaleIn(final View view, long duration) {
        view.setScaleX(0f);
        view.setScaleY(0f);
        view.setAlpha(0f);
        
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(new OvershootInterpolator(2f))
            .start();
    }
    
    public static void scaleOut(final View view, long duration, final Runnable onComplete) {
        view.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(new TimeInterpolator() {
                @Override
                public float getInterpolation(float input) {
                    return (float) Math.pow(input, 3);
                }
            })
            .withEndAction(onComplete)
            .start();
    }
    
    public static void bounce(final View view, long duration) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new TimeInterpolator() {
            @Override
            public float getInterpolation(float input) {
                if (input < 0.5f) {
                    return (float) Math.pow(input * 2, 2) / 2;
                } else {
                    return (float) (1 - Math.pow((1 - input) * 2, 2) / 2);
                }
            }
        });
        
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                float scale = 1f + (float) Math.sin(value * Math.PI * 4) * 0.1f * (1f - value);
                view.setScaleX(scale);
                view.setScaleY(scale);
            }
        });
        
        animator.start();
    }
    
    public static void shake(final View view, long duration) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                float offset = (float) Math.sin(value * Math.PI * 8) * 10f * (1f - value);
                view.setTranslationX(offset);
            }
        });
        
        animator.start();
    }
}
