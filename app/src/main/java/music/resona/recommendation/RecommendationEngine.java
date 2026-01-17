package music.resona.recommendation;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;

import music.resona.database.ContentRelationship;
import music.resona.database.ListeningHistory;
import music.resona.database.RecommendationDatabase;
import music.resona.online.bridge.models.YTItemResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced recommendation engine that generates personalized content.
 * Combines local user behavior with YouTube Music API data.
 */
public class RecommendationEngine {
    
    private static final String TAG = "RecommendationEngine";
    
    private final RecommendationDatabase database;
    private final Context context;
    
    private static RecommendationEngine instance;
    
    private RecommendationEngine(Context context) {
        this.context = context.getApplicationContext();
        this.database = RecommendationDatabase.getInstance(context);
    }
    
    public static synchronized RecommendationEngine getInstance(Context context) {
        if (instance == null) {
            instance = new RecommendationEngine(context);
        }
        return instance;
    }
    
    /**
     * Generate "Because you listened to X" recommendations.
     */
    public List<RecommendationSection> generateBecauseYouListenedTo(int sectionsLimit) {
        Log.d(TAG, "=== generateBecauseYouListenedTo START ===");
        Log.d(TAG, "Sections limit: " + sectionsLimit);
        
        List<RecommendationSection> sections = new ArrayList<>();
        
        // Get top engaged songs
        List<ListeningHistory> topSongs = database.getTopEngaged(5);
        Log.d(TAG, "Retrieved " + topSongs.size() + " top engaged songs from database");
        
        int songsProcessed = 0;
        for (ListeningHistory history : topSongs) {
            songsProcessed++;
            Log.d(TAG, "Processing song " + songsProcessed + "/" + topSongs.size() + ": " + history.getTitle());
            Log.d(TAG, "  VideoId: " + history.getVideoId() + ", PlayCount: " + history.getPlayCount());
            
            if (sections.size() >= sectionsLimit) {
                Log.d(TAG, "Reached sections limit (" + sectionsLimit + "), stopping");
                break;
            }
            
            // Get related songs
            List<String> relatedSongIds = database.getRelatedSongs(history.getVideoId(), 10);
            Log.d(TAG, "  Found " + relatedSongIds.size() + " related songs");
            
            if (!relatedSongIds.isEmpty()) {
                float confidence = calculateConfidence(history);
                
                RecommendationSection section = new RecommendationSection();
                section.setTitle("Because you listened to " + history.getTitle());
                section.setReason("Based on your listening to " + history.getArtistName());
                section.setSeedVideoId(history.getVideoId());
                section.setRelatedVideoIds(relatedSongIds);
                section.setType(RecommendationSection.SectionType.BECAUSE_YOU_LISTENED);
                section.setConfidence(confidence);
                
                sections.add(section);
                Log.d(TAG, "  ✓ Created section with confidence: " + confidence);
            } else {
                Log.w(TAG, "  No related songs found - skipping");
            }
        }
        
        Log.d(TAG, "Total 'Because You Listened To' sections created: " + sections.size());
        Log.d(TAG, "=== generateBecauseYouListenedTo END ===");
        return sections;
    }
    
    /**
     * Generate "Forgotten Favorites" section.
     */
    public RecommendationSection generateForgottenFavorites() {
        List<ListeningHistory> forgotten = database.getForgottenFavorites(20);
        
        if (forgotten.isEmpty()) {
            return null;
        }
        
        RecommendationSection section = new RecommendationSection();
        section.setTitle("Forgotten Favorites");
        section.setReason("Songs you loved but haven't played recently");
        section.setType(RecommendationSection.SectionType.FORGOTTEN_FAVORITES);
        section.setConfidence(80f);
        
        List<String> videoIds = forgotten.stream()
                .map(ListeningHistory::getVideoId)
                .collect(Collectors.toList());
        section.setRelatedVideoIds(videoIds);
        
        return section;
    }
    
