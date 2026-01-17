package music.resona.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.ArrayList;
import java.util.List;

import music.resona.R;
import music.resona.online.bridge.models.YTItemResult;

/**
 * Adapter for displaying recommendation cards in horizontal RecyclerView.
 * Used for "Similar to" sections with circular artist images.
 */
public class RecommendationCardAdapter extends RecyclerView.Adapter<RecommendationCardAdapter.ViewHolder> {

    private final List<YTItemResult> items;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(YTItemResult item);
    }

    public RecommendationCardAdapter() {
        this.items = new ArrayList<>();
    }

    public void setItems(List<YTItemResult> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommendation_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        YTItemResult item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivThumbnail;
        private final TextView tvTitle;
        private final TextView tvSubtitle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.ivThumbnail);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
        }

        public void bind(YTItemResult item, OnItemClickListener listener) {
            if (item == null) return;

            // Set title
            tvTitle.setText(item.getTitle() != null ? item.getTitle() : "Unknown");

            // Set subtitle (artist or album info)
            String subtitle = "Artist";
            if (item.getArtists() != null && !item.getArtists().isEmpty()) {
                subtitle = item.getArtists().get(0).getName();
            } else if (item.getType() != null) {
                subtitle = item.getType();
            }
            tvSubtitle.setText(subtitle);

            // Load circular thumbnail
            String thumbnailUrl = item.getThumbnail();

            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(thumbnailUrl)
                        .transform(new CenterCrop())
                        .placeholder(R.drawable.placeholder_album)
                        .error(R.drawable.placeholder_album)
                        .into(ivThumbnail);
            } else {
                ivThumbnail.setImageResource(R.drawable.placeholder_album);
            }

            // Click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }
    }
}
