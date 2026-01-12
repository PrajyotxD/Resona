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
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.Locale;

import music.resona.R;
import music.resona.online.bridge.models.YTItemResult;
import music.resona.utils.UiUXUtil;

/**
 * Adapter managing the horizontal list of items within a home feed section.
 * 
 * <p>Each item represents a song, album, artist, or playlist with:</p>
 * <ul>
 *   <li>Thumbnail image (album art, artist photo, etc.)</li>
 *   <li>Title (song name, album name, etc.)</li>
 *   <li>Subtitle (artist name, track count, duration, etc.)</li>
 * </ul>
 */
public class HomeItemAdapter extends RecyclerView.Adapter<HomeItemAdapter.ItemViewHolder> {

    private static final String TAG = "HomeItemAdapter";
    private static final String ITEM_TYPE_SONG = "song";
    private static final String ITEM_TYPE_ALBUM = "album";
    private static final String ITEM_TYPE_ARTIST = "artist";
    private static final String ITEM_TYPE_PLAYLIST = "playlist";

    private final Context context;
    private final List<YTItemResult> items;

    /**
     * Creates a new HomeItemAdapter.
     * 
     * @param context the activity context
     * @param items the list of items to display
     */
    public HomeItemAdapter(@NonNull Context context, @NonNull List<YTItemResult> items) {
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_home_card, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        YTItemResult item = items.get(position);
        
        holder.tvTitle.setText(item.getTitle());
        holder.tvSubtitle.setText(buildSubtitle(item));
        loadThumbnail(holder.ivThumbnail, item.getThumbnail());
        setupItemClickListener(holder.itemView, item);
        
        // Apply typefaces
        music.resona.utils.UiUXUtil.typeface(context, holder.tvTitle, "akatski.ttf", android.graphics.Typeface.BOLD);
        // tvSubtitle uses default system font
        UiUXUtil.typeface(context, holder.tvSubtitle, "copy.ttf", android.graphics.Typeface.NORMAL);
        
        // Apply rounded corners to thumbnail
//        music.resona.utils.UiUXUtil.setRoundedImage(holder.ivThumbnail, 16);
        UiUXUtil.imageradius(holder.ivThumbnail,20);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
    
    /**
     * Builds the subtitle text based on item type and metadata.
     * 
     * @param item the item result
     * @return the formatted subtitle string
     */
    @NonNull
    private String buildSubtitle(@NonNull YTItemResult item) {
        String type = item.getType();
        
        if (ITEM_TYPE_SONG.equals(type)) {
            return buildSongSubtitle(item);
        } else if (ITEM_TYPE_ALBUM.equals(type)) {
            return buildAlbumSubtitle(item);
        } else if (ITEM_TYPE_ARTIST.equals(type)) {
            return "Artist";
        } else if (ITEM_TYPE_PLAYLIST.equals(type)) {
            return "Playlist";
        }
        
        return type != null ? type : "";
    }
    
    /**
     * Builds subtitle for song items.
     * 
     * @param item the song item
     * @return formatted subtitle with artist and duration
     */
    @NonNull
    private String buildSongSubtitle(@NonNull YTItemResult item) {
        String artistName = getFirstArtistName(item);
        
        if (artistName != null) {
            Integer duration = item.getDuration();
            if (duration != null && duration > 0) {
                return artistName + " • " + formatDuration(duration);
            }
            return artistName;
        }
        
        return "Song";
    }
    
    /**
     * Builds subtitle for album items.
     * 
     * @param item the album item
     * @return formatted subtitle with artist name
     */
    @NonNull
    private String buildAlbumSubtitle(@NonNull YTItemResult item) {
        String artistName = getFirstArtistName(item);
        return artistName != null ? artistName : "Album";
    }
    
    /**
     * Retrieves the first artist name from the item.
     * 
     * @param item the item result
     * @return the artist name, or null if not available
     */
    @Nullable
    private String getFirstArtistName(@NonNull YTItemResult item) {
        if (item.getArtists() != null && !item.getArtists().isEmpty()) {
            return item.getArtists().get(0).getName();
        }
        return null;
    }
    
    /**
     * Formats duration from seconds to MM:SS format.
     * 
     * @param durationSeconds the duration in seconds
     * @return formatted duration string (e.g., "3:45")
     */
    @NonNull
    private String formatDuration(int durationSeconds) {
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }
    
    /**
     * Loads thumbnail image into ImageView using Glide.
     * 
     * @param imageView the target ImageView
     * @param thumbnailUrl the thumbnail URL, or null
     */
    private void loadThumbnail(@NonNull ImageView imageView, @Nullable String thumbnailUrl) {
        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            Glide.with(context)
                .load(thumbnailUrl)
                .placeholder(R.drawable.memefi)
                .error(R.drawable.memefi)
                .centerCrop()
                .into(imageView);
        } else {
            imageView.setImageResource(R.drawable.memefi);
        }
    }
    
    /**
     * Sets up click listener for item interactions.
     * 
     * @param itemView the item view
     * @param item the item data
     */
    private void setupItemClickListener(@NonNull View itemView, @NonNull YTItemResult item) {
        itemView.setOnClickListener(v -> handleItemClick(item));
    }
    
    /**
     * Handles item click events.
     * 
     * @param item the clicked item
     */
    private void handleItemClick(@NonNull YTItemResult item) {
        Log.d(TAG, "Clicked: " + item.getTitle() + " (Type: " + item.getType() + ")");
        
        String message = buildClickMessage(item);
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        
        // TODO: Implement type-specific navigation
        // - Songs: Start playback
        // - Albums: Open album page
        // - Artists: Open artist page
        // - Playlists: Open playlist page
    }
    
    /**
     * Builds the click message for toast display.
     * 
     * @param item the clicked item
     * @return the formatted message
     */
    @NonNull
    private String buildClickMessage(@NonNull YTItemResult item) {
        return item.getTitle() + "\n" + 
               "Type: " + item.getType() + "\n" +
               "ID: " + item.getId();
    }

    /**
     * ViewHolder for item display.
     */
    static class ItemViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivThumbnail;
        final TextView tvTitle;
        final TextView tvSubtitle;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.imageview1);
            tvTitle = itemView.findViewById(R.id.textview1);
            tvSubtitle = itemView.findViewById(R.id.textview2);
        }
    }
}
