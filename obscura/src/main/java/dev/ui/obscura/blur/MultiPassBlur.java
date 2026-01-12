package dev.ui.obscura.blur;

import android.graphics.Bitmap;
import android.graphics.Canvas;

public class MultiPassBlur {
    
    public static Bitmap apply(Bitmap source, int radius, int passes) {
        if (source == null || source.isRecycled()) {
            return null;
        }
        
        Bitmap result = source.copy(Bitmap.Config.ARGB_8888, true);
        
        for (int i = 0; i < passes; i++) {
            result = FastBlur.apply(result, radius);
        }
        
        return result;
    }
    
    public static Bitmap applyWithDownscale(Bitmap source, int radius, float downscale) {
        if (source == null || source.isRecycled() || downscale <= 0 || downscale > 1) {
            return null;
        }
        
        int newWidth = (int) (source.getWidth() * downscale);
        int newHeight = (int) (source.getHeight() * downscale);
        
        if (newWidth <= 0 || newHeight <= 0) {
            return null;
        }
        
        Bitmap scaled = Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
        Bitmap blurred = FastBlur.apply(scaled, radius);
        
        return Bitmap.createScaledBitmap(blurred, source.getWidth(), source.getHeight(), true);
    }
}
