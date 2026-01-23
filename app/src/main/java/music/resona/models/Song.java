package music.resona.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Model representing a song with all metadata needed for playback.
 * Implements Parcelable for passing between activities/fragments.
 */
public class Song implements Parcelable {
    
    private final String videoId;
    private final String title;
    private final String artist;
    private final String album;
    private final String thumbnailUrl;
    private final int durationSeconds;
    private final String playlistId;
    
    // Artist browse IDs for navigation (multiple artists supported)
    private String[] artistBrowseIds;
    private String[] artistNames;
    
    // Cached stream URL for instant playback
    private String cachedStreamUrl;
    private long streamUrlExpiry;
    
    public Song(@NonNull String videoId, 
                @NonNull String title, 
                @Nullable String artist,
                @Nullable String album,
                @Nullable String thumbnailUrl,
                int durationSeconds) {
        this(videoId, title, artist, album, thumbnailUrl, durationSeconds, null);
    }
    
    public Song(@NonNull String videoId, 
                @NonNull String title, 
                @Nullable String artist,
                @Nullable String album,
                @Nullable String thumbnailUrl,
                int durationSeconds,
                @Nullable String playlistId) {
        this.videoId = videoId;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.thumbnailUrl = thumbnailUrl;
        this.durationSeconds = durationSeconds;
        this.playlistId = playlistId;
    }
    
    protected Song(Parcel in) {
        videoId = in.readString();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        thumbnailUrl = in.readString();
        durationSeconds = in.readInt();
        playlistId = in.readString();
        artistBrowseIds = in.createStringArray();
        artistNames = in.createStringArray();
        cachedStreamUrl = in.readString();
        streamUrlExpiry = in.readLong();
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(videoId);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeString(thumbnailUrl);
        dest.writeInt(durationSeconds);
        dest.writeString(playlistId);
        dest.writeStringArray(artistBrowseIds);
        dest.writeStringArray(artistNames);
        dest.writeString(cachedStreamUrl);
        dest.writeLong(streamUrlExpiry);
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public static final Creator<Song> CREATOR = new Creator<Song>() {
        @Override
        public Song createFromParcel(Parcel in) {
            return new Song(in);
        }
        
        @Override
        public Song[] newArray(int size) {
            return new Song[size];
        }
    };
    
    // Getters
    @NonNull
    public String getVideoId() {
        return videoId;
    }
    
    @NonNull
    public String getTitle() {
        return title;
    }
    
    @Nullable
    public String getArtist() {
        return artist;
    }
    
    @Nullable
    public String getAlbum() {
        return album;
    }
    
    @Nullable
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
    
    public int getDurationSeconds() {
        return durationSeconds;
    }
    
    @Nullable
    public String getPlaylistId() {
        return playlistId;
    }
    
    // Stream URL caching for instant playback
    public void setCachedStreamUrl(String url, long expiryTimeMillis) {
        this.cachedStreamUrl = url;
        this.streamUrlExpiry = expiryTimeMillis;
    }
    
    @Nullable
    public String getCachedStreamUrl() {
        if (cachedStreamUrl != null && System.currentTimeMillis() < streamUrlExpiry) {
            return cachedStreamUrl;
        }
        return null;
    }
    
    public boolean hasValidStreamUrl() {
        return cachedStreamUrl != null && System.currentTimeMillis() < streamUrlExpiry;
    }
    
    // Artist browse ID methods
    public void setArtistInfo(@Nullable String[] browseIds, @Nullable String[] names) {
        this.artistBrowseIds = browseIds;
        this.artistNames = names;
    }
    
    @Nullable
    public String[] getArtistBrowseIds() {
        return artistBrowseIds;
    }
    
    @Nullable
    public String[] getArtistNames() {
        return artistNames;
    }
    
    public boolean hasArtistInfo() {
        return artistBrowseIds != null && artistBrowseIds.length > 0;
    }
    
    @NonNull
    public String getFormattedDuration() {
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Song song = (Song) o;
        return videoId.equals(song.videoId);
    }
    
    @Override
    public int hashCode() {
        return videoId.hashCode();
    }
    
    @NonNull
    @Override
    public String toString() {
        return "Song{" +
                "videoId='" + videoId + '\'' +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                '}';
    }
}
