package music.resona.activity.models;

public class SongItem {
    private String videoId;
    private String title;
    private String artist;
    private String thumbnailUrl;
    private String duration;
    private String albumTitle;
    
    public SongItem(String videoId, String title, String artist, String thumbnailUrl, String duration) {
        this.videoId = videoId;
        this.title = title;
        this.artist = artist;
        this.thumbnailUrl = thumbnailUrl;
        this.duration = duration;
    }
    
    // Getters
    public String getVideoId() { return videoId; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getDuration() { return duration; }
    public String getAlbumTitle() { return albumTitle; }
    
    // Setters
    public void setAlbumTitle(String albumTitle) { this.albumTitle = albumTitle; }
}
