package music.resona.lyrics.providers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import music.resona.lyrics.LyricsProvider;
import music.resona.lyrics.LyricsResult;
import music.resona.online.bridge.InnertubeBridge;

/**
 * YouTube Music lyrics provider
 * 
 * Gets official lyrics from YouTube Music via InnertubeBridge.
 * These are plain text lyrics (not time-synced) provided by YouTube.
 */
public class YouTubeMusicProvider implements LyricsProvider {
    
    private static final String TAG = "YouTubeMusicProvider";
    
    // Video ID is required for this provider (stored separately)
    private String currentVideoId;
    
    @NonNull
    @Override
    public String getName() {
        return "YouTube Music";
    }
    
    @Override
    public int getPriority() {
        return 4; // Lower priority since lyrics are not synced
    }
    
    @Override
    public boolean supportsSyncedLyrics() {
        return false; // YouTube Music provides plain text only
    }
    
    /**
     * Set the video ID for fetching lyrics
     */
    public void setVideoId(@Nullable String videoId) {
        this.currentVideoId = videoId;
    }
    
    @Nullable
    @Override
    public LyricsResult fetchLyrics(@NonNull String title, @NonNull String artist,
                                    @Nullable String album, long durationMs) {
        if (currentVideoId == null || currentVideoId.isEmpty()) {
            Log.d(TAG, "No video ID set, skipping YouTube Music lyrics");
            return null;
        }
        
        try {
            Log.d(TAG, "Fetching YouTube Music lyrics for: " + currentVideoId);
            
            // Use the new method that fetches lyrics by videoId
            // This properly gets the lyrics browse endpoint from the next() call
            String lyrics = InnertubeBridge.getLyricsByVideoIdSync(currentVideoId);
            
            if (lyrics != null && !lyrics.isEmpty() && !lyrics.equals("No lyrics available")) {
                Log.d(TAG, "Found YouTube Music lyrics (" + lyrics.length() + " chars)");
                return LyricsResult.plain(getName(), lyrics);
            } else {
                Log.d(TAG, "No lyrics available from YouTube Music");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching YouTube Music lyrics", e);
        }
        
        return null;
    }
}
