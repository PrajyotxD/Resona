package music.resona.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * Utility class for UI/UX customizations and effects.
 * 
 * <p>Provides methods for styling views, managing system UI visibility,
 * and creating visual effects.</p>
 */
public final class UiUXUtil {

    // Color constants - keeping both old and new names for backward compatibility
    public static final String COLOR_BLUE = "#2157e6";
    public static final String COLOR_DARK_BLUE = "#0047ab";
    public static final String COLOR_ACCENT = "#14141c";
    public static final String COLOR_PRIMARY = "#0B0B10";
    public static final String COLOR_STROKE = "#464643";
    
    // Legacy names for backward compatibility
    public static final String blue = COLOR_BLUE;
    public static final String darkblue = COLOR_DARK_BLUE;
    public static final String accent = COLOR_ACCENT;
    public static final String primary = COLOR_PRIMARY;
    public static final String stroke = COLOR_STROKE;
    private static final int STATUS_BAR_COLOR = 0xFF111111;

    private UiUXUtil() {
        // Prevent instantiation
    }

    /**
     * Applies a gradient background with rounded corners and stroke to a view.
     *
     * @param view        the target view
     * @param radius      the corner radius in pixels
     * @param colorTop    the top gradient color
     * @param colorBottom the bottom gradient color
     * @param strokeWidth the stroke width in pixels
     * @param strokeColor the stroke color
     */
    public static void radiusGrad(@NonNull View view, int radius, @NonNull String colorTop,
                                  @NonNull String colorBottom, int strokeWidth,
                                  @NonNull String strokeColor) {
        int[] colors = {Color.parseColor(colorTop), Color.parseColor(colorBottom)};
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, colors
        );
        drawable.setCornerRadius(radius);
        drawable.setStroke(strokeWidth, Color.parseColor(strokeColor));
        view.setBackground(drawable);
    }

    /**
     * Sets a custom typeface for a TextView.
     *
     * @param context  the application context
     * @param textView the target TextView
     * @param fontName the font file name in assets/fonts/
     * @param style    the typeface style (e.g., Typeface.BOLD)
     */
    public static void typeface(@NonNull Context context, @NonNull TextView textView,
                                @NonNull String fontName, int style) {
        textView.setTypeface(
                Typeface.createFromAsset(context.getAssets(), "fonts/" + fontName),
                style
        );
    }

    /**
     * Applies rounded corners to an ImageView's drawable.
     *
     * @param imageView the target ImageView
     * @param radius    the corner radius in pixels
     */
    public static void setRoundedImage(@NonNull ImageView imageView, int radius) {
        if (imageView.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
            Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) imageView.getDrawable()).getBitmap();
            imageView.setImageBitmap(getRoundedCornerBitmap(bitmap, radius));
        }
    }

    /**
     * Creates a bitmap with rounded corners.
     *
     * @param bitmap       the source bitmap
     * @param cornerRadius the corner radius in pixels
     * @return the rounded bitmap
     */
    @NonNull
    public static Bitmap getRoundedCornerBitmap(@NonNull Bitmap bitmap, int cornerRadius) {
        Bitmap output = Bitmap.createBitmap(
                bitmap.getWidth(),
                bitmap.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        RectF rectF = new RectF(rect);

        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(0xff424242);
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    /**
     * Displays a short toast message.
     *
     * @param context the application context
     * @param message the message to display
     */
    public static void showMessage(@NonNull Context context, @NonNull String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Configures translucent status bar with custom color.
     *
     * @param activity the target activity
     */
    public static void TStatusBar(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            Window window = activity.getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(STATUS_BAR_COLOR);
        }

        activity.getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
    }

    /**
     * Configures transparent status bar with full-screen layout.
     *
     * @param activity the target activity
     */
    public static void TransparentStatus(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    );
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
        }
    }

    /**
     * Configures immersive mode with visible status bar and hidden navigation.
     *
     * @param activity the target activity
     */
    public static void ImmersiveHome(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = activity.getWindow();

            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(STATUS_BAR_COLOR);

            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    /**
     * Applies rounded corners to an ImageView using clipping.
     *
     * @param imageView the target ImageView
     * @param radius    the corner radius in pixels
     */
    public static void imageradius(@NonNull ImageView imageView, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(radius);
        drawable.setColor(Color.TRANSPARENT);
        imageView.setBackground(drawable);
        imageView.setClipToOutline(true);
    }
}