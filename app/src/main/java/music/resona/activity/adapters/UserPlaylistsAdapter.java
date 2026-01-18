package music.resona.activity.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.List;

import music.resona.R;
import music.resona.online.bridge.models.YTItemResult;

public class UserPlaylistsAdapter extends RecyclerView.Adapter<UserPlaylistsAdapter.PlaylistViewHolder> {

    private final List<YTItemResult> playlists;
    private final OnPlaylistClickListener listener;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(YTItemResult playlist);
    }

    public UserPlaylistsAdapter(List<YTItemResult> playlists, OnPlaylistClickListener listener) {
        this.playlists = playlists;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        YTItemResult playlist = playlists.get(position);
        holder.bind(playlist, listener);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnail;
        private final TextView title;
        private final TextView subtitle;

        public PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.playlist_thumbnail);
            title = itemView.findViewById(R.id.playlist_title);
            subtitle = itemView.findViewById(R.id.playlist_subtitle);

            // Apply typefaces
            music.resona.utils.UiUXUtil.typeface(
                itemView.getContext(), title, "medium.ttf", android.graphics.Typeface.NORMAL);
            music.resona.utils.UiUXUtil.typeface(
                itemView.getContext(), subtitle, "copy.ttf", android.graphics.Typeface.NORMAL);
        }

        public void bind(YTItemResult playlist, OnPlaylistClickListener listener) {
            title.setText(playlist.getTitle());
            
            // Set subtitle (could show song count if available)
            String subtitleText = "Playlist";
            if (playlist.getArtists() != null && !playlist.getArtists().isEmpty()) {
                subtitleText = playlist.getArtists().get(0).getName();
            }
            subtitle.setText(subtitleText);

            // Load thumbnail
            if (playlist.getThumbnail() != null && !playlist.getThumbnail().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(playlist.getThumbnail())
                        .transform(new RoundedCorners(24))
                        .placeholder(R.drawable.ic_placeholder)
                        .into(thumbnail);
            } else {
                thumbnail.setImageResource(R.drawable.ic_placeholder);
            }

            // Set click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlaylistClick(playlist);
                }
            });
        }
    }
}
