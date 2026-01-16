package music.resona.adapters;

import android.content.Context;
import android.content.Intent;
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

import music.resona.MainActivity;
import music.resona.R;
import music.resona.activity.Vibe;
import music.resona.models.Song;
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
        
        // Set unique transition name for shared element animation
        String transitionName = "thumbnail_" + item.getId() + "_" + position;
        holder.ivThumbnail.setTransitionName(transitionName);
        
        setupItemClickListener(holder.itemView, holder.ivThumbnail, item, transitionName);
        
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
     * @param imageView the thumbnail ImageView for transition
     * @param item the item data
     * @param transitionName the unique transition name
     */
    private void setupItemClickListener(@NonNull View itemView, @NonNull ImageView imageView, 
                                       @NonNull YTItemResult item, @NonNull String transitionName) {
        itemView.setOnClickListener(v -> handleItemClick(imageView, item, transitionName));
    }
    
    /**
     * Handles item click events.
     * 
     * @param imageView the thumbnail ImageView for transition
     * @param item the clicked item
     * @param transitionName the transition name for shared element
     */
    private void handleItemClick(@NonNull ImageView imageView, @NonNull YTItemResult item, 
                                @NonNull String transitionName) {
        Log.d(TAG, "Clicked: " + item.getTitle() + " (Type: " + item.getType() + ")");
        
        String type = item.getType();
        
        // For albums and playlists - open Vibe activity with transition
        if (ITEM_TYPE_ALBUM.equals(type) || ITEM_TYPE_PLAYLIST.equals(type)) {
            openVibeActivity(imageView, item, transitionName);
        } else if (ITEM_TYPE_SONG.equals(type)) {
            // Play the song directly
            playSong(item);
        } else if (ITEM_TYPE_ARTIST.equals(type)) {
            // TODO: Open artist page
            Toast.makeText(context, "Artist page: " + item.getTitle(), Toast.LENGTH_SHORT).show();
        } else {
            // For other types, show a toast for now
            Toast.makeText(context, "Item: " + item.getTitle(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Plays a song item by converting it to Song model and calling MainActivity.
     * 
     * @param item the song item to play
     */
    private void playSong(@NonNull YTItemResult item) {
        String videoId = item.getId();
        if (videoId == null || videoId.isEmpty()) {
            Toast.makeText(context, "Cannot play: No video ID", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String artistName = getFirstArtistName(item);
        Integer duration = item.getDuration();
        
        Song song = new Song(
            videoId,
            item.getTitle(),
            artistName,
            null, // album
            item.getThumbnail(),
            duration != null ? duration : 0
        );
        
        // Play through MainActivity
        if (context instanceof MainActivity) {
            ((MainActivity) context).playSong(song);
        } else {
            Toast.makeText(context, "Playing: " + item.getTitle(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Opens the Vibe activity for albums, playlists, and singles with shared element transition.
     * 
     * @param imageView the thumbnail ImageView for transition
     * @param item the item to display
     * @param transitionName the transition name
     */
    private void openVibeActivity(@NonNull ImageView imageView, @NonNull YTItemResult item, 
                                 @NonNull String transitionName) {
        Intent intent = new Intent(context, Vibe.class);
        
        // Determine browseId - use browseId for albums/artists, playlistId for playlists
        String browseId = item.getBrowseId();
        if (browseId == null && item.getPlaylistId() != null) {
            browseId = item.getPlaylistId();
        }
        if (browseId == null) {
            browseId = item.getId();
        }
        
        // Build subtitle
        String subtitle = buildSubtitle(item);
        
        intent.putExtra("browseId", browseId);
        intent.putExtra("title", item.getTitle());
        intent.putExtra("subtitle", subtitle);
        intent.putExtra("thumbnailUrl", item.getThumbnail());
        intent.putExtra("transitionName", transitionName);
        
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            android.os.Bundle options = android.app.ActivityOptions
                .makeSceneTransitionAnimation(activity, imageView, transitionName)
                .toBundle();
            context.startActivity(intent, options);
        } else {
            context.startActivity(intent);
        }
        
        Log.d(TAG, "Opening Vibe activity for: " + item.getTitle() + " (browseId: " + browseId + ")");
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
