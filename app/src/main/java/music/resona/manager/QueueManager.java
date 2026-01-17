package music.resona.manager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import music.resona.models.Song;

/**
 * Advanced queue manager with smart shuffle, history tracking, and queue manipulation.
 * Phase 1 Implementation - Core functionality.
 * 
 * Features:
 * - Original queue preservation
 * - Fisher-Yates shuffle with artist spreading
 * - Play history (last 30 songs)
 * - Toggle shuffle without losing position
 * - Queue manipulation (add, remove, reorder)
 */
public class QueueManager {
    
    private static final int MAX_HISTORY_SIZE = 30;
    private static final Random random = new Random();
    
    // Core queues
    private final List<Song> originalQueue = new ArrayList<>();
    private final List<Song> shuffledQueue = new ArrayList<>();
    private final LinkedList<Song> playHistory = new LinkedList<>();
    
    // State
    private int currentIndexOriginal = -1;
    private int currentIndexShuffled = -1;
    private boolean isShuffleEnabled = false;
    private long shuffleSeed = 0; // For reproducible shuffle
    
    // Mapping between original and shuffled indices
    private final Map<Integer, Integer> originalToShuffled = new HashMap<>();
    private final Map<Integer, Integer> shuffledToOriginal = new HashMap<>();
    
    public QueueManager() {
    }
    
    /**
     * Set a new queue and optional start index.
     * Clears shuffle state but preserves history.
     */
    public void setQueue(@NonNull List<Song> songs, int startIndex) {
        originalQueue.clear();
        originalQueue.addAll(songs);
        currentIndexOriginal = Math.max(0, Math.min(startIndex, songs.size() - 1));
        
        // Clear shuffle state
        shuffledQueue.clear();
        originalToShuffled.clear();
        shuffledToOriginal.clear();
        currentIndexShuffled = -1;
        
        // If shuffle is enabled, regenerate shuffled queue
        if (isShuffleEnabled && !originalQueue.isEmpty()) {
            generateSmartShuffle();
        }
    }
    
    /**
     * Add songs to the end of the queue.
     */
    public void addToQueue(@NonNull List<Song> songs) {
        int startIndex = originalQueue.size();
        originalQueue.addAll(songs);
        
        if (isShuffleEnabled) {
            // Add to shuffled queue at random positions (except before current)
            for (Song song : songs) {
                int insertPos = currentIndexShuffled + 1 + random.nextInt(Math.max(1, shuffledQueue.size() - currentIndexShuffled));
                shuffledQueue.add(Math.min(insertPos, shuffledQueue.size()), song);
            }
            rebuildMappings();
        }
    }
    
    /**
     * Add a song to play next (after current song).
     */
    public void addNext(@NonNull Song song) {
        if (isShuffleEnabled && !shuffledQueue.isEmpty()) {
            shuffledQueue.add(currentIndexShuffled + 1, song);
            originalQueue.add(currentIndexOriginal + 1, song);
            rebuildMappings();
        } else {
            originalQueue.add(currentIndexOriginal + 1, song);
        }
    }
    
    /**
     * Remove a song from the queue by position in active queue.
     */
    public boolean removeAt(int position) {
        List<Song> activeQueue = getActiveQueue();
        if (position < 0 || position >= activeQueue.size() || position == getCurrentIndex()) {
            return false; // Can't remove current song or invalid position
        }
        
        Song songToRemove = activeQueue.get(position);
        
        if (isShuffleEnabled) {
            shuffledQueue.remove(position);
            originalQueue.remove(songToRemove);
            rebuildMappings();
            // Adjust current index if needed
            if (position < currentIndexShuffled) {
                currentIndexShuffled--;
            }
        } else {
            originalQueue.remove(position);
            if (position < currentIndexOriginal) {
                currentIndexOriginal--;
            }
        }
        
        return true;
    }
    
    /**
     * Clear the entire queue.
     */
    public void clear() {
        originalQueue.clear();
        shuffledQueue.clear();
        originalToShuffled.clear();
        shuffledToOriginal.clear();
        currentIndexOriginal = -1;
        currentIndexShuffled = -1;
    }
    
