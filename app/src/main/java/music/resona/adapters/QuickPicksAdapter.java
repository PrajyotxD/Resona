package music.resona.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import music.resona.R;
import music.resona.online.bridge.models.YTItemResult;
import music.resona.utils.UiUXUtil;

/**
 * Adapter for Quick Picks grid display.
 * Shows personalized song recommendations in a 2-column grid layout.
 */
public class QuickPicksAdapter extends RecyclerView.Adapter<QuickPicksAdapter.QuickPickViewHolder> {

    private static final String TAG = "QuickPicksAdapter";
    private final Context context;
    private final List<YTItemResult> quickPicks;

    public QuickPicksAdapter(@NonNull Context context, @NonNull List<YTItemResult> quickPicks) {
        this.context = context;
        this.quickPicks = quickPicks;
    }

    @NonNull
    @Override
    public QuickPickViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_quick_pick_item, parent, false);
        return new QuickPickViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuickPickViewHolder holder, int position) {
        YTItemResult item = quickPicks.get(position);

        holder.tvTitle.setText(item.getTitle());
        
        String artist = getArtistName(item);
        holder.tvArtist.setText(artist != null ? artist : "Unknown Artist");

        // Load thumbnail
        if (item.getThumbnail() != null && !item.getThumbnail().isEmpty()) {
            Glide.with(context)
                    .load(item.getThumbnail())
                    .placeholder(R.drawable.memefi)
                    .error(R.drawable.memefi)
                    .centerCrop()
                    .into(holder.ivThumbnail);
        } else {
            holder.ivThumbnail.setImageResource(R.drawable.memefi);
        }

        // Apply typefaces
        UiUXUtil.typeface(context, holder.tvTitle, "akatski.ttf", android.graphics.Typeface.BOLD);
        UiUXUtil.typeface(context, holder.tvArtist, "copy.ttf", android.graphics.Typeface.NORMAL);

        // Apply rounded corners
        UiUXUtil.setRoundedImage(holder.ivThumbnail, 12);

        // Click listener
        holder.itemView.setOnClickListener(v -> handleItemClick(item));
    }

    @Override
    public int getItemCount() {
        return quickPicks.size();
    }

    private String getArtistName(YTItemResult item) {
        if (item.getArtists() != null && !item.getArtists().isEmpty()) {
            return item.getArtists().get(0).getName();
        }
        return null;
    }

    private void handleItemClick(YTItemResult item) {
        Log.d(TAG, "Quick pick clicked: " + item.getTitle());
        Toast.makeText(context, "Playing: " + item.getTitle(), Toast.LENGTH_SHORT).show();
        // TODO: Implement playback
    }

    static class QuickPickViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivThumbnail;
        final TextView tvTitle;
        final TextView tvArtist;

        QuickPickViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.ivQuickPickThumbnail);
            tvTitle = itemView.findViewById(R.id.tvQuickPickTitle);
            tvArtist = itemView.findViewById(R.id.tvQuickPickArtist);
        }
    }
}
