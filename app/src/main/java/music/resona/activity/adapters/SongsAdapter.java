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

import java.util.ArrayList;
import java.util.List;

import music.resona.R;
import music.resona.activity.models.SongItem;

public class SongsAdapter extends RecyclerView.Adapter<SongsAdapter.SongViewHolder> {

    private List<SongItem> songs = new ArrayList<>();
    private OnSongClickListener listener;

    public interface OnSongClickListener {
        void onSongClick(SongItem song, int position);
        void onSongMoreClick(SongItem song, int position);
    }

    public SongsAdapter(OnSongClickListener listener) {
        this.listener = listener;
    }

    public void setSongs(List<SongItem> songs) {
        this.songs = songs;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        SongItem song = songs.get(position);
        holder.bind(song, position);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    class SongViewHolder extends RecyclerView.ViewHolder {
        private ImageView thumbnail;
        private TextView title;
        private TextView artist;
        private ImageView btnMore;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.song_thumbnail);
            title = itemView.findViewById(R.id.song_title);
            artist = itemView.findViewById(R.id.song_artist);
            btnMore = itemView.findViewById(R.id.btn_more_options);
        }

        public void bind(SongItem song, int position) {
            title.setText(song.getTitle());
            artist.setText(song.getArtist());

            // Apply typefaces
            music.resona.utils.UiUXUtil.typeface(itemView.getContext(), title, "medium.ttf", android.graphics.Typeface.NORMAL);
            music.resona.utils.UiUXUtil.typeface(itemView.getContext(), artist, "medium.ttf", android.graphics.Typeface.NORMAL);

            // Load thumbnail with Glide
            if (song.getThumbnailUrl() != null && !song.getThumbnailUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(song.getThumbnailUrl())
                        .transform(new RoundedCorners(8))
                        .placeholder(R.drawable.plugin)
                        .into(thumbnail);
            } else {
                thumbnail.setImageResource(R.drawable.plugin);
            }

            // Click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSongClick(song, position);
                }
            });

            btnMore.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSongMoreClick(song, position);
                }
            });
        }
    }
}
