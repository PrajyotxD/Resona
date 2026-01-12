package dev.ui.obscura.blur;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncBlur {
    
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    public interface BlurCallback {
        void onBlurComplete(Bitmap blurredBitmap);
    }
    
    public static void applyAsync(final Bitmap source, final int radius, final BlurCallback callback) {
        if (source == null || source.isRecycled()) {
            if (callback != null) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onBlurComplete(null);
                    }
                });
            }
            return;
        }
        
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap result = FastBlur.apply(
                        source.copy(Bitmap.Config.ARGB_8888, true), 
                        radius
                    );
                    
                    if (callback != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onBlurComplete(result);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (callback != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onBlurComplete(null);
                            }
                        });
                    }
                }
            }
        });
    }
    
    public static void shutdown() {
        executor.shutdown();
    }
}