    /**
     * Move a song from one position to another in the queue.
     * Works with the active queue (shuffled or original).
     */
    public boolean moveSong(int fromPosition, int toPosition) {
        List<Song> activeQueue = isShuffleEnabled ? shuffledQueue : originalQueue;
        
        if (fromPosition < 0 || fromPosition >= activeQueue.size() ||
            toPosition < 0 || toPosition >= activeQueue.size() ||
            fromPosition == toPosition) {
            return false;
        }
        
        Song song = activeQueue.remove(fromPosition);
        activeQueue.add(toPosition, song);
        
        // Update current index if affected
        int currentIndex = getCurrentIndex();
        if (fromPosition == currentIndex) {
            if (isShuffleEnabled) {
                currentIndexShuffled = toPosition;
            } else {
                currentIndexOriginal = toPosition;
            }
        } else if (fromPosition < currentIndex && toPosition >= currentIndex) {
            // Moved from before to after current
            if (isShuffleEnabled) {
                currentIndexShuffled--;
            } else {
                currentIndexOriginal--;
            }
        } else if (fromPosition > currentIndex && toPosition <= currentIndex) {
            // Moved from after to before current
            if (isShuffleEnabled) {
                currentIndexShuffled++;
            } else {
                currentIndexOriginal++;
            }
        }
        
        if (isShuffleEnabled) {
            rebuildMappings();
        }
        
        return true;
    }
    
    /**
     * Swap two songs in the queue.
     */
    public boolean swapSongs(int position1, int position2) {
        List<Song> activeQueue = isShuffleEnabled ? shuffledQueue : originalQueue;
        
        if (position1 < 0 || position1 >= activeQueue.size() ||
            position2 < 0 || position2 >= activeQueue.size()) {
            return false;
        }
        
        Collections.swap(activeQueue, position1, position2);
        
        // Update current index if affected
        int currentIndex = getCurrentIndex();
        if (position1 == currentIndex) {
            if (isShuffleEnabled) {
                currentIndexShuffled = position2;
            } else {
                currentIndexOriginal = position2;
            }
        } else if (position2 == currentIndex) {
            if (isShuffleEnabled) {
                currentIndexShuffled = position1;
            } else {
                currentIndexOriginal = position1;
            }
        }
        
        if (isShuffleEnabled) {
            rebuildMappings();
        }
        
        return true;
    }
    
    /**
     * Remove multiple songs at once (batch operation).
     */
    public int removeSongs(@NonNull List<Integer> positions) {
        // Sort positions in descending order to avoid index shifting issues
        List<Integer> sortedPositions = new ArrayList<>(positions);
        Collections.sort(sortedPositions, Collections.reverseOrder());
        
        int removedCount = 0;
        for (int position : sortedPositions) {
            if (removeAt(position)) {
                removedCount++;
            }
        }
        
        return removedCount;
    }
    
    /**
     * Get queue statistics for analytics/debugging.
     */
    @NonNull
    public QueueStats getQueueStats() {
        return new QueueStats(
            originalQueue.size(),
            getCurrentIndex(),
            getRemainingQueue().size(),
            playHistory.size(),
            isShuffleEnabled,
            getUniqueArtistCount(),
            getUniqueAlbumCount()
        );
    }
    
    private int getUniqueArtistCount() {
        return (int) originalQueue.stream()
            .map(Song::getArtist)
            .filter(artist -> artist != null && !artist.isEmpty())
            .distinct()
            .count();
    }
    
    private int getUniqueAlbumCount() {
        return (int) originalQueue.stream()
            .map(Song::getAlbum)
            .filter(album -> album != null && !album.isEmpty())
            .distinct()
            .count();
    }
    
    /**
     * Queue statistics data class.
     */
    public static class QueueStats {
        public final int totalSongs;
        public final int currentPosition;
        public final int remainingSongs;
        public final int historySize;
        public final boolean isShuffled;
        public final int uniqueArtists;
        public final int uniqueAlbums;
        
        QueueStats(int totalSongs, int currentPosition, int remainingSongs, 
                   int historySize, boolean isShuffled, int uniqueArtists, int uniqueAlbums) {
            this.totalSongs = totalSongs;
            this.currentPosition = currentPosition;
            this.remainingSongs = remainingSongs;
            this.historySize = historySize;
            this.isShuffled = isShuffled;
            this.uniqueArtists = uniqueArtists;
            this.uniqueAlbums = uniqueAlbums;
        }
    }
    
    /**
     * Toggle shuffle mode.
     * Maintains current song position.
     */
    public void toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled;
        
