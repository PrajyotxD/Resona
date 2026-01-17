package music.resona.playback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import music.resona.models.Song;
import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.callbacks.NextPageCallback;
import music.resona.online.bridge.exceptions.BridgeException;
import music.resona.online.bridge.models.NextPageResult;
import music.resona.online.bridge.models.YTItemResult;

/**
 * YouTube dynamic radio queue with continuation support.
 * Loads content dynamically as needed with InnerTube integration.
 */
public class YouTubeDynamicQueue implements PlaybackQueue {
    
    private static final String TAG = "YouTubeDynamicQueue";
    
    private final String title;
    private final String initialVideoId;
    private final List<Song> songs;
    private Song preloadItem;
    private String continuationToken;
    private PlaybackStatus initialStatus;
    private boolean isLoading = false;
    
    public YouTubeDynamicQueue(@NonNull String title, @NonNull String videoId) {
        this.title = title;
        this.initialVideoId = videoId;
        this.songs = new ArrayList<>();
        this.initialStatus = PlaybackStatus.createAutoPlay();
        
        // Create initial song from videoId
        this.preloadItem = createSongFromVideoId(videoId);
        if (preloadItem != null) {
            songs.add(preloadItem);
        }
    }
    
    public YouTubeDynamicQueue(@NonNull String title, @NonNull List<Song> initialSongs, @Nullable String continuation) {
        this.title = title;
        this.initialVideoId = initialSongs.isEmpty() ? null : initialSongs.get(0).getVideoId();
        this.songs = new ArrayList<>(initialSongs);
        this.continuationToken = continuation;
        this.preloadItem = initialSongs.isEmpty() ? null : initialSongs.get(0);
        this.initialStatus = PlaybackStatus.createAutoPlay();
    }
    
    @NonNull
    @Override
    public String getTitle() {
        return title;
    }
    
    @NonNull
    @Override
    public QueueManager.QueueType getType() {
        return QueueManager.QueueType.YOUTUBE_DYNAMIC;
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
        return true; // YouTube dynamic queues support continuation
    }
    
    @Nullable
    @Override
    public String getContinuationToken() {
        return continuationToken;
    }
    
    @Override
    public void loadNextPage(@NonNull PaginationCallback callback) {
        if (isLoading) {
            callback.onError("Already loading next page");
            return;
        }
        
        if (continuationToken == null) {
            callback.onError("No continuation token available");
            return;
        }
        
        isLoading = true;
        
        InnertubeBridge.getNextAsync(continuationToken, new NextPageCallback() {
            @Override
            public void onSuccess(@NonNull NextPageResult result) {
                isLoading = false;
                
                List<Song> newSongs = new ArrayList<>();
                if (result.getItems() != null) {
                    for (YTItemResult item : result.getItems()) {
                        if ("song".equals(item.getType())) {
                            Song song = convertToSong(item);
                            if (song != null) {
                                newSongs.add(song);
                            }
                        }
                    }
                }
                
                // Update continuation token
                continuationToken = result.getContinuation();
                
                callback.onPageLoaded(newSongs, continuationToken);
            }
            
            @Override
            public void onError(@NonNull BridgeException error) {
                isLoading = false;
                callback.onError("Failed to load next page: " + error.getMessage());
            }
        });
    }
    
    @NonNull
    @Override
    public PlaybackStatus getInitialStatus() {
        return initialStatus;
    }
    
    @NonNull
    @Override
    public PlaybackQueue copy() {
        return new YouTubeDynamicQueue(title, new ArrayList<>(songs), continuationToken);
    }
    
    /**
     * Create a Song object from video ID (placeholder implementation).
     */
    @Nullable
    private Song createSongFromVideoId(@NonNull String videoId) {
        // This would typically fetch song metadata
        // For now, create a placeholder
        return new Song(
            videoId,
            "Loading...", // title
            "Unknown Artist", // artist
            "", // artistId
            "", // thumbnail
            0, // duration
            null // album
        );
    }
    
    /**
     * Convert YTItemResult to Song object.
     */
    @Nullable
    private Song convertToSong(@NonNull YTItemResult item) {
        String artist = "";
        String artistId = "";
        
        if (item.getArtists() != null && !item.getArtists().isEmpty()) {
            artist = item.getArtists().get(0).getName();
            artistId = item.getArtists().get(0).getId();
        }
        
        return new Song(
            item.getId(),
            item.getTitle(),
            artist,
            artistId,
            item.getThumbnail(),
            item.getDuration() != null ? item.getDuration() : 0,
            null // album - could be populated from item.getAlbum()
        );
    }
    
    /**
     * Builder for creating YouTube dynamic queues.
     */
    public static class Builder {
        private String title = "Radio";
        private String videoId;
        private List<Song> initialSongs = new ArrayList<>();
        private String continuation;
        private PlaybackStatus initialStatus = PlaybackStatus.createAutoPlay();
        
        public Builder setTitle(@NonNull String title) {
            this.title = title;
            return this;
        }
        
        public Builder setVideoId(@NonNull String videoId) {
            this.videoId = videoId;
            return this;
        }
        
        public Builder setInitialSongs(@NonNull List<Song> songs) {
            this.initialSongs = new ArrayList<>(songs);
            return this;
        }
        
        public Builder setContinuation(@Nullable String continuation) {
            this.continuation = continuation;
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
        
        public YouTubeDynamicQueue build() {
            if (!initialSongs.isEmpty()) {
                return new YouTubeDynamicQueue(title, initialSongs, continuation);
            } else if (videoId != null) {
                return new YouTubeDynamicQueue(title, videoId);
            } else {
                throw new IllegalStateException("Either videoId or initialSongs must be provided");
            }
        }
    }
}