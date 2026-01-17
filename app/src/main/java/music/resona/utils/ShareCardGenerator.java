package music.resona.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;

import music.resona.R;

/**
 * Utility class for generating share card bitmaps.
 * Handles all Canvas drawing operations for story-ready share cards.
 */
public class ShareCardGenerator {

    private final Context context;
    private final int width;
    private final int height;

    public ShareCardGenerator(@NonNull Context context) {
        this.context = context;
        this.width = context.getResources().getDimensionPixelSize(R.dimen.share_card_width);
        this.height = context.getResources().getDimensionPixelSize(R.dimen.share_card_height);
    }

    /**
     * Generates a share card bitmap with the specified content and gradient.
     *
     * @param gradientColors Array of gradient colors [start, center, end]
     * @param title          Playlist/Album title
     * @param subtitle       Artist or creator name
     * @param albumArtView   ImageView containing album artwork
     * @param thumbnailUrl   Fallback URL for album artwork
     * @return Generated bitmap or null on failure
     */
    @Nullable
    public Bitmap generateShareCard(
            @NonNull int[] gradientColors,
            @Nullable String title,
            @Nullable String subtitle,
            @Nullable ImageView albumArtView,
            @Nullable String thumbnailUrl
    ) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            drawGradientBackground(canvas, gradientColors);
            drawInnerCard(canvas);
            drawAlbumArtwork(canvas, albumArtView, thumbnailUrl);
            drawTexts(canvas, title, subtitle);
            drawWatermark(canvas);

