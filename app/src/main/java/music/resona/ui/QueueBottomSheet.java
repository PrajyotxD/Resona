package music.resona.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

import music.resona.models.Song;
import music.resona.utils.UiUXUtil;

/**
 * Bottom sheet dialog displaying the current queue.
 * Shows song thumbnail, title, artist, and duration with Akatsuki theme.
 */
public class QueueBottomSheet extends BottomSheetDialog {
    
    private RecyclerView recyclerView;
    private QueueAdapter adapter;
    private List<Song> queueSongs;
    private int currentIndex;
    private OnQueueItemClickListener listener;
    
    public interface OnQueueItemClickListener {
        void onSongClick(int position);
        void onRemoveClick(int position);
    }
    
    public QueueBottomSheet(@NonNull Context context, @NonNull List<Song> songs, 
                           int currentPosition, OnQueueItemClickListener listener) {
        super(context);
        this.queueSongs = songs;
        this.currentIndex = currentPosition;
        this.listener = listener;
        setupUI();
    }
    
    private void setupUI() {
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(0), dp(12), dp(0), dp(0));
        
        // Akatsuki dark background
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#14141c")); // Akatsuki accent
        background.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        container.setBackground(background);
        
        // Drag handle
        View handle = new View(getContext());
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0x40FFFFFF);
        handleBg.setCornerRadius(dp(3));
        handle.setBackground(handleBg);
        
        FrameLayout handleContainer = new FrameLayout(getContext());
        handleContainer.setPadding(0, dp(8), 0, dp(16));
        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(dp(40), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleContainer.addView(handle, handleParams);
        container.addView(handleContainer);
        
        // Header
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(8), dp(20), dp(16));
        
        TextView title = new TextView(getContext());
        title.setText("Queue");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        UiUXUtil.typeface(getContext(), title, "akatski.ttf", Typeface.BOLD);
        
        TextView count = new TextView(getContext());
        count.setText(queueSongs.size() + " songs");
        count.setTextSize(14);
        count.setTextColor(0x99FFFFFF);
        count.setPadding(dp(12), 0, 0, 0);
        UiUXUtil.typeface(getContext(), count, "medium.ttf", Typeface.NORMAL);
        
        header.addView(title);
        header.addView(count);
        container.addView(header);
        
        // Divider
        View divider = new View(getContext());
        divider.setBackgroundColor(0x20FFFFFF);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        );
        dividerParams.setMargins(dp(20), 0, dp(20), dp(8));
        container.addView(divider, dividerParams);
        
        // RecyclerView for queue items
        recyclerView = new RecyclerView(getContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new QueueAdapter(getContext(), queueSongs, currentIndex, listener);
        recyclerView.setAdapter(adapter);
        
        LinearLayout.LayoutParams recyclerParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        recyclerParams.height = dp(400); // Max height
        container.addView(recyclerView, recyclerParams);
        
        setContentView(container);
        
        // Scroll to current playing song
        if (currentIndex >= 0 && currentIndex < queueSongs.size()) {
            recyclerView.post(() -> recyclerView.smoothScrollToPosition(currentIndex));
        }
    }
    
    public void updateQueue(@NonNull List<Song> songs, int currentPosition) {
        this.queueSongs = songs;
        this.currentIndex = currentPosition;
        if (adapter != null) {
            adapter.updateData(songs, currentPosition);
        }
    }
    
    private int dp(int value) {
        return (int) (value * getContext().getResources().getDisplayMetrics().density);
    }
    
    /**
     * Adapter for queue items
     */
    private static class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.ViewHolder> {
        
        private Context context;
        private List<Song> songs;
        private int currentIndex;
        private OnQueueItemClickListener listener;
        
        QueueAdapter(Context context, List<Song> songs, int currentIndex, OnQueueItemClickListener listener) {
            this.context = context;
            this.songs = songs;
            this.currentIndex = currentIndex;
            this.listener = listener;
        }
        
        void updateData(List<Song> songs, int currentIndex) {
            this.songs = songs;
            this.currentIndex = currentIndex;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = createItemView(parent.getContext());
            return (ViewHolder) itemView.getTag();
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Song song = songs.get(position);
            boolean isPlaying = position == currentIndex;
            
            // Load thumbnail
            if (song.getThumbnailUrl() != null && !song.getThumbnailUrl().isEmpty()) {
                Glide.with(context)
                    .load(song.getThumbnailUrl())
                    .transform(new RoundedCorners(dp(8)))
                    .into(holder.thumbnail);
            } else {
                holder.thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
            }
            
            // Song title
            holder.title.setText(song.getTitle());
            holder.title.setTextColor(isPlaying ? Color.parseColor("#2157e6") : Color.WHITE);
            
            // Artist name
            String artist = song.getArtist() != null ? song.getArtist() : "Unknown Artist";
            holder.artist.setText(artist);
            holder.artist.setTextColor(isPlaying ? 0xBB2157e6 : 0x99FFFFFF);
            
            // Duration
            holder.duration.setText(formatDuration(song.getDurationSeconds()));
            holder.duration.setTextColor(0x77FFFFFF);
            
            // Highlight current playing
            if (isPlaying) {
                holder.itemView.setAlpha(1.0f);
                holder.playingIndicator.setVisibility(View.VISIBLE);
            } else {
                holder.itemView.setAlpha(0.85f);
                holder.playingIndicator.setVisibility(View.GONE);
            }
            
            // Click listeners
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSongClick(position);
                }
            });
            
            holder.removeButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRemoveClick(position);
                }
            });
        }
        
        @Override
        public int getItemCount() {
            return songs.size();
        }
        
        private View createItemView(Context context) {
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER_VERTICAL);
            container.setPadding(dp(20), dp(8), dp(20), dp(8));
            container.setClickable(true);
            container.setFocusable(true);
            
            // Ripple effect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                container.setForeground(context.getDrawable(android.R.drawable.list_selector_background));
            }
            
            // Thumbnail
            FrameLayout thumbContainer = new FrameLayout(context);
            ImageView thumbnail = new ImageView(context);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setClipToOutline(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                thumbnail.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(8));
                    }
                });
            }
            FrameLayout.LayoutParams thumbParams = new FrameLayout.LayoutParams(dp(56), dp(56));
            thumbContainer.addView(thumbnail, thumbParams);
            
            // Playing indicator (animated equalizer icon)
            ImageView playingIndicator = new ImageView(context);
            playingIndicator.setImageResource(android.R.drawable.ic_media_play);
            playingIndicator.setColorFilter(Color.parseColor("#2157e6"));
            playingIndicator.setVisibility(View.GONE);
            FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(dp(24), dp(24));
            indicatorParams.gravity = Gravity.CENTER;
            thumbContainer.addView(playingIndicator, indicatorParams);
            
            container.addView(thumbContainer);
            
            // Text info
            LinearLayout textContainer = new LinearLayout(context);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            );
            textParams.setMargins(dp(12), 0, dp(12), 0);
            
            TextView title = new TextView(context);
            title.setTextSize(16);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            UiUXUtil.typeface(context, title, "akatski.ttf", Typeface.NORMAL);
            textContainer.addView(title);
            
            TextView artist = new TextView(context);
            artist.setTextSize(13);
            artist.setSingleLine(true);
            artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
            artist.setPadding(0, dp(2), 0, 0);
            UiUXUtil.typeface(context, artist, "medium.ttf", Typeface.NORMAL);
            textContainer.addView(artist);
            
            container.addView(textContainer, textParams);
            
            // Duration
            TextView duration = new TextView(context);
            duration.setTextSize(13);
            UiUXUtil.typeface(context, duration, "medium.ttf", Typeface.NORMAL);
            LinearLayout.LayoutParams durationParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            durationParams.setMargins(0, 0, dp(12), 0);
            container.addView(duration, durationParams);
            
            // Remove button
            ImageView removeButton = new ImageView(context);
            removeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            removeButton.setColorFilter(0x77FFFFFF);
            removeButton.setPadding(dp(8), dp(8), dp(8), dp(8));
            removeButton.setClickable(true);
            removeButton.setFocusable(true);
            container.addView(removeButton, new LinearLayout.LayoutParams(dp(32), dp(32)));
            
            container.setTag(new ViewHolder(container, thumbnail, title, artist, duration, removeButton, playingIndicator));
            return container;
        }
        
        private String formatDuration(int seconds) {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            return String.format("%d:%02d", minutes, secs);
        }
        
        private int dp(int value) {
            return (int) (value * context.getResources().getDisplayMetrics().density);
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView thumbnail;
            TextView title;
            TextView artist;
            TextView duration;
            ImageView removeButton;
            ImageView playingIndicator;
            
            ViewHolder(View itemView) {
                super(itemView);
            }
            
            ViewHolder(View itemView, ImageView thumbnail, TextView title, TextView artist,
                      TextView duration, ImageView removeButton, ImageView playingIndicator) {
                super(itemView);
                this.thumbnail = thumbnail;
                this.title = title;
                this.artist = artist;
                this.duration = duration;
                this.removeButton = removeButton;
                this.playingIndicator = playingIndicator;
            }
        }
    }
}
