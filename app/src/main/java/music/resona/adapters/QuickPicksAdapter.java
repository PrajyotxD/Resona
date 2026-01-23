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

import music.resona.MainActivity;
import music.resona.R;
import music.resona.models.Song;
import music.resona.online.bridge.models.ArtistResult;
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
    private long lastClickTime = 0;
    private static final long CLICK_DEBOUNCE_MS = 1000; // 1 second debounce

    public QuickPicksAdapter(@NonNull Context context, @NonNull List<YTItemResult> quickPicks) {
        this.context = context;
        this.quickPicks = quickPicks;
    }

    /**
     * Update the adapter with new items without resetting scroll position.
     * Efficiently notifies only the newly added items.
     */
    public void updateItems(@NonNull List<YTItemResult> newItems) {
        if (newItems.isEmpty()) {
            Log.d(TAG, "No new items to update");
            return;
        }
        
        int oldSize = quickPicks.size();
        quickPicks.clear();
        quickPicks.addAll(newItems);
        int newSize = quickPicks.size();
        
        if (oldSize == 0) {
            // First load: notify entire dataset
            notifyDataSetChanged();
            Log.d(TAG, "Initial load: " + newSize + " items");
        } else if (newSize > oldSize) {
            // Items added: notify only new items to preserve scroll position
            notifyItemRangeInserted(oldSize, newSize - oldSize);
            Log.d(TAG, "Added " + (newSize - oldSize) + " new items, total now: " + newSize);
        } else {
            // Size changed in other way: full refresh
            notifyDataSetChanged();
            Log.d(TAG, "Dataset changed: " + newSize + " items");
        }
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

        // Set artist name
        String artistName = getArtistName(item);
        if (artistName != null && !artistName.isEmpty()) {
            holder.tvArtist.setText(artistName);
        } else {
            holder.tvArtist.setText("Unknown Artist");
        }

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

        // Apply typefaces using UIUXUtil
        UiUXUtil.typeface(context, holder.tvTitle, "akatski.ttf", android.graphics.Typeface.NORMAL);
        UiUXUtil.typeface(context, holder.tvArtist, "medium.ttf", android.graphics.Typeface.NORMAL);

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
        // Debounce to prevent double clicks
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < CLICK_DEBOUNCE_MS) {
            Log.d(TAG, "Click ignored (debounce)");
            return;
        }
        lastClickTime = currentTime;
        
        Log.d(TAG, "Quick pick clicked: " + item.getTitle());
        
        String videoId = item.getId();
        if (videoId == null || videoId.isEmpty()) {
            Toast.makeText(context, "Cannot play: No video ID", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String artistName = getArtistName(item);
        // Fallback to "Unknown Artist" if no artist info
        if (artistName == null || artistName.isEmpty()) {
            artistName = "Unknown Artist";
        }
        Integer duration = item.getDuration();
        
        Song song = new Song(
            videoId,
            item.getTitle(),
            artistName,
            null, // album
            item.getThumbnail(),
            duration != null ? duration : 0
        );
        
        // Populate artist info for clickable navigation
        if (item.getArtists() != null && !item.getArtists().isEmpty()) {
            List<ArtistResult> artists = item.getArtists();
            String[] browseIds = new String[artists.size()];
            String[] names = new String[artists.size()];
            
            for (int i = 0; i < artists.size(); i++) {
                ArtistResult artist = artists.get(i);
                browseIds[i] = artist.getId();
                names[i] = artist.getName();
            }
            
            song.setArtistInfo(browseIds, names);
        }
        
        // Play radio mode for continuous playback (auto-queues similar songs)
        if (context instanceof MainActivity) {
            ((MainActivity) context).playRadio(song);
        } else {
            Toast.makeText(context, "Playing: " + item.getTitle(), Toast.LENGTH_SHORT).show();
        }
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
