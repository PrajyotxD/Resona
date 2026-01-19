package music.resona.lyrics.providers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import music.resona.lyrics.LyricsProvider;
import music.resona.lyrics.LyricsResult;
import music.resona.online.bridge.InnertubeBridge;

/**
 * YouTube Subtitles/Transcript provider
 * 
 * Gets lyrics from YouTube video subtitles/captions via InnertubeBridge.
 * These are time-synced when available.
 */
public class YouTubeSubtitlesProvider implements LyricsProvider {
    
    private static final String TAG = "YouTubeSubtitles";
    
    // Video ID is required for this provider
    private String currentVideoId;
    
    @NonNull
    @Override
    public String getName() {
        return "YouTube Subtitles";
    }
    
    @Override
    public int getPriority() {
        return 3;
    }
    
    @Override
    public boolean supportsSyncedLyrics() {
        return true; // Transcripts can be time-synced
    }
    
    /**
     * Set the video ID for fetching subtitles
     */
    public void setVideoId(@Nullable String videoId) {
        this.currentVideoId = videoId;
    }
    
    @Nullable
    @Override
    public LyricsResult fetchLyrics(@NonNull String title, @NonNull String artist,
                                    @Nullable String album, long durationMs) {
        if (currentVideoId == null || currentVideoId.isEmpty()) {
            Log.d(TAG, "No video ID set, skipping YouTube subtitles");
            return null;
        }
        
        try {
            Log.d(TAG, "Fetching YouTube transcript for: " + currentVideoId);
            
            String transcript = InnertubeBridge.getTranscriptSync(currentVideoId);
            
            if (transcript != null && !transcript.isEmpty()) {
                // Parse transcript - it comes in format with timestamps
                // Format varies: could be plain text or structured
                List<LyricsResult.SyncedLine> syncedLines = parseTranscript(transcript);
                
                if (!syncedLines.isEmpty()) {
                    Log.d(TAG, "Found synced transcript with " + syncedLines.size() + " lines");
                    return LyricsResult.synced(getName(), syncedLines);
                }
                
                // Fallback to plain text if parsing failed
                Log.d(TAG, "Returning plain transcript");
                return LyricsResult.plain(getName(), transcript);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching YouTube transcript", e);
        }
        
        return null;
    }
    
    /**
     * Parse transcript text into synced lines
     * Transcript format from YouTube can vary, try to extract timestamps
     */
    @NonNull
    private List<LyricsResult.SyncedLine> parseTranscript(@NonNull String transcript) {
        List<LyricsResult.SyncedLine> lines = new ArrayList<>();
        
        // Try parsing different formats
        
        // Format 1: "0:00 Line text\n0:05 Next line"
        Pattern pattern1 = Pattern.compile("(\\d+):(\\d+)(?::(\\d+))?\\s+(.+)");
        
        // Format 2: "[00:00.00] Line text"
        Pattern pattern2 = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\]\\s*(.*)");
        
        String[] transcriptLines = transcript.split("\n");
        
        for (String line : transcriptLines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // Try pattern 1
            Matcher matcher1 = pattern1.matcher(line);
            if (matcher1.matches()) {
                try {
                    int minutes = Integer.parseInt(matcher1.group(1));
                    int seconds = Integer.parseInt(matcher1.group(2));
                    String millisStr = matcher1.group(3);
                    int millis = millisStr != null ? Integer.parseInt(millisStr) * 10 : 0;
                    String text = matcher1.group(4).trim();
                    
                    long timeMs = (minutes * 60L + seconds) * 1000L + millis;
                    if (!text.isEmpty()) {
                        lines.add(new LyricsResult.SyncedLine(timeMs, text));
                    }
                    continue;
                } catch (Exception ignored) {}
            }
            
            // Try pattern 2
            Matcher matcher2 = pattern2.matcher(line);
            if (matcher2.matches()) {
                try {
                    int minutes = Integer.parseInt(matcher2.group(1));
                    int seconds = Integer.parseInt(matcher2.group(2));
                    String millisStr = matcher2.group(3);
                    int millis = millisStr != null ? Integer.parseInt(millisStr) * 10 : 0;
                    String text = matcher2.group(4).trim();
                    
                    long timeMs = (minutes * 60L + seconds) * 1000L + millis;
                    if (!text.isEmpty()) {
                        lines.add(new LyricsResult.SyncedLine(timeMs, text));
                    }
                } catch (Exception ignored) {}
            }
        }
        
        return lines;
    }
}
