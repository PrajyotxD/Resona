package music.resona.lyrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Interface for lyrics providers.
 * Each provider implements this to fetch lyrics from a specific source.
 */
public interface LyricsProvider {
    
    /**
     * Provider name for logging and identification
     */
    @NonNull
    String getName();
    
    /**
     * Provider priority (lower = higher priority)
     * Used when multiple providers return results
     */
    int getPriority();
    
    /**
     * Whether this provider supports time-synced lyrics (LRC format)
     */
    boolean supportsSyncedLyrics();
    
    /**
     * Fetch lyrics for a song
     * 
     * @param title Song title
     * @param artist Artist name
     * @param album Album name (optional, may improve accuracy)
     * @param durationMs Song duration in milliseconds (optional, may improve accuracy)
     * @return LyricsResult containing lyrics data, or null if not found
     */
    @Nullable
    LyricsResult fetchLyrics(@NonNull String title, @NonNull String artist, 
                             @Nullable String album, long durationMs);
}
