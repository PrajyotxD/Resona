package music.resona.playback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import music.resona.models.Song;

/**
 * Static playlist/album queue implementation.
 * Represents a fixed list of songs without dynamic loading.
 */
public class StaticQueue implements PlaybackQueue {
    
    private final String title;
    private final List<Song> songs;
    private Song preloadItem;
    private PlaybackStatus initialStatus;
    
    public StaticQueue(@NonNull String title, @NonNull List<Song> songs) {
        this.title = title;
        this.songs = new ArrayList<>(songs); // Defensive copy
        this.initialStatus = PlaybackStatus.createAutoPlay();
    }
    
    public StaticQueue(@NonNull String title, @NonNull List<Song> songs, @NonNull PlaybackStatus initialStatus) {
        this.title = title;
        this.songs = new ArrayList<>(songs);
        this.initialStatus = initialStatus;
    }
    
    @NonNull
    @Override
    public String getTitle() {
        return title;
    }
    
    @NonNull
    @Override
    public QueueManager.QueueType getType() {
        return QueueManager.QueueType.STATIC;
    }
    
    @NonNull
    @Override
    public List<Song> getSongs() {
        return Collections.unmodifiableList(songs);
    }
    
    @Override
    public int size() {
        return songs.size();
    }
    
    @Override
    public boolean isEmpty() {
        return songs.isEmpty();
    }
    
    @Override
    public void addSongs(@NonNull List<Song> newSongs) {
        songs.addAll(newSongs);
    }
    
    @Override
    public void removeSongAt(int index) {
        if (index >= 0 && index < songs.size()) {
            songs.remove(index);
        }
    }
    
    @Nullable
    @Override
    public Song getPreloadItem() {
        return preloadItem != null ? preloadItem : (songs.isEmpty() ? null : songs.get(0));
    }
    
    @Override
    public void setPreloadItem(@Nullable Song song) {
        this.preloadItem = song;
    }
    
    @Override
    public boolean supportsPagination() {
        return false; // Static queues don't support pagination
    }
    
    @Nullable
    @Override
    public String getContinuationToken() {
        return null; // No pagination support
    }
    
    @Override
    public void loadNextPage(@NonNull PaginationCallback callback) {
        callback.onError("Static queue does not support pagination");
    }
    
    @NonNull
    @Override
    public PlaybackStatus getInitialStatus() {
        return initialStatus;
    }
    
    @NonNull
    @Override
    public PlaybackQueue copy() {
        return new StaticQueue(title, new ArrayList<>(songs), initialStatus);
    }
    
    /**
     * Builder for creating static queues with configuration.
     */
    public static class Builder {
        private String title = "";
        private List<Song> songs = new ArrayList<>();
        private PlaybackStatus initialStatus = PlaybackStatus.createAutoPlay();
        
        public Builder setTitle(@NonNull String title) {
            this.title = title;
            return this;
        }
        
        public Builder addSong(@NonNull Song song) {
            this.songs.add(song);
            return this;
        }
        
        public Builder addSongs(@NonNull List<Song> songs) {
            this.songs.addAll(songs);
            return this;
        }
        
        public Builder setInitialStatus(@NonNull PlaybackStatus status) {
            this.initialStatus = status;
            return this;
        }
        
        public Builder autoPlay() {
            this.initialStatus = PlaybackStatus.createAutoPlay();
            return this;
        }
        
        public Builder autoPlay(int startIndex) {
            this.initialStatus = PlaybackStatus.createAutoPlay(startIndex);
            return this;
        }
        
        public Builder paused() {
            this.initialStatus = PlaybackStatus.createPaused();
            return this;
        }
        
        public Builder paused(int startIndex, long startPosition) {
            this.initialStatus = PlaybackStatus.createPaused(startIndex, startPosition);
            return this;
        }
        
        public StaticQueue build() {
            return new StaticQueue(title, songs, initialStatus);
        }
    }
}