    /**
     * Generate "Your Top Artists" recommendations.
     */
    public List<RecommendationSection> generateSimilarToFavoriteArtists(int sectionsLimit) {
        List<RecommendationSection> sections = new ArrayList<>();
        
        Set<String> favoriteArtists = database.getFavoriteArtists();
        
        int artistsProcessed = 0;
        for (String artistId : favoriteArtists) {
            artistsProcessed++;
            Log.d(TAG, "Processing artist " + artistsProcessed + "/" + favoriteArtists.size() + ": " + artistId);
            
            if (sections.size() >= sectionsLimit) {
                Log.d(TAG, "Reached sections limit (" + sectionsLimit + "), stopping");
                break;
            }
            
            // Find artist name from history
            String artistName = getArtistName(artistId);
            Log.d(TAG, "Artist name for ID " + artistId + ": " + artistName);
            
            if (artistName == null) {
                Log.w(TAG, "Could not find artist name for ID " + artistId + " - skipping");
                continue;
            }
            
            RecommendationSection section = new RecommendationSection();
            section.setTitle("Similar to " + artistName);
            section.setReason("Based on your favorite artist");
            section.setSeedArtistId(artistId);
            section.setType(RecommendationSection.SectionType.SIMILAR_ARTISTS);
            section.setConfidence(70f);
            
            sections.add(section);
            Log.d(TAG, "✓ Created section: Similar to " + artistName + " (confidence: 70)");
        }
        
        Log.d(TAG, "Total sections created: " + sections.size());
        Log.d(TAG, "=== generateSimilarToFavoriteArtists END ===");
        return sections;
    }
    
    /**
     * Generate "Recently Played" section.
     */
    public RecommendationSection generateRecentlyPlayed() {
        List<ListeningHistory> recent = database.getRecentlyPlayed(20);
        
        if (recent.isEmpty()) {
            return null;
        }
        
        RecommendationSection section = new RecommendationSection();
        section.setTitle("Recently Played");
        section.setReason("Your recent listening history");
        section.setType(RecommendationSection.SectionType.RECENTLY_PLAYED);
        section.setConfidence(100f);
        
        List<String> videoIds = recent.stream()
                .map(ListeningHistory::getVideoId)
                .collect(Collectors.toList());
        section.setRelatedVideoIds(videoIds);
        
        return section;
    }
    
    /**
     * Generate "On Repeat" section - most played songs.
     */
    public RecommendationSection generateOnRepeat() {
        List<ListeningHistory> mostPlayed = database.getMostPlayed(20);
        
        if (mostPlayed.isEmpty()) {
            return null;
        }
        
        RecommendationSection section = new RecommendationSection();
        section.setTitle("On Repeat");
        section.setReason("Your most played songs");
        section.setType(RecommendationSection.SectionType.ON_REPEAT);
        section.setConfidence(95f);
        
        List<String> videoIds = mostPlayed.stream()
                .map(ListeningHistory::getVideoId)
                .collect(Collectors.toList());
        section.setRelatedVideoIds(videoIds);
        
        return section;
    }
    
    /**
     * Generate "Mix" based on multiple favorite artists.
     */
    public RecommendationSection generatePersonalizedMix() {
        Set<String> favoriteArtists = database.getFavoriteArtists();
        
        if (favoriteArtists.size() < 2) {
            return null;
        }
        
        RecommendationSection section = new RecommendationSection();
        section.setTitle("Your Mix");
        section.setReason("Based on your favorite artists");
        section.setType(RecommendationSection.SectionType.PERSONALIZED_MIX);
        section.setConfidence(85f);
        
        List<String> artistIds = new ArrayList<>(favoriteArtists);
        Collections.shuffle(artistIds);
        section.setSeedArtistIds(artistIds.subList(0, Math.min(5, artistIds.size())));
        
        return section;
    }
    
    /**
     * Generate "Discover Weekly" style recommendations.
     */
    public RecommendationSection generateDiscoverWeekly() {
        // Get diverse set of artists user likes
        Set<String> favoriteArtists = database.getFavoriteArtists();
        
        if (favoriteArtists.isEmpty()) {
            return null;
        }
        
        RecommendationSection section = new RecommendationSection();
        section.setTitle("Discover Weekly");
        section.setReason("Fresh finds based on your taste");
        section.setType(RecommendationSection.SectionType.DISCOVER_WEEKLY);
        section.setConfidence(75f);
        
        // Use favorite artists as seeds for discovery
        List<String> artistIds = new ArrayList<>(favoriteArtists);
        Collections.shuffle(artistIds);
        section.setSeedArtistIds(artistIds.subList(0, Math.min(3, artistIds.size())));
        
        return section;
    }
    
