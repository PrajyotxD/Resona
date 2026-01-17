package music.resona.database;

import androidx.annotation.NonNull;

/**
 * Represents a user's listening history entry.
 */
public class ListeningHistory {
    
    private String videoId;
    private String title;
    private String artistName;
    private String artistId;
    private String albumName;
    private String albumId;
    private String thumbnail;
    private long timestamp;
    private int playCount;
    private int skipCount;
    private float completionRate; // 0-100%
    private long totalPlayTimeMs;
    private long lastPlayedAt;
    
    public ListeningHistory() {
        this.timestamp = System.currentTimeMillis();
        this.playCount = 0;
        this.skipCount = 0;
        this.completionRate = 0f;
        this.totalPlayTimeMs = 0;
        this.lastPlayedAt = 0;
    }
    
    @NonNull
    public String getVideoId() {
        return videoId;
    }
    
    public void setVideoId(@NonNull String videoId) {
        this.videoId = videoId;
    }
    
    @NonNull
    public String getTitle() {
        return title;
    }
    
    public void setTitle(@NonNull String title) {
        this.title = title;
    }
    
    public String getArtistName() {
        return artistName;
    }
    
    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }
    
    public String getArtistId() {
        return artistId;
    }
    
    public void setArtistId(String artistId) {
        this.artistId = artistId;
    }
    
    public String getAlbumName() {
        return albumName;
    }
    
    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }
    
    public String getAlbumId() {
        return albumId;
    }
    
    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }
    
    public String getThumbnail() {
        return thumbnail;
    }
    
    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getPlayCount() {
        return playCount;
    }
    
    public void setPlayCount(int playCount) {
        this.playCount = playCount;
    }
    
    public void incrementPlayCount() {
        this.playCount++;
    }
    
    public int getSkipCount() {
        return skipCount;
    }
    
    public void setSkipCount(int skipCount) {
        this.skipCount = skipCount;
    }
    
    public void incrementSkipCount() {
        this.skipCount++;
    }
    
    public float getCompletionRate() {
        return completionRate;
    }
    
    public void setCompletionRate(float completionRate) {
        this.completionRate = completionRate;
    }
    
    public long getTotalPlayTimeMs() {
        return totalPlayTimeMs;
    }
    
    public void setTotalPlayTimeMs(long totalPlayTimeMs) {
        this.totalPlayTimeMs = totalPlayTimeMs;
    }
    
    public void addPlayTime(long durationMs) {
        this.totalPlayTimeMs += durationMs;
    }
    
    public long getLastPlayedAt() {
        return lastPlayedAt;
    }
    
    public void setLastPlayedAt(long lastPlayedAt) {
        this.lastPlayedAt = lastPlayedAt;
    }
    
    /**
     * Calculate engagement score based on play behavior.
     * Higher score = more engaged with this content.
     */
    public float getEngagementScore() {
        float recencyScore = calculateRecencyScore();
        float playCountScore = Math.min(playCount / 10f, 1f);
        float completionScore = completionRate / 100f;
        float skipPenalty = Math.max(0, 1f - (skipCount / 5f));
        
        return (recencyScore * 0.3f + playCountScore * 0.3f + 
                completionScore * 0.2f + skipPenalty * 0.2f) * 100f;
    }
    
    private float calculateRecencyScore() {
        long hoursSinceLastPlay = (System.currentTimeMillis() - lastPlayedAt) / (1000 * 60 * 60);
        
        if (hoursSinceLastPlay < 24) return 1.0f;
        if (hoursSinceLastPlay < 24 * 7) return 0.8f;
        if (hoursSinceLastPlay < 24 * 30) return 0.5f;
        if (hoursSinceLastPlay < 24 * 90) return 0.3f;
        return 0.1f;
    }
    
    /**
     * Check if this is a "forgotten favorite" - frequently played but not recently.
     */
    public boolean isForgottenFavorite() {
        long daysSinceLastPlay = (System.currentTimeMillis() - lastPlayedAt) / (1000 * 60 * 60 * 24);
        return playCount >= 5 && daysSinceLastPlay > 30 && completionRate > 60f;
    }
}
