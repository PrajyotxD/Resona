package dev.ui.obscura;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;

import dev.ui.obscura.blur.AsyncBlur;
import dev.ui.obscura.blur.BlurDrawable;
import dev.ui.obscura.blur.BlurredFrameLayout;
import dev.ui.obscura.blur.FastBlur;
import dev.ui.obscura.blur.GlassView;
import dev.ui.obscura.blur.MultiPassBlur;
import dev.ui.obscura.elastic.ElasticAnimator;
import dev.ui.obscura.gradient.AnimatedGradient;
import dev.ui.obscura.loading.LoadingIndicator;
import dev.ui.obscura.parallax.ParallaxLayout;
import dev.ui.obscura.reveal.RevealDrawable;
import dev.ui.obscura.reveal.RevealEffect;
import dev.ui.obscura.reveal.RevealFrameLayout;
import dev.ui.obscura.reveal.RevealTextView;
import dev.ui.obscura.reveal.RevealableSpan;
import dev.ui.obscura.ripple.RippleEffect;
import dev.ui.obscura.shimmer.ShimmerEffect;
import dev.ui.obscura.shimmer.ShimmerLayout;
import dev.ui.obscura.wave.WaveEffect;

public class Obscura {
    
    public static final int DEFAULT_BLUR_RADIUS = 12;
    public static final float DEFAULT_GLASS_OPACITY = 0.8f;
    public static final int DEFAULT_GLASS_TINT = 0x40FFFFFF;
    
    public static class Blur {
        
        public static Bitmap fast(Bitmap source, int radius) {
            if (source == null || source.isRecycled()) {
                return null;
            }
            return FastBlur.apply(source.copy(Bitmap.Config.ARGB_8888, true), radius);
        }
        
        public static Bitmap fast(Bitmap source) {
            return fast(source, DEFAULT_BLUR_RADIUS);
        }
        
        public static Bitmap multiPass(Bitmap source, int radius, int passes) {
            return MultiPassBlur.apply(source, radius, passes);
        }
        
        public static Bitmap withDownscale(Bitmap source, int radius, float downscale) {
            return MultiPassBlur.applyWithDownscale(source, radius, downscale);
        }
        
        public static void fastAsync(Bitmap source, int radius, AsyncBlur.BlurCallback callback) {
            AsyncBlur.applyAsync(source, radius, callback);
        }
        
        public static BlurDrawable createDrawable() {
            return new BlurDrawable();
        }
        
        public static BlurDrawable createDrawable(Bitmap blurredBitmap) {
            BlurDrawable drawable = new BlurDrawable();
            drawable.setBlurredBitmap(blurredBitmap);
            return drawable;
        }
        
        public static BlurredFrameLayout createLayout(Context context) {
            return new BlurredFrameLayout(context);
        }
    }
    
    public static class Glass {
        
        public static GlassView createView(Context context) {
            return new GlassView(context);
        }
        
        public static GlassView createView(Context context, View sourceView) {
            GlassView view = new GlassView(context);
            view.setSourceView(sourceView);
            return view;
        }
        
        public static GlassView createView(Context context, View sourceView, int blurRadius) {
            GlassView view = new GlassView(context);
            view.setSourceView(sourceView);
            view.setBlurRadius(blurRadius);
            return view;
        }
    }
    
    public static class Reveal {
        
        public static RevealEffect createEffect() {
            return new RevealEffect();
        }
        
        public static RevealDrawable createDrawable() {
            return new RevealDrawable();
        }
        
        public static RevealTextView createTextView(Context context) {
            return new RevealTextView(context);
        }
        
        public static RevealFrameLayout createLayout(Context context) {
            return new RevealFrameLayout(context);
        }
        
        public static RevealableSpan createSpan(int textColor, int coverColor) {
            return new RevealableSpan(textColor, coverColor);
        }
    }
    
    public static class Shimmer {
        
        public static ShimmerEffect createEffect() {
            return new ShimmerEffect();
        }
        
        public static ShimmerLayout createLayout(Context context) {
            return new ShimmerLayout(context);
        }
    }
    
    public static class Gradient {
        
        public static AnimatedGradient create(int... colors) {
            return new AnimatedGradient(colors);
        }
    }
    
    public static class Ripple {
        
        public static RippleEffect create() {
            return new RippleEffect();
        }
    }
    
    public static class Wave {
        
        public static WaveEffect create() {
            return new WaveEffect();
        }
    }
    
    public static class Elastic {
        
        public static void scaleIn(View view, long duration) {
            ElasticAnimator.scaleIn(view, duration);
        }
        
        public static void scaleOut(View view, long duration, Runnable onComplete) {
            ElasticAnimator.scaleOut(view, duration, onComplete);
        }
        
        public static void bounce(View view, long duration) {
            ElasticAnimator.bounce(view, duration);
        }
        
        public static void shake(View view, long duration) {
            ElasticAnimator.shake(view, duration);
        }
    }
    
    public static class Parallax {
        
        public static ParallaxLayout createLayout(Context context) {
            return new ParallaxLayout(context);
        }
    }
    
    public static class Loading {
        
        public static LoadingIndicator create() {
            return new LoadingIndicator();
        }
    }
}