        if (isShuffleEnabled) {
            generateSmartShuffle();
        } else {
            // Return to original queue, find current song's position
            Song currentSong = getCurrentSong();
            if (currentSong != null) {
                currentIndexOriginal = originalQueue.indexOf(currentSong);
            }
            shuffledQueue.clear();
            originalToShuffled.clear();
            shuffledToOriginal.clear();
        }
    }
    
    /**
     * Enable or disable shuffle.
     */
    public void setShuffle(boolean enabled) {
        if (isShuffleEnabled != enabled) {
            toggleShuffle();
        }
    }
    
    /**
     * Fisher-Yates shuffle with smart artist spreading.
     * Tries to avoid same artist playing consecutively.
     */
    private void generateSmartShuffle() {
        if (originalQueue.isEmpty()) return;
        
        Song currentSong = getCurrentSong();
        shuffledQueue.clear();
        shuffledQueue.addAll(originalQueue);
        
        // Generate new seed for shuffle
        shuffleSeed = System.currentTimeMillis();
        Random shuffleRandom = new Random(shuffleSeed);
        
        // Fisher-Yates shuffle
        for (int i = shuffledQueue.size() - 1; i > 0; i--) {
            int j = shuffleRandom.nextInt(i + 1);
            Collections.swap(shuffledQueue, i, j);
        }
        
        // Apply artist spreading (Phase 1: basic version)
        spreadArtists();
        
        // Move current song to front
        if (currentSong != null) {
            shuffledQueue.remove(currentSong);
            shuffledQueue.add(0, currentSong);
            currentIndexShuffled = 0;
        } else {
            currentIndexShuffled = 0;
        }
        
        rebuildMappings();
    }
    
    /**
     * Advanced artist/album spreading algorithm.
     * Phase 2: Considers both artist and album to create better listening flow.
     */
    private void spreadArtists() {
        if (shuffledQueue.size() < 3) return;
        
        int maxAttempts = shuffledQueue.size() * 2; // Prevent infinite loops
        int attempts = 0;
        boolean improved = true;
        
        while (improved && attempts < maxAttempts) {
            improved = false;
            attempts++;
            
            for (int i = 0; i < shuffledQueue.size() - 1; i++) {
                Song current = shuffledQueue.get(i);
                Song next = shuffledQueue.get(i + 1);
                
                // Calculate conflict score (higher = worse)
                int conflictScore = calculateConflictScore(current, next);
                
                if (conflictScore > 0) {
                    // Find best swap candidate
                    int bestSwapIndex = findBestSwapCandidate(i, current, next);
                    
                    if (bestSwapIndex > i + 1) {
                        Collections.swap(shuffledQueue, i + 1, bestSwapIndex);
                        improved = true;
                    }
                }
            }
        }
    }
    
    /**
     * Calculate conflict score between two consecutive songs.
     * Higher score means more conflict (worse placement).
     */
    private int calculateConflictScore(Song song1, Song song2) {
        int score = 0;
        
        // Same artist = +10 conflict
        if (haveSameArtist(song1, song2)) {
            score += 10;
        }
        
        // Same album = +5 conflict (less severe than same artist)
        if (haveSameAlbum(song1, song2)) {
            score += 5;
        }
        
        // Similar genre could be added here in future
        
        return score;
    }
    
    /**
     * Find the best position to swap with to reduce conflicts.
     */
    private int findBestSwapCandidate(int currentPos, Song current, Song problematicNext) {
        int searchWindow = Math.min(10, shuffledQueue.size() - currentPos - 2);
        int bestIndex = -1;
        int bestScore = Integer.MAX_VALUE;
        
        for (int j = currentPos + 2; j < currentPos + 2 + searchWindow; j++) {
            Song candidate = shuffledQueue.get(j);
            
            // Calculate how good this swap would be
            int newScore = calculateConflictScore(current, candidate);
            
            // Also check if it creates new problems
            if (j > 0) {
                Song beforeCandidate = shuffledQueue.get(j - 1);
                newScore += calculateConflictScore(beforeCandidate, problematicNext);
            }
            
            if (newScore < bestScore) {
                bestScore = newScore;
                bestIndex = j;
            }
            
            // If we found a perfect match (no conflicts), use it
            if (newScore == 0) {
                break;
            }
        }
        
        return bestIndex;
    }
    
    /**
     * Check if two songs are from the same album.
     */
    private boolean haveSameAlbum(Song song1, Song song2) {
        // Album info might not always be available
        String album1 = song1.getAlbum();
        String album2 = song2.getAlbum();
        
        if (album1 == null || album2 == null || album1.isEmpty() || album2.isEmpty()) {
            return false;
        }
        
        return album1.equalsIgnoreCase(album2);
    }
    
    /**
     * Check if two songs have the same artist.
     */
    private boolean haveSameArtist(Song song1, Song song2) {
        if (song1.getArtist() == null || song2.getArtist() == null) {
            return false;
        }
        return song1.getArtist().equalsIgnoreCase(song2.getArtist());
    }
    
    /**
     * Rebuild index mappings between original and shuffled queues.
     */
    private void rebuildMappings() {
        originalToShuffled.clear();
        shuffledToOriginal.clear();
        
        for (int i = 0; i < shuffledQueue.size(); i++) {
            Song shuffledSong = shuffledQueue.get(i);
            int originalIndex = originalQueue.indexOf(shuffledSong);
            if (originalIndex >= 0) {
                originalToShuffled.put(originalIndex, i);
                shuffledToOriginal.put(i, originalIndex);
            }
        }
    }
    
    /**
     * Move to next song in queue.
     * @return true if there's a next song, false if end of queue
     */
    public boolean moveToNext() {
        List<Song> activeQueue = getActiveQueue();
        int currentIndex = getCurrentIndex();
        
        if (currentIndex + 1 < activeQueue.size()) {
            // Add current to history
            Song current = getCurrentSong();
            if (current != null) {
                addToHistory(current);
            }
            
            if (isShuffleEnabled) {
                currentIndexShuffled++;
                currentIndexOriginal = shuffledToOriginal.getOrDefault(currentIndexShuffled, -1);
            } else {
                currentIndexOriginal++;
            }
            return true;
        }
        return false;
    }
    
    /**
     * Move to previous song.
     * First tries history, then moves back in queue.
     */
    public boolean moveToPrevious() {
        // If we have history, go to last played song
        if (!playHistory.isEmpty()) {
            Song previousSong = playHistory.removeLast();
            int index = findSongInActiveQueue(previousSong);
            if (index >= 0) {
                setCurrentIndex(index);
                return true;
            }
        }
        
        // Otherwise, move back in queue
        int currentIndex = getCurrentIndex();
        if (currentIndex > 0) {
            if (isShuffleEnabled) {
                currentIndexShuffled--;
                currentIndexOriginal = shuffledToOriginal.getOrDefault(currentIndexShuffled, -1);
            } else {
                currentIndexOriginal--;
            }
            return true;
        }
        return false;
    }
    
    /**
     * Jump to a specific index in the active queue.
     */
    public void setCurrentIndex(int index) {
        List<Song> activeQueue = getActiveQueue();
        if (index >= 0 && index < activeQueue.size()) {
            // Add current to history before jumping
            Song current = getCurrentSong();
            if (current != null) {
                addToHistory(current);
            }
            
            if (isShuffleEnabled) {
                currentIndexShuffled = index;
                currentIndexOriginal = shuffledToOriginal.getOrDefault(index, -1);
            } else {
                currentIndexOriginal = index;
            }
        }
    }
    
    /**
     * Find a song's position in the active queue.
     */
    private int findSongInActiveQueue(Song song) {
        List<Song> activeQueue = getActiveQueue();
        for (int i = 0; i < activeQueue.size(); i++) {
            Song queueSong = activeQueue.get(i);
            if (queueSong.getVideoId().equals(song.getVideoId())) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Add a song to play history.
     */
    private void addToHistory(Song song) {
        playHistory.addLast(song);
        if (playHistory.size() > MAX_HISTORY_SIZE) {
            playHistory.removeFirst();
        }
    }
    
    // ========== GETTERS ==========
    
    /**
     * Get the currently active queue (shuffled or original).
     */
    @NonNull
    public List<Song> getActiveQueue() {
        return isShuffleEnabled && !shuffledQueue.isEmpty() 
            ? new ArrayList<>(shuffledQueue) 
            : new ArrayList<>(originalQueue);
    }
    
    /**
     * Get the original queue (unshuffled).
     */
    @NonNull
    public List<Song> getOriginalQueue() {
        return new ArrayList<>(originalQueue);
    }
    
    /**
     * Get the current song.
     */
    @Nullable
    public Song getCurrentSong() {
        List<Song> activeQueue = isShuffleEnabled ? shuffledQueue : originalQueue;
        int currentIndex = isShuffleEnabled ? currentIndexShuffled : currentIndexOriginal;
        
        if (currentIndex >= 0 && currentIndex < activeQueue.size()) {
            return activeQueue.get(currentIndex);
        }
        return null;
    }
    
    /**
     * Get the current index in the active queue.
     */
    public int getCurrentIndex() {
        return isShuffleEnabled ? currentIndexShuffled : currentIndexOriginal;
    }
    
    /**
     * Get the next song without moving the index.
     */
    @Nullable
    public Song peekNext() {
        List<Song> activeQueue = getActiveQueue();
        int currentIndex = getCurrentIndex();
        
        if (currentIndex + 1 < activeQueue.size()) {
            return activeQueue.get(currentIndex + 1);
        }
        return null;
    }
    
    /**
     * Get the previous song without moving the index.
     */
    @Nullable
    public Song peekPrevious() {
        if (!playHistory.isEmpty()) {
            return playHistory.getLast();
        }
        
        int currentIndex = getCurrentIndex();
        if (currentIndex > 0) {
            List<Song> activeQueue = getActiveQueue();
            return activeQueue.get(currentIndex - 1);
        }
        return null;
    }
    
    /**
     * Check if there's a next song available.
     */
    public boolean hasNext() {
        return getCurrentIndex() + 1 < getActiveQueue().size();
    }
    
    /**
     * Check if there's a previous song available.
     */
    public boolean hasPrevious() {
        return !playHistory.isEmpty() || getCurrentIndex() > 0;
    }
    
    /**
     * Get the queue size.
     */
    public int size() {
        return originalQueue.size();
    }
    
    /**
     * Check if queue is empty.
     */
    public boolean isEmpty() {
        return originalQueue.isEmpty();
    }
    
    /**
     * Check if shuffle is enabled.
     */
    public boolean isShuffleEnabled() {
        return isShuffleEnabled;
    }
    
    /**
     * Get play history.
     */
    @NonNull
    public List<Song> getPlayHistory() {
        return new ArrayList<>(playHistory);
    }
    
    /**
     * Get remaining songs in queue (after current).
     */
    @NonNull
    public List<Song> getRemainingQueue() {
        List<Song> activeQueue = getActiveQueue();
        int currentIndex = getCurrentIndex();
        
        if (currentIndex + 1 < activeQueue.size()) {
            return new ArrayList<>(activeQueue.subList(currentIndex + 1, activeQueue.size()));
        }
        return new ArrayList<>();
    }
    
    /**
     * Get the shuffle seed for reproducibility.
     */
    public long getShuffleSeed() {
        return shuffleSeed;
    }
    
    /**
     * Create a state snapshot for persistence.
     * Used by QueuePersistence to save queue state.
     */
    @NonNull
    public QueueSnapshot createSnapshot() {
        return new QueueSnapshot(
            new ArrayList<>(originalQueue),
            new ArrayList<>(playHistory),
            currentIndexOriginal,
            isShuffleEnabled,
            shuffleSeed
        );
    }
    
    /**
     * Restore queue from a saved snapshot.
     * Used by QueuePersistence to restore queue state.
     */
    public void restoreFromSnapshot(@NonNull QueueSnapshot snapshot) {
        originalQueue.clear();
        originalQueue.addAll(snapshot.originalQueue);
        
        playHistory.clear();
        playHistory.addAll(snapshot.playHistory);
        
        currentIndexOriginal = snapshot.currentIndex;
        isShuffleEnabled = snapshot.isShuffleEnabled;
        shuffleSeed = snapshot.shuffleSeed;
        
        // Regenerate shuffle if needed
        if (isShuffleEnabled && !originalQueue.isEmpty()) {
            // Use saved seed to reproduce same shuffle
            Random savedRandom = new Random(shuffleSeed);
            shuffledQueue.clear();
            shuffledQueue.addAll(originalQueue);
            
            // Fisher-Yates with saved seed
            for (int i = shuffledQueue.size() - 1; i > 0; i--) {
                int j = savedRandom.nextInt(i + 1);
                Collections.swap(shuffledQueue, i, j);
            }
            
            // Apply spreading
            spreadArtists();
            
            // Find current song position in shuffled queue
            Song currentSong = getCurrentSong();
            if (currentSong != null) {
                for (int i = 0; i < shuffledQueue.size(); i++) {
                    if (shuffledQueue.get(i).getVideoId().equals(currentSong.getVideoId())) {
                        currentIndexShuffled = i;
                        break;
                    }
                }
            }
            
            rebuildMappings();
        }
    }
    
    /**
     * Snapshot of queue state for persistence.
     */
    public static class QueueSnapshot {
        public final List<Song> originalQueue;
        public final List<Song> playHistory;
        public final int currentIndex;
        public final boolean isShuffleEnabled;
        public final long shuffleSeed;
        
        public QueueSnapshot(List<Song> originalQueue, List<Song> playHistory, 
                     int currentIndex, boolean isShuffleEnabled, long shuffleSeed) {
            this.originalQueue = originalQueue;
            this.playHistory = playHistory;
            this.currentIndex = currentIndex;
            this.isShuffleEnabled = isShuffleEnabled;
            this.shuffleSeed = shuffleSeed;
        }
    }
}
