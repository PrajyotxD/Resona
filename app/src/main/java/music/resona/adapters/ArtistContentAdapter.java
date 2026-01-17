package music.resona.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import music.resona.R;
import music.resona.databinding.ItemArtistContentBinding;
import music.resona.online.bridge.models.YTItemResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying artist content items (songs, albums, singles, videos)
 * in horizontal scrolling sections.
 */
public class ArtistContentAdapter extends RecyclerView.Adapter<ArtistContentAdapter.ContentViewHolder> {
    
    private final List<YTItemResult> items = new ArrayList<>();
    private OnItemClickListener listener;
    private final String contentType; // "song", "album", "single", "video"
    
    public interface OnItemClickListener {
        void onItemClick(YTItemResult item);
        void onMoreClick(YTItemResult item);
    }
    
    public ArtistContentAdapter(@NonNull String contentType) {
        this.contentType = contentType;
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
    
    public void submitList(@NonNull List<YTItemResult> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }
    
    public void clearItems() {
        items.clear();
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ContentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemArtistContentBinding binding = ItemArtistContentBinding.inflate(
            LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ContentViewHolder(binding);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ContentViewHolder holder, int position) {
        YTItemResult item = items.get(position);
        holder.bind(item);
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    class ContentViewHolder extends RecyclerView.ViewHolder {
        
        private final ItemArtistContentBinding binding;
        
        ContentViewHolder(@NonNull ItemArtistContentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        void bind(@NonNull YTItemResult item) {
            binding.titleText.setText(item.getTitle());
            
            // Build subtitle based on content type
            String subtitle = buildSubtitle(item);
            binding.subtitleText.setText(subtitle);
            binding.subtitleText.setVisibility(
                subtitle != null && !subtitle.isEmpty() ? View.VISIBLE : View.GONE
            );
            
            // Load thumbnail
            String thumbnailUrl = item.getThumbnail();
            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                Glide.with(binding.thumbnail.getContext())
                    .load(thumbnailUrl)
                    .apply(new RequestOptions()
                        .transform(new RoundedCorners(16))
                        .placeholder(R.drawable.placeholder_album))
                    .into(binding.thumbnail);
            } else {
                binding.thumbnail.setImageResource(R.drawable.placeholder_album);
            }
            
            // Set click listeners
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
            
            binding.moreButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMoreClick(item);
                }
            });
        }
        
        @NonNull
        private String buildSubtitle(@NonNull YTItemResult item) {
            StringBuilder subtitle = new StringBuilder();
            
            switch (contentType) {
                case "song":
                case "video":
                    // For songs/videos: show artists
                    if (item.getArtists() != null && !item.getArtists().isEmpty()) {
                        subtitle.append(item.getArtists().get(0).getName());
                    }
                    break;
                    
                case "album":
                case "single":
                    // For albums/singles: show artist
                    if (item.getArtists() != null && !item.getArtists().isEmpty()) {
                        subtitle.append(item.getArtists().get(0).getName());
                    }
                    break;
            }
            
            return subtitle.toString();
        }
    }
}
