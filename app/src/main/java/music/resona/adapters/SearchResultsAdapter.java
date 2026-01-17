package music.resona.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import music.resona.R;
import music.resona.databinding.ItemSearchResultBinding;
import music.resona.online.bridge.models.YTItemResult;

/**
 * RecyclerView adapter for search results with View Binding.
 * Displays songs, albums, artists, and playlists in a Spotify-like design.
 */
public class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.SearchResultViewHolder> {

    private final List<YTItemResult> items = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(@NonNull YTItemResult item, int position);
        void onMoreClick(@NonNull YTItemResult item, int position);
    }

    public void setOnItemClickListener(@NonNull OnItemClickListener listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<YTItemResult> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void addItems(@NonNull List<YTItemResult> newItems) {
        int startPosition = items.size();
        items.addAll(newItems);
        notifyItemRangeInserted(startPosition, newItems.size());
    }

    public void clearItems() {
        items.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SearchResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSearchResultBinding binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.getContext()), parent, false
        );
        return new SearchResultViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class SearchResultViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchResultBinding binding;

        SearchResultViewHolder(@NonNull ItemSearchResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull YTItemResult item) {
            // Set title
            binding.txtTitle.setText(item.getTitle());

            // Set subtitle based on type
            String subtitle = buildSubtitle(item);
            binding.txtSubtitle.setText(subtitle);

            // Set info (type and duration)
            String info = buildInfo(item);
            if (info != null && !info.isEmpty()) {
                binding.txtInfo.setVisibility(View.VISIBLE);
                binding.txtInfo.setText(info);
            } else {
                binding.txtInfo.setVisibility(View.GONE);
            }

            // Load thumbnail
            if (item.getThumbnail() != null && !item.getThumbnail().isEmpty()) {
                Glide.with(binding.imgThumbnail.getContext())
                    .load(item.getThumbnail())
                    .transform(new RoundedCorners(12))
                    .placeholder(R.drawable.placeholder_album)
                    .error(R.drawable.placeholder_album)
                    .into(binding.imgThumbnail);
            } else {
                binding.imgThumbnail.setImageResource(R.drawable.placeholder_album);
            }

            // Set click listeners
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item, getAdapterPosition());
                }
            });

            binding.btnMore.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMoreClick(item, getAdapterPosition());
                }
            });
        }

        @NonNull
        private String buildSubtitle(@NonNull YTItemResult item) {
            StringBuilder subtitle = new StringBuilder();

            if (!item.getArtists().isEmpty()) {
                // Join artist names
                for (int i = 0; i < item.getArtists().size(); i++) {
                    subtitle.append(item.getArtists().get(i).getName());
                    if (i < item.getArtists().size() - 1) {
                        subtitle.append(", ");
                    }
                }

                // Add album if available
                if (item.getAlbum() != null && item.getAlbum().getName() != null) {
                    subtitle.append(" • ").append(item.getAlbum().getName());
                }
            } else if (item.getAlbum() != null && item.getAlbum().getName() != null) {
                subtitle.append(item.getAlbum().getName());
            } else {
                // Fallback based on type
                String type = item.getType();
                if ("album".equals(type)) {
                    subtitle.append("Album");
                } else if ("artist".equals(type)) {
                    subtitle.append("Artist");
                } else if ("playlist".equals(type)) {
                    subtitle.append("Playlist");
                }
            }

            return subtitle.toString();
        }

        @NonNull
        private String buildInfo(@NonNull YTItemResult item) {
            StringBuilder info = new StringBuilder();

            // Add type
            String type = item.getType();
            if (type != null) {
                switch (type) {
                    case "song":
                        info.append("Song");
                        break;
                    case "album":
                        info.append("Album");
                        break;
                    case "artist":
                        info.append("Artist");
                        break;
                    case "playlist":
                        info.append("Playlist");
                        break;
                }
            }

            // Add duration for songs
            if ("song".equals(type) && item.getDuration() != null) {
                String duration = formatDuration(item.getDuration());
                if (!info.toString().isEmpty()) {
                    info.append(" • ");
                }
                info.append(duration);
            }

            return info.toString();
        }

        @NonNull
        private String formatDuration(int seconds) {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            return String.format(Locale.getDefault(), "%d:%02d", minutes, secs);
        }
    }
}
