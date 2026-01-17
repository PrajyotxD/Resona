package music.resona.viewmodel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import music.resona.R;

/**
 * ViewModel for managing share card generation and state.
 * Separates business logic from UI components following MVVM architecture.
 */
public class ShareCardViewModel extends ViewModel {

    private final MutableLiveData<Bitmap> previewBitmap = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isGenerating = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Integer> selectedGradient = new MutableLiveData<>(R.drawable.share_gradient_1);

    @NonNull
    public LiveData<Bitmap> getPreviewBitmap() {
        return previewBitmap;
    }

    @NonNull
    public LiveData<Boolean> getIsGenerating() {
        return isGenerating;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @NonNull
    public LiveData<Integer> getSelectedGradient() {
        return selectedGradient;
    }

    public void setSelectedGradient(int gradientResId) {
        selectedGradient.setValue(gradientResId);
    }

    public void setPreviewBitmap(@Nullable Bitmap bitmap) {
        previewBitmap.setValue(bitmap);
    }

    public void setIsGenerating(boolean generating) {
        isGenerating.setValue(generating);
    }

    public void setError(@Nullable String message) {
        errorMessage.setValue(message);
    }

    /**
     * Extracts gradient colors from drawable resource.
     * 
     * @param context Android context for resource access
     * @param gradientDrawable Gradient drawable resource ID
     * @return Array of gradient colors [start, center, end]
     */
    @NonNull
    public int[] getGradientColors(@NonNull Context context, int gradientDrawable) {
        if (gradientDrawable == R.drawable.share_gradient_1) {
            return new int[]{
                context.getResources().getColor(R.color.gradient_1_start, null),
                context.getResources().getColor(R.color.gradient_1_center, null),
                context.getResources().getColor(R.color.gradient_1_end, null)
            };
        } else if (gradientDrawable == R.drawable.share_gradient_2) {
            return new int[]{
                context.getResources().getColor(R.color.gradient_2_start, null),
                context.getResources().getColor(R.color.gradient_2_center, null),
                context.getResources().getColor(R.color.gradient_2_end, null)
            };
        } else if (gradientDrawable == R.drawable.share_gradient_3) {
            return new int[]{
                context.getResources().getColor(R.color.gradient_3_start, null),
                context.getResources().getColor(R.color.gradient_3_center, null),
                context.getResources().getColor(R.color.gradient_3_end, null)
            };
        } else if (gradientDrawable == R.drawable.share_gradient_4) {
            return new int[]{
                context.getResources().getColor(R.color.gradient_4_start, null),
                context.getResources().getColor(R.color.gradient_4_center, null),
                context.getResources().getColor(R.color.gradient_4_end, null)
            };
        } else if (gradientDrawable == R.drawable.share_gradient_5) {
            return new int[]{
                context.getResources().getColor(R.color.gradient_5_start, null),
                context.getResources().getColor(R.color.gradient_5_center, null),
                context.getResources().getColor(R.color.gradient_5_end, null)
            };
        }
        
        // Default to gradient 1
        return new int[]{
            context.getResources().getColor(R.color.gradient_1_start, null),
            context.getResources().getColor(R.color.gradient_1_center, null),
            context.getResources().getColor(R.color.gradient_1_end, null)
        };
    }

    /**
     * Saves bitmap to cache directory as JPEG.
     * 
     * @param context Android context for cache access
     * @param bitmap Bitmap to save
     * @return File if successful, null otherwise
     */
    @Nullable
    public File saveBitmapToCache(@NonNull Context context, @NonNull Bitmap bitmap) {
        try {
            File cacheDir = new File(context.getCacheDir(), "share_images");
            if (!cacheDir.exists()) {
                if (!cacheDir.mkdirs()) {
                    setError("Failed to create cache directory");
                    return null;
                }
            }

            File imageFile = new File(cacheDir, "share_card_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                fos.flush();
            }

            return imageFile;
        } catch (IOException e) {
            setError("Error saving bitmap: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Clean up any resources if needed
    }
}
