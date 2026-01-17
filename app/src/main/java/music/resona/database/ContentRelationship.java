package music.resona.database;

import androidx.annotation.NonNull;

/**
 * Represents relationships between content (songs/artists/albums).
 * Used to build a local recommendation graph.
 */
public class ContentRelationship {
    
    @NonNull
    private String sourceId;
    @NonNull
    private String targetId;
    @NonNull
    private RelationType relationType;
    private float strength; // 0-100, how strong the relationship is
    private long createdAt;
    private long updatedAt;
    private int coOccurrenceCount; // How many times they were played together
    
    public enum RelationType {
        SONG_TO_SONG,       // Related songs
        SONG_TO_ARTIST,     // Song belongs to artist
        SONG_TO_ALBUM,      // Song in album
        ARTIST_TO_ARTIST,   // Similar artists
        ALBUM_TO_ALBUM,     // Similar albums
        USER_PLAYLIST       // User-created relationship
    }
    
    public ContentRelationship() {
        this.strength = 1f;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.coOccurrenceCount = 1;
    }
    
    public ContentRelationship(@NonNull String sourceId, @NonNull String targetId, 
                               @NonNull RelationType relationType) {
        this();
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.relationType = relationType;
    }
    
    @NonNull
    public String getSourceId() {
        return sourceId;
    }
    
    public void setSourceId(@NonNull String sourceId) {
        this.sourceId = sourceId;
    }
    
    @NonNull
    public String getTargetId() {
        return targetId;
    }
    
    public void setTargetId(@NonNull String targetId) {
        this.targetId = targetId;
    }
    
    @NonNull
    public RelationType getRelationType() {
        return relationType;
    }
    
    public void setRelationType(@NonNull RelationType relationType) {
        this.relationType = relationType;
    }
    
    public float getStrength() {
        return strength;
    }
    
    public void setStrength(float strength) {
        this.strength = Math.min(100f, Math.max(0f, strength));
    }
    
    public void incrementStrength(float amount) {
        this.strength = Math.min(100f, this.strength + amount);
        this.updatedAt = System.currentTimeMillis();
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public int getCoOccurrenceCount() {
        return coOccurrenceCount;
    }
    
    public void setCoOccurrenceCount(int coOccurrenceCount) {
        this.coOccurrenceCount = coOccurrenceCount;
    }
    
    public void incrementCoOccurrence() {
        this.coOccurrenceCount++;
        // Increase strength based on co-occurrence
        incrementStrength(5f);
    }
    
    /**
     * Get unique key for this relationship.
     */
    public String getKey() {
        return sourceId + "_" + targetId + "_" + relationType.name();
    }
}
