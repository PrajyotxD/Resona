package music.resona.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.ArrayList;
import java.util.List;

import music.resona.R;
import music.resona.online.bridge.models.HomeSectionResult;
import music.resona.online.bridge.models.YTItemResult;

/**
 * Adapter for displaying recommendation sections in the home feed.
 * Each section contains a horizontal list of recommendation cards.
 */
public class RecommendationSectionAdapter extends RecyclerView.Adapter<RecommendationSectionAdapter.ViewHolder> {

    private final List<HomeSectionResult> sections;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(YTItemResult item);
        void onSectionClick(HomeSectionResult section);
    }

    public RecommendationSectionAdapter() {
        this.sections = new ArrayList<>();
    }

    public void setSections(List<HomeSectionResult> newSections) {
        sections.clear();
        if (newSections != null) {
            sections.addAll(newSections);
        }
        notifyDataSetChanged();
    }

    public void addSections(List<HomeSectionResult> newSections) {
        if (newSections != null && !newSections.isEmpty()) {
            int startPosition = sections.size();
            sections.addAll(newSections);
            notifyItemRangeInserted(startPosition, newSections.size());
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommendation_section, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HomeSectionResult section = sections.get(position);
        holder.bind(section, listener);
    }

    @Override
    public int getItemCount() {
        return sections.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivSeedThumbnail;
        private final TextView tvSectionTitle;
        private final TextView tvArtistName;
        private final TextView tvReason;
        private final ImageView ivArrow;
        private final RecyclerView rvRecommendations;
        private final RecommendationCardAdapter cardAdapter;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSeedThumbnail = itemView.findViewById(R.id.ivSeedThumbnail);
            tvSectionTitle = itemView.findViewById(R.id.tvSectionTitle);
            tvArtistName = itemView.findViewById(R.id.tvArtistName);
            tvReason = itemView.findViewById(R.id.tvReason);
            ivArrow = itemView.findViewById(R.id.ivArrow);
            rvRecommendations = itemView.findViewById(R.id.rvRecommendations);

            // Setup horizontal RecyclerView
            cardAdapter = new RecommendationCardAdapter();
            rvRecommendations.setLayoutManager(
                    new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false)
            );
            rvRecommendations.setAdapter(cardAdapter);
        }

        public void bind(HomeSectionResult section, OnItemClickListener listener) {
            if (section == null) return;

            // Extract "Similar to" info from section title
            // Expected format: "Similar to <Artist Name>" or just use section title
            String sectionTitle = section.getTitle() != null ? section.getTitle() : "Recommended";
            
            // Parse title like "Similar to KARMA" -> "Similar to" + "KARMA"
            String artistName = "";
            String prefix = "Similar to";
            
            if (sectionTitle.startsWith("Similar to ")) {
                artistName = sectionTitle.substring("Similar to ".length());
                tvSectionTitle.setText(prefix);
                tvArtistName.setText(artistName);
                tvArtistName.setVisibility(View.VISIBLE);
            } else if (sectionTitle.startsWith("Because you listened to ")) {
                artistName = sectionTitle.substring("Because you listened to ".length());
                tvSectionTitle.setText("Because you listened to");
                tvArtistName.setText(artistName);
                tvArtistName.setVisibility(View.VISIBLE);
            } else {
                // Generic title
                tvSectionTitle.setText(sectionTitle);
                tvArtistName.setVisibility(View.GONE);
            }

            // Hide reason (subtitle not available in HomeSectionResult)
            tvReason.setVisibility(View.GONE);

            // Load seed thumbnail (from first item or section thumbnail)
            String thumbnailUrl = null;
            if (section.getItems() != null && !section.getItems().isEmpty()) {
                YTItemResult firstItem = section.getItems().get(0);
                thumbnailUrl = firstItem.getThumbnail();
            }

            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(thumbnailUrl)
                        .transform(new RoundedCorners(999))
                        .placeholder(R.drawable.placeholder_album)
                        .error(R.drawable.placeholder_album)
                        .into(ivSeedThumbnail);
            } else {
                ivSeedThumbnail.setImageResource(R.drawable.placeholder_album);
            }

            // Setup card adapter
            cardAdapter.setItems(section.getItems());
            cardAdapter.setOnItemClickListener(item -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });

            // Arrow click for viewing all
            View.OnClickListener viewAllClick = v -> {
                if (listener != null) {
                    listener.onSectionClick(section);
                }
            };
            ivArrow.setOnClickListener(viewAllClick);
            itemView.findViewById(R.id.ivSeedThumbnail).setOnClickListener(viewAllClick);
        }
    }
}
