package dev.ui.obscura.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

public class BitmapUtil {
    
    public static Bitmap captureView(View view) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            return null;
        }
        
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }
    
    public static Bitmap captureViewScaled(View view, float scale) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0 || scale <= 0) {
            return null;
        }
        
        int width = (int) (view.getWidth() * scale);
        int height = (int) (view.getHeight() * scale);
        
        if (width <= 0 || height <= 0) {
            return null;
        }
        
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(scale, scale);
        view.draw(canvas);
        return bitmap;
    }
    
    public static void recycleSafe(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
