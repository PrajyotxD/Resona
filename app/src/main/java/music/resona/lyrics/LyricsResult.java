package music.resona.lyrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Result object containing lyrics data from a provider.
 * Supports both time-synced (LRC) and plain text lyrics.
 */
public class LyricsResult {
    
    private final String providerName;
    private final boolean isSynced;
    private final String plainLyrics;
    private final List<SyncedLine> syncedLines;
    
    /**
     * Represents a single line of synced lyrics with timestamp
     */
    public static class SyncedLine {
        private final long timeMs;  // Timestamp in milliseconds
        private final String text;
        
        public SyncedLine(long timeMs, @NonNull String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
        
        public long getTimeMs() {
            return timeMs;
        }
        
        @NonNull
        public String getText() {
            return text;
        }
        
        /**
         * Convert to LRC format line: [mm:ss.xx]text
         */
        @NonNull
        public String toLrcLine() {
            long totalSeconds = timeMs / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            long hundredths = (timeMs % 1000) / 10;
            return String.format("[%02d:%02d.%02d]%s", minutes, seconds, hundredths, text);
        }
    }
    
    private LyricsResult(@NonNull String providerName, boolean isSynced, 
                        @Nullable String plainLyrics, @Nullable List<SyncedLine> syncedLines) {
        this.providerName = providerName;
        this.isSynced = isSynced;
        this.plainLyrics = plainLyrics;
        this.syncedLines = syncedLines != null ? new ArrayList<>(syncedLines) : new ArrayList<>();
    }
    
    /**
     * Create a result with plain (unsynced) lyrics
     */
    @NonNull
    public static LyricsResult plain(@NonNull String providerName, @NonNull String lyrics) {
        return new LyricsResult(providerName, false, lyrics, null);
    }
    
    /**
     * Create a result with synced lyrics
     */
    @NonNull
    public static LyricsResult synced(@NonNull String providerName, @NonNull List<SyncedLine> lines) {
        return new LyricsResult(providerName, true, null, lines);
    }
    
    /**
     * Create a result with both synced and plain lyrics
     */
    @NonNull
    public static LyricsResult both(@NonNull String providerName, @NonNull List<SyncedLine> syncedLines,
                                   @NonNull String plainLyrics) {
        return new LyricsResult(providerName, true, plainLyrics, syncedLines);
    }
    
    @NonNull
    public String getProviderName() {
        return providerName;
    }
    
    public boolean isSynced() {
        return isSynced;
    }
    
    /**
     * Get plain text lyrics (lines separated by newlines)
     */
    @NonNull
    public String getPlainLyrics() {
        if (plainLyrics != null) {
            return plainLyrics;
        }
        // Convert synced lines to plain text
        StringBuilder sb = new StringBuilder();
        for (SyncedLine line : syncedLines) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(line.getText());
        }
        return sb.toString();
    }
    
    /**
     * Get synced lyrics lines
     */
    @NonNull
    public List<SyncedLine> getSyncedLines() {
        return new ArrayList<>(syncedLines);
    }
    
    /**
     * Get lyrics in LRC format
     */
    @NonNull
    public String toLrc() {
        if (!isSynced || syncedLines.isEmpty()) {
            return plainLyrics != null ? plainLyrics : "";
        }
        StringBuilder sb = new StringBuilder();
        for (SyncedLine line : syncedLines) {
            sb.append(line.toLrcLine()).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * Parse LRC format string into synced lines
     */
    @NonNull
    public static List<SyncedLine> parseLrc(@NonNull String lrc) {
        List<SyncedLine> lines = new ArrayList<>();
        String[] lrcLines = lrc.split("\n");
        
        for (String line : lrcLines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // Match [mm:ss.xx] or [mm:ss:xx] or [mm:ss] patterns
            int bracketEnd = line.indexOf(']');
            if (bracketEnd > 0 && line.startsWith("[")) {
                String timestamp = line.substring(1, bracketEnd);
                String text = line.substring(bracketEnd + 1).trim();
                
                // Skip metadata tags like [ar:Artist]
                if (timestamp.contains(":") && !Character.isLetter(timestamp.charAt(0))) {
                    try {
                        long timeMs = parseTimestamp(timestamp);
                        if (timeMs >= 0) {
                            lines.add(new SyncedLine(timeMs, text));
                        }
                    } catch (Exception ignored) {
                        // Skip malformed lines
                    }
                }
            }
        }
        
        return lines;
    }
    
    /**
     * Parse timestamp string (mm:ss.xx or mm:ss:xx or mm:ss) to milliseconds
     */
    private static long parseTimestamp(@NonNull String timestamp) {
        // Handle different formats: mm:ss.xx, mm:ss:xx, mm:ss
        String[] parts = timestamp.replace(".", ":").split(":");
        if (parts.length < 2) return -1;
        
        try {
            long minutes = Long.parseLong(parts[0]);
            long seconds = Long.parseLong(parts[1]);
            long hundredths = parts.length > 2 ? Long.parseLong(parts[2]) : 0;
            
            // Handle case where hundredths might be in different format
            if (parts.length > 2 && parts[2].length() == 3) {
                // It's milliseconds, not hundredths
                return (minutes * 60 + seconds) * 1000 + hundredths;
            }
            
            return (minutes * 60 + seconds) * 1000 + hundredths * 10;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    
    /**
     * Find the current line index for a given playback position
     */
    public int findLineIndexAt(long positionMs) {
        if (!isSynced || syncedLines.isEmpty()) return -1;
        
        int index = -1;
        for (int i = 0; i < syncedLines.size(); i++) {
            if (syncedLines.get(i).getTimeMs() <= positionMs) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }
}
