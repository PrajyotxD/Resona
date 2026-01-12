package dev.ui.obscura.util;

public class MathUtil {
    
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    public static float lerp(float start, float end, float fraction) {
        return start + (end - start) * fraction;
    }
    
    public static int lerp(int start, int end, float fraction) {
        return (int) (start + (end - start) * fraction);
    }
    
    public static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
