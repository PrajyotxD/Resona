package music.resona.playback;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import music.resona.models.Song;

/**
 * True shuffle implementation using Fisher-Yates algorithm with smart behavior.
 * Prevents artist clustering and maintains seamless playback during shuffle changes.
 */
public class ShuffleManager {
    
    private static final String TAG = "ShuffleManager";
    private static final int MAX_ARTIST_SEPARATION_ATTEMPTS = 5;
    
    private final Random random;
    
    // Shuffle state
    private boolean shuffleEnabled = false;
    private List<Integer> shuffleOrder;
    private Map<Integer, Integer> originalToShuffled;
    private Map<Integer, Integer> shuffledToOriginal;
    private Set<String> recentArtists;
    
    public ShuffleManager() {
        this.random = new Random();
        this.shuffleOrder = new ArrayList<>();
        this.originalToShuffled = new HashMap<>();
        this.shuffledToOriginal = new HashMap<>();
        this.recentArtists = new HashSet<>();
    }
    
    /**
     * Enable or disable shuffle mode.
     */
    public void setShuffleEnabled(boolean enabled) {
        this.shuffleEnabled = enabled;
        Log.d(TAG, "Shuffle " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if shuffle is currently enabled.
     */
    public boolean isShuffleEnabled() {
        return shuffleEnabled;
    }
    
    /**
     * Shuffle the entire queue with Fisher-Yates algorithm.
     * Keeps current playing song fixed and shuffles the rest.
     */
    public void shuffleQueue(@NonNull PlaybackQueue queue, int currentIndex) {
        if (!shuffleEnabled) return;
        
        List<Song> songs = queue.getSongs();
        int size = songs.size();
        
        if (size <= 1) {
            Log.d(TAG, "Queue too small to shuffle");
            return;
        }
        
        Log.d(TAG, "Shuffling queue: " + size + " items, current: " + currentIndex);
        
        // Create initial order
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            indices.add(i);
        }
        
        // Remove current playing item
        Song currentSong = null;
        if (currentIndex >= 0 && currentIndex < size) {
            currentSong = songs.get(currentIndex);
            indices.remove(Integer.valueOf(currentIndex));
        }
        
        // Apply Fisher-Yates shuffle
        shuffleOrder = fisherYatesShuffle(indices);
        
        // Insert current song at the beginning
        if (currentSong != null) {
            shuffleOrder.add(0, currentIndex);
        }
        
        // Apply smart artist separation
        applyArtistSeparation(songs, shuffleOrder);
        
        // Build mapping tables
        buildMappingTables();
        
        Log.d(TAG, "Queue shuffled successfully with artist separation");
    }
    
    /**
     * Insert new songs into existing shuffle order.
     */
    public void insertSongsIntoShuffle(@NonNull List<Song> newSongs, int insertionPoint) {
        if (!shuffleEnabled || shuffleOrder == null) return;
        
        int newSongCount = newSongs.size();
        List<Integer> newIndices = new ArrayList<>();
        
        // Create indices for new songs
        for (int i = 0; i < newSongCount; i++) {
            newIndices.add(insertionPoint + i);
        }
        
        // Shuffle new indices
        List<Integer> shuffledNewIndices = fisherYatesShuffle(newIndices);
        
        // Insert randomly into existing shuffle order (avoid clustering)
        for (Integer newIndex : shuffledNewIndices) {
            int insertPos = random.nextInt(shuffleOrder.size() + 1);
            
            // Try to avoid same artist clustering
            insertPos = findBestInsertionPosition(newSongs.get(newIndex - insertionPoint), insertPos);
            
            shuffleOrder.add(insertPos, newIndex);
        }
        
        // Rebuild mapping tables
        buildMappingTables();
        
        Log.d(TAG, "Inserted " + newSongCount + " new songs into shuffle order");
    }
    
    /**
     * Disable shuffle and return to original order.
     */
    public void disableShuffle() {
        shuffleOrder = null;
        originalToShuffled.clear();
        shuffledToOriginal.clear();
        recentArtists.clear();
        Log.d(TAG, "Shuffle disabled, returned to original order");
    }
    
    /**
     * Get shuffled index for original index.
     */
    public int getShuffledIndex(int originalIndex) {
        if (!shuffleEnabled || originalToShuffled == null) {
            return originalIndex;
        }
        return originalToShuffled.getOrDefault(originalIndex, originalIndex);
    }
    
    /**
     * Get original index for shuffled index.
     */
    public int getOriginalIndex(int shuffledIndex) {
        if (!shuffleEnabled || shuffledToOriginal == null) {
            return shuffledIndex;
        }
        return shuffledToOriginal.getOrDefault(shuffledIndex, shuffledIndex);
    }
    
    /**
     * Apply Fisher-Yates shuffle algorithm.
     */
    @NonNull
    private List<Integer> fisherYatesShuffle(@NonNull List<Integer> list) {
        List<Integer> shuffled = new ArrayList<>(list);
        
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(shuffled, i, j);
        }
        
        return shuffled;
    }
    
