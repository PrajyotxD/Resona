package music.resona.recommendation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a personalized recommendation section.
 */
public class RecommendationSection {
    
    public enum SectionType {
        RECENTLY_PLAYED,
        ON_REPEAT,
        BECAUSE_YOU_LISTENED,
        FORGOTTEN_FAVORITES,
        SIMILAR_ARTISTS,
        PERSONALIZED_MIX,
        DISCOVER_WEEKLY,
        RELATED_TO_SEARCH
    }
    
    @NonNull
    private String title;
    
    @Nullable
    private String reason;
    
    @NonNull
    private SectionType type;
    
    private float confidence; // 0-100, how confident we are in this recommendation
    
    // Seed content that triggered this recommendation
    @Nullable
    private String seedVideoId;
    
    @Nullable
    private String seedArtistId;
    
    @Nullable
    private List<String> seedArtistIds;
    
    // Related content IDs (to be fetched from API)
    @NonNull
    private List<String> relatedVideoIds;
    
    public RecommendationSection() {
        this.relatedVideoIds = new ArrayList<>();
        this.confidence = 50f;
        this.title = "";
        this.type = SectionType.RELATED_TO_SEARCH;
    }
    
    @NonNull
    public String getTitle() {
        return title;
    }
    
    public void setTitle(@NonNull String title) {
        this.title = title;
    }
    
    @Nullable
    public String getReason() {
        return reason;
    }
    
    public void setReason(@Nullable String reason) {
        this.reason = reason;
    }
    
    @NonNull
    public SectionType getType() {
        return type;
    }
    
    public void setType(@NonNull SectionType type) {
        this.type = type;
    }
    
    public float getConfidence() {
        return confidence;
    }
    
    public void setConfidence(float confidence) {
        this.confidence = Math.min(100f, Math.max(0f, confidence));
    }
    
    @Nullable
    public String getSeedVideoId() {
        return seedVideoId;
    }
    
    public void setSeedVideoId(@Nullable String seedVideoId) {
        this.seedVideoId = seedVideoId;
    }
    
    @Nullable
    public String getSeedArtistId() {
        return seedArtistId;
    }
    
    public void setSeedArtistId(@Nullable String seedArtistId) {
        this.seedArtistId = seedArtistId;
    }
    
    @Nullable
    public List<String> getSeedArtistIds() {
        return seedArtistIds;
    }
    
    public void setSeedArtistIds(@Nullable List<String> seedArtistIds) {
        this.seedArtistIds = seedArtistIds;
    }
    
    @NonNull
    public List<String> getRelatedVideoIds() {
        return relatedVideoIds;
    }
    
    public void setRelatedVideoIds(@NonNull List<String> relatedVideoIds) {
        this.relatedVideoIds = relatedVideoIds;
    }
    
    /**
     * Check if this section needs API data to be fetched.
     */
    public boolean needsApiFetch() {
        return (seedVideoId != null || seedArtistId != null || 
                (seedArtistIds != null && !seedArtistIds.isEmpty())) &&
                relatedVideoIds.isEmpty();
    }
    
    /**
     * Check if this section has enough content to display.
     */
    public boolean hasContent() {
        return relatedVideoIds != null && !relatedVideoIds.isEmpty();
    }
}
