package music.resona.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Utility class for generating noise textures for UI effects.
 * 
 * <p>Provides cached film grain noise drawable for consistent visual effects.</p>
 */
public final class NoiseUtils {
    
    private static final int NOISE_WIDTH = 100;
    private static final int NOISE_HEIGHT = 100;
    private static final int DEFAULT_INTENSITY = 40;
    private static final int MAX_COLOR_VALUE = 255;

    @Nullable
    private static Drawable cachedNoise;

    private NoiseUtils() {
        // Prevent instantiation
    }

    /**
     * Returns a cached noise drawable with film grain effect.
     * 
     * @param context the application context
     * @return the noise drawable
     */
    @NonNull
    public static Drawable getNoise(@NonNull Context context) {
        if (cachedNoise == null) {
            cachedNoise = generateNoise(context, DEFAULT_INTENSITY);
        }
        return cachedNoise;
    }

    /**
     * Generates a noise texture with the specified intensity.
     * 
     * @param context the application context
     * @param intensity the noise intensity (0-255)
     * @return the generated noise drawable
     */
    @NonNull
    private static Drawable generateNoise(@NonNull Context context, int intensity) {
        Bitmap bitmap = createNoiseBitmap(intensity);
        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        return drawable;
    }

    /**
     * Creates a bitmap with random noise pixels.
     * 
     * @param intensity the noise intensity
     * @return the noise bitmap
     */
    @NonNull
    private static Bitmap createNoiseBitmap(int intensity) {
        Bitmap bitmap = Bitmap.createBitmap(NOISE_WIDTH, NOISE_HEIGHT, Bitmap.Config.ARGB_8888);
        int[] pixels = generateNoisePixels(NOISE_WIDTH * NOISE_HEIGHT, intensity);
        bitmap.setPixels(pixels, 0, NOISE_WIDTH, 0, 0, NOISE_WIDTH, NOISE_HEIGHT);
        return bitmap;
    }

    /**
     * Generates an array of random noise pixels.
     * 
     * @param pixelCount the number of pixels to generate
     * @param intensity the noise intensity
     * @return the pixel array
     */
    @NonNull
    private static int[] generateNoisePixels(int pixelCount, int intensity) {
        int[] pixels = new int[pixelCount];
        Random random = new Random();
        
        for (int i = 0; i < pixelCount; i++) {
            int alpha = random.nextInt(intensity);
            pixels[i] = Color.argb(alpha, MAX_COLOR_VALUE, MAX_COLOR_VALUE, MAX_COLOR_VALUE);
        }
        
        return pixels;
    }
}