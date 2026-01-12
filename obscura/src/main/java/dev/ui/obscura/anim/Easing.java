package dev.ui.obscura.anim;

import android.view.animation.Interpolator;

public class Easing {
    
    public static final Interpolator LINEAR = new Interpolator() {
        @Override
        public float getInterpolation(float input) {
            return input;
        }
    };
    
    public static final Interpolator EASE_IN_OUT = new Interpolator() {
        @Override
        public float getInterpolation(float input) {
            return (float) (Math.cos((input + 1) * Math.PI) / 2.0f) + 0.5f;
        }
    };
    
    public static final Interpolator EASE_OUT_QUAD = new Interpolator() {
        @Override
        public float getInterpolation(float input) {
            return -input * (input - 2);
        }
    };
    
    public static final Interpolator EASE_IN_QUAD = new Interpolator() {
        @Override
        public float getInterpolation(float input) {
            return input * input;
        }
    };
    
    public static final Interpolator EASE_OUT_CUBIC = new Interpolator() {
        @Override
        public float getInterpolation(float input) {
            float f = input - 1;
            return f * f * f + 1;
        }
    };
    
    public static final Interpolator EASE_IN_CUBIC = new Interpolator() {
        @Override
        public float getInterpolation(float input) {
            return input * input * input;
        }
    };
    
    public static final Interpolator OVERSHOOT = new Interpolator() {
        @Override
        public float getInterpolation(float input) {
            float s = 1.70158f;
            return input * input * ((s + 1) * input - s);
        }
    };
    
    public static final Interpolator BOUNCE = new Interpolator() {
        @Override
        public float getInterpolation(float input) {
            if (input < (1 / 2.75f)) {
                return 7.5625f * input * input;
            } else if (input < (2 / 2.75f)) {
                float f = input - (1.5f / 2.75f);
                return 7.5625f * f * f + 0.75f;
            } else if (input < (2.5 / 2.75)) {
                float f = input - (2.25f / 2.75f);
                return 7.5625f * f * f + 0.9375f;
            } else {
                float f = input - (2.625f / 2.75f);
                return 7.5625f * f * f + 0.984375f;
            }
        }
    };
}
