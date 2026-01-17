package music.resona.playback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import music.resona.models.Song;

/**
 * Interface for all queue types in the playback system.
 * Supports preload items, pagination, and immutable operations.
 */
public interface PlaybackQueue {
    
    /**
     * Get queue title for display.
     */
    @NonNull
    String getTitle();
    
    /**
     * Get queue type.
     */
    @NonNull
    QueueManager.QueueType getType();
    
    /**
     * Get all songs in the queue (immutable view).
     */
    @NonNull
    List<Song> getSongs();
    
    /**
     * Get number of songs in queue.
     */
    int size();
    
    /**
     * Check if queue is empty.
     */
    boolean isEmpty();
    
    /**
     * Add songs to the end of queue.
     */
    void addSongs(@NonNull List<Song> songs);
    
    /**
     * Remove song at specific index.
     */
    void removeSongAt(int index);
    
    /**
     * Get preload item for instant playback start.
     */
    @Nullable
    Song getPreloadItem();
    
    /**
     * Set preload item for next playback.
     */
    void setPreloadItem(@Nullable Song song);
    
    /**
     * Check if queue supports pagination (dynamic loading).
     */
    boolean supportsPagination();
    
    /**
     * Get continuation token for pagination (if supported).
     */
    @Nullable
    String getContinuationToken();
    
    /**
     * Load next page of content (for dynamic queues).
     */
    void loadNextPage(@NonNull PaginationCallback callback);
    
    /**
     * Get initial playback status.
     */
    @NonNull
    PlaybackStatus getInitialStatus();
    
    /**
     * Create a copy of this queue for immutable operations.
     */
    @NonNull
    PlaybackQueue copy();
    
    /**
     * Callback interface for pagination operations.
     */
    interface PaginationCallback {
        void onPageLoaded(@NonNull List<Song> newSongs, @Nullable String nextContinuation);
        void onError(@NonNull String error);
    }
    
    /**
     * Initial playback status for queue.
     */
    class PlaybackStatus {
        private final boolean shouldStartPlaying;
        private final int startIndex;
        private final long startPosition;
        
        public PlaybackStatus(boolean shouldStartPlaying, int startIndex, long startPosition) {
            this.shouldStartPlaying = shouldStartPlaying;
            this.startIndex = Math.max(0, startIndex);
            this.startPosition = Math.max(0, startPosition);
        }
        
        public boolean shouldStartPlaying() {
            return shouldStartPlaying;
        }
        
        public int getStartIndex() {
            return startIndex;
        }
        
        public long getStartPosition() {
            return startPosition;
        }
        
        public static PlaybackStatus createAutoPlay() {
            return new PlaybackStatus(true, 0, 0);
        }
        
        public static PlaybackStatus createAutoPlay(int startIndex) {
            return new PlaybackStatus(true, startIndex, 0);
        }
        
        public static PlaybackStatus createPaused() {
            return new PlaybackStatus(false, 0, 0);
        }
        
        public static PlaybackStatus createPaused(int startIndex, long startPosition) {
            return new PlaybackStatus(false, startIndex, startPosition);
        }
    }
}