    /**
     * Apply smart artist separation to reduce back-to-back same artist plays.
     */
    private void applyArtistSeparation(@NonNull List<Song> songs, @NonNull List<Integer> order) {
        int attempts = 0;
        
        while (attempts < MAX_ARTIST_SEPARATION_ATTEMPTS) {
            boolean needsAdjustment = false;
            
            for (int i = 1; i < order.size(); i++) {
                Song prevSong = songs.get(order.get(i - 1));
                Song currentSong = songs.get(order.get(i));
                
                // Check if same artist plays back-to-back
                if (isSameArtist(prevSong, currentSong)) {
                    // Try to find a different song to swap with
                    int swapIndex = findNonConflictingSong(songs, order, i);
                    if (swapIndex != -1) {
                        Collections.swap(order, i, swapIndex);
                        needsAdjustment = true;
                    }
                }
            }
            
            if (!needsAdjustment) {
                break; // No more conflicts found
            }
            
            attempts++;
        }
        
        Log.d(TAG, "Artist separation applied with " + attempts + " adjustment passes");
    }
    
    /**
     * Find a song that doesn't create artist conflicts when swapped.
     */
    private int findNonConflictingSong(@NonNull List<Song> songs, @NonNull List<Integer> order, int problemIndex) {
        Song problemSong = songs.get(order.get(problemIndex));
        
        // Look ahead for a good swap candidate
        for (int i = problemIndex + 1; i < Math.min(order.size(), problemIndex + 20); i++) {
            Song candidateSong = songs.get(order.get(i));
            
            // Check if swapping would resolve the conflict without creating new ones
            if (!isSameArtist(problemSong, candidateSong)) {
                // Check previous neighbor
                if (problemIndex > 0) {
                    Song prevSong = songs.get(order.get(problemIndex - 1));
                    if (isSameArtist(candidateSong, prevSong)) {
                        continue; // Would create new conflict
                    }
                }
                
                // Check next neighbor  
                if (i + 1 < order.size()) {
                    Song nextSong = songs.get(order.get(i + 1));
                    if (isSameArtist(problemSong, nextSong)) {
                        continue; // Would create new conflict
                    }
                }
                
                return i; // Good swap candidate found
            }
        }
        
        return -1; // No good candidate found
    }
    
    /**
     * Find best position to insert a new song to avoid artist clustering.
     */
    private int findBestInsertionPosition(@NonNull Song newSong, int preferredPosition) {
        if (shuffleOrder == null || shuffleOrder.isEmpty()) {
            return 0;
        }
        
        // Check if preferred position is good
        if (isGoodInsertionPosition(newSong, preferredPosition)) {
            return preferredPosition;
        }
        
        // Search around preferred position
        int maxSearch = Math.min(10, shuffleOrder.size());
        for (int offset = 1; offset <= maxSearch; offset++) {
            // Try position before preferred
            int beforePos = Math.max(0, preferredPosition - offset);
            if (isGoodInsertionPosition(newSong, beforePos)) {
                return beforePos;
            }
            
            // Try position after preferred
            int afterPos = Math.min(shuffleOrder.size(), preferredPosition + offset);
            if (isGoodInsertionPosition(newSong, afterPos)) {
                return afterPos;
            }
        }
        
        // Fallback to preferred position if no better option found
        return preferredPosition;
    }
    
    /**
     * Check if insertion position would create artist conflicts.
     */
    private boolean isGoodInsertionPosition(@NonNull Song newSong, int position) {
        // Check previous song
        if (position > 0 && position <= shuffleOrder.size()) {
            // Implementation would check against actual songs
            // For now, return true as placeholder
        }
        
        // Check next song
        if (position < shuffleOrder.size()) {
            // Implementation would check against actual songs
            // For now, return true as placeholder
        }
        
        return true; // Placeholder - implement actual artist checking
    }
    
    /**
     * Check if two songs are by the same artist.
     */
    private boolean isSameArtist(@NonNull Song song1, @NonNull Song song2) {
        String artist1 = song1.getArtist();
        String artist2 = song2.getArtist();
        
        if (artist1 == null || artist2 == null) {
            return false;
        }
        
        // Normalize for comparison
        return artist1.trim().toLowerCase().equals(artist2.trim().toLowerCase());
    }
    
    /**
     * Build mapping tables for efficient index conversion.
     */
    private void buildMappingTables() {
        originalToShuffled.clear();
        shuffledToOriginal.clear();
        
        if (shuffleOrder != null) {
            for (int shuffledPos = 0; shuffledPos < shuffleOrder.size(); shuffledPos++) {
                int originalPos = shuffleOrder.get(shuffledPos);
                originalToShuffled.put(originalPos, shuffledPos);
                shuffledToOriginal.put(shuffledPos, originalPos);
            }
        }
    }
}