            return bitmap;
        } catch (Exception e) {
            android.util.Log.e("ShareCardGenerator", "Error generating share card", e);
            return null;
        }
    }

    private void drawGradientBackground(@NonNull Canvas canvas, @NonNull int[] colors) {
        android.graphics.LinearGradient gradient = new android.graphics.LinearGradient(
                0, 0, width, height,
                colors,
                null,
                android.graphics.Shader.TileMode.CLAMP
        );
        Paint gradientPaint = new Paint();
        gradientPaint.setShader(gradient);
        canvas.drawRect(0, 0, width, height, gradientPaint);
    }

    private void drawInnerCard(@NonNull Canvas canvas) {
        int cardWidth = context.getResources().getDimensionPixelSize(R.dimen.share_card_inner_width);
        int cardHeight = context.getResources().getDimensionPixelSize(R.dimen.share_card_inner_height);
        int cardRadius = context.getResources().getDimensionPixelSize(R.dimen.share_card_inner_radius);

        int cardLeft = (width - cardWidth) / 2;
        int cardTop = (height - cardHeight) / 2;
        int cardRight = cardLeft + cardWidth;
        int cardBottom = cardTop + cardHeight;

        Paint cardPaint = new Paint();
        cardPaint.setColor(context.getResources().getColor(R.color.card_overlay, null));
        cardPaint.setAntiAlias(true);

        RectF cardRect = new RectF(cardLeft, cardTop, cardRight, cardBottom);
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardPaint);
    }

    private void drawAlbumArtwork(
            @NonNull Canvas canvas,
            @Nullable ImageView albumArtView,
            @Nullable String thumbnailUrl
    ) {
        int artWidth = context.getResources().getDimensionPixelSize(R.dimen.share_card_artwork_width);
        int artHeight = context.getResources().getDimensionPixelSize(R.dimen.share_card_artwork_height);
        int artRadius = context.getResources().getDimensionPixelSize(R.dimen.share_card_artwork_radius);
        float density = context.getResources().getDisplayMetrics().density;

        int cardWidth = context.getResources().getDimensionPixelSize(R.dimen.share_card_inner_width);
        int cardHeight = context.getResources().getDimensionPixelSize(R.dimen.share_card_inner_height);
        int cardTop = (height - cardHeight) / 2;

        int artX = (width - artWidth) / 2;
        int artY = cardTop + (int) (20 * density);

        try {
            Bitmap albumBitmap = loadAlbumBitmap(albumArtView, thumbnailUrl, artWidth, artHeight);
            if (albumBitmap != null) {
                Bitmap roundedAlbum = createRoundedBitmap(albumBitmap, artWidth, artHeight, artRadius);
                canvas.drawBitmap(roundedAlbum, artX, artY, null);
            }
        } catch (Exception e) {
            android.util.Log.e("ShareCardGenerator", "Error loading album art", e);
        }
    }

    @Nullable
    private Bitmap loadAlbumBitmap(
            @Nullable ImageView albumArtView,
            @Nullable String thumbnailUrl,
            int targetWidth,
            int targetHeight
    ) throws Exception {
        if (albumArtView != null && albumArtView.getDrawable() instanceof BitmapDrawable) {
            return ((BitmapDrawable) albumArtView.getDrawable()).getBitmap();
        } else if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            return Glide.with(context)
                    .asBitmap()
                    .load(thumbnailUrl)
                    .submit(targetWidth, targetHeight)
                    .get();
        }
        return null;
    }

    @NonNull
    private Bitmap createRoundedBitmap(
            @NonNull Bitmap source,
            int width,
            int height,
            int radius
    ) {
        Bitmap rounded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(rounded);

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        RectF rect = new RectF(0, 0, width, height);
        canvas.drawRoundRect(rect, radius, radius, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        Rect src = new Rect(0, 0, source.getWidth(), source.getHeight());
        Rect dst = new Rect(0, 0, width, height);
        canvas.drawBitmap(source, src, dst, paint);

        return rounded;
    }

    private void drawTexts(
            @NonNull Canvas canvas,
            @Nullable String title,
            @Nullable String subtitle
    ) {
        float density = context.getResources().getDisplayMetrics().density;
        int artHeight = context.getResources().getDimensionPixelSize(R.dimen.share_card_artwork_height);
        int cardHeight = context.getResources().getDimensionPixelSize(R.dimen.share_card_inner_height);
        int cardTop = (height - cardHeight) / 2;
        int artY = cardTop + (int) (20 * density);

        int textY = artY + artHeight + (int) (25 * density);

        // Title
        Paint titlePaint = createTextPaint(
                context.getResources().getColor(R.color.text_primary, null),
                20 * density,
                "akatski.ttf",
                Typeface.BOLD
        );
        String titleText = title != null ? title : context.getString(R.string.playlist);
        canvas.drawText(titleText, width / 2f, textY, titlePaint);

        // Artist/Subtitle
        textY += (int) (32 * density);
        Paint artistPaint = createTextPaint(
                context.getResources().getColor(R.color.text_secondary, null),
                14 * density,
                "medium.ttf",
                Typeface.NORMAL
        );
        String artistText = subtitle != null ? subtitle : context.getString(R.string.various_artists);
        canvas.drawText(artistText, width / 2f, textY, artistPaint);

        // Metadata
        textY += (int) (24 * density);
        Paint metaPaint = createTextPaint(
                context.getResources().getColor(R.color.text_tertiary, null),
                12 * density,
                "copy.ttf",
                Typeface.NORMAL
        );
        canvas.drawText(context.getString(R.string.playlist), width / 2f, textY, metaPaint);
    }

    private void drawWatermark(@NonNull Canvas canvas) {
        float density = context.getResources().getDisplayMetrics().density;
        Paint watermarkPaint = createTextPaint(
                context.getResources().getColor(R.color.white_60, null),
                28 * density,
                "akatski.ttf",
                Typeface.BOLD
        );
        watermarkPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(
                context.getString(R.string.watermark),
                width - (int) (20 * density),
                (int) (45 * density),
                watermarkPaint
        );
    }

    @NonNull
    private Paint createTextPaint(int color, float textSize, @NonNull String fontAsset, int style) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);

        try {
            Typeface typeface = Typeface.createFromAsset(context.getAssets(), fontAsset);
            paint.setTypeface(Typeface.create(typeface, style));
        } catch (Exception e) {
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, style));
        }

        return paint;
    }
}