    /**
     * Generate all personalized sections.
     */
    public List<RecommendationSection> generateAllRecommendations() {
        Log.d(TAG, "=====================================");
        Log.d(TAG, "=== GENERATING ALL RECOMMENDATIONS ===");
        Log.d(TAG, "=====================================");
        
        List<RecommendationSection> allSections = new ArrayList<>();
        
        // Add recently played (always first if available)
        Log.d(TAG, "\n--- Generating Recently Played ---");
        RecommendationSection recentSection = generateRecentlyPlayed();
        if (recentSection != null) {
            allSections.add(recentSection);
            Log.d(TAG, "✓ Added Recently Played section");
        } else {
            Log.d(TAG, "✗ No Recently Played section");
        }
        
        // Add on repeat
        Log.d(TAG, "\n--- Generating On Repeat ---");
        RecommendationSection repeatSection = generateOnRepeat();
        if (repeatSection != null) {
            allSections.add(repeatSection);
            Log.d(TAG, "✓ Added On Repeat section");
        } else {
            Log.d(TAG, "✗ No On Repeat section");
        }
        
        // Add "because you listened to" sections
        Log.d(TAG, "\n--- Generating Because You Listened To ---");
        List<RecommendationSection> becauseSections = generateBecauseYouListenedTo(3);
        allSections.addAll(becauseSections);
        Log.d(TAG, "Added " + becauseSections.size() + " Because You Listened To sections");
        
        // Add personalized mix
        Log.d(TAG, "\n--- Generating Personalized Mix ---");
        RecommendationSection mixSection = generatePersonalizedMix();
        if (mixSection != null) {
            allSections.add(mixSection);
            Log.d(TAG, "✓ Added Personalized Mix section");
        } else {
            Log.d(TAG, "✗ No Personalized Mix section");
        }
        
        // Add forgotten favorites
        Log.d(TAG, "\n--- Generating Forgotten Favorites ---");
        RecommendationSection forgottenSection = generateForgottenFavorites();
        if (forgottenSection != null) {
            allSections.add(forgottenSection);
            Log.d(TAG, "✓ Added Forgotten Favorites section");
        } else {
            Log.d(TAG, "✗ No Forgotten Favorites section");
        }
        
        // Add similar to favorite artists
        Log.d(TAG, "\n--- Generating Similar to Favorite Artists ---");
        List<RecommendationSection> similarSections = generateSimilarToFavoriteArtists(2);
        allSections.addAll(similarSections);
        Log.d(TAG, "Added " + similarSections.size() + " Similar Artist sections");
        
        // Add discover weekly
        Log.d(TAG, "\n--- Generating Discover Weekly ---");
        RecommendationSection discoverSection = generateDiscoverWeekly();
        if (discoverSection != null) {
            allSections.add(discoverSection);
            Log.d(TAG, "✓ Added Discover Weekly section");
        } else {
            Log.d(TAG, "✗ No Discover Weekly section");
        }
        
        // Sort by confidence score
        Log.d(TAG, "\n--- Sorting by confidence ---");
        Log.d(TAG, "Before sort: " + allSections.size() + " sections");
        allSections.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        Log.d(TAG, "After sort: " + allSections.size() + " sections");
        
        Log.d(TAG, "\n=====================================");
        Log.d(TAG, "=== TOTAL SECTIONS: " + allSections.size() + " ===");
        for (int i = 0; i < allSections.size(); i++) {
            RecommendationSection section = allSections.get(i);
            Log.d(TAG, (i+1) + ". " + section.getTitle() + " (confidence: " + section.getConfidence() + ", type: " + section.getType() + ")");
        }
        Log.d(TAG, "=====================================");
        
        return allSections;
    }
    
    // ==================== Helper Methods ====================
    
    private float calculateConfidence(ListeningHistory history) {
        float baseConfidence = 50f;
        
        // Boost for play count
        baseConfidence += Math.min(history.getPlayCount() * 3f, 30f);
        
        // Boost for completion rate
        baseConfidence += (history.getCompletionRate() / 100f) * 15f;
        
        // Boost for recency
        long hoursSincePlay = (System.currentTimeMillis() - history.getLastPlayedAt()) / (1000 * 60 * 60);
        if (hoursSincePlay < 24) {
            baseConfidence += 10f;
        } else if (hoursSincePlay < 24 * 7) {
            baseConfidence += 5f;
        }
        
        return Math.min(100f, baseConfidence);
    }
    
    private String getArtistName(String artistId) {
        Log.d(TAG, "  getArtistName() called for artistId: " + artistId);
        
        // Find artist name from listening history
        List<ListeningHistory> allHistory = database.getAllHistory();
        Log.d(TAG, "  Searching through " + allHistory.size() + " history entries");
        
        for (ListeningHistory history : allHistory) {
            if (artistId.equals(history.getArtistId())) {
                Log.d(TAG, "  ✓ Found match: " + history.getArtistName());
                return history.getArtistName();
            }
        }
        
        Log.w(TAG, "  ✗ No matching artist found in history");
        return null;
    }
}
