package music.resona.recommendation;

import android.content.Context;
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
        List<RecommendationSection> sections = new ArrayList<>();
        
        // Get top engaged songs
        List<ListeningHistory> topSongs = database.getTopEngaged(5);
        
        for (ListeningHistory history : topSongs) {
            if (sections.size() >= sectionsLimit) break;
            
            // Get related songs
            List<String> relatedSongIds = database.getRelatedSongs(history.getVideoId(), 10);
            
            if (!relatedSongIds.isEmpty()) {
                RecommendationSection section = new RecommendationSection();
                section.setTitle("Because you listened to " + history.getTitle());
                section.setReason("Based on your listening to " + history.getArtistName());
                section.setSeedVideoId(history.getVideoId());
                section.setRelatedVideoIds(relatedSongIds);
                section.setType(RecommendationSection.SectionType.BECAUSE_YOU_LISTENED);
                section.setConfidence(calculateConfidence(history));
                
                sections.add(section);
            }
        }
        
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
        
        for (String artistId : favoriteArtists) {
            if (sections.size() >= sectionsLimit) break;
            
            // Find artist name from history
            String artistName = getArtistName(artistId);
            if (artistName == null) continue;
            
            RecommendationSection section = new RecommendationSection();
            section.setTitle("Similar to " + artistName);
            section.setReason("Based on your favorite artist");
            section.setSeedArtistId(artistId);
            section.setType(RecommendationSection.SectionType.SIMILAR_ARTISTS);
            section.setConfidence(70f);
            
            sections.add(section);
        }
        
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
        List<RecommendationSection> allSections = new ArrayList<>();
        
        // Add recently played (always first if available)
        RecommendationSection recentSection = generateRecentlyPlayed();
        if (recentSection != null) {
            allSections.add(recentSection);
        }
        
        // Add on repeat
        RecommendationSection repeatSection = generateOnRepeat();
        if (repeatSection != null) {
            allSections.add(repeatSection);
        }
        
        // Add "because you listened to" sections
        allSections.addAll(generateBecauseYouListenedTo(3));
        
        // Add personalized mix
        RecommendationSection mixSection = generatePersonalizedMix();
        if (mixSection != null) {
            allSections.add(mixSection);
        }
        
        // Add forgotten favorites
        RecommendationSection forgottenSection = generateForgottenFavorites();
        if (forgottenSection != null) {
            allSections.add(forgottenSection);
        }
        
        // Add similar to favorite artists
        allSections.addAll(generateSimilarToFavoriteArtists(2));
        
        // Add discover weekly
        RecommendationSection discoverSection = generateDiscoverWeekly();
        if (discoverSection != null) {
            allSections.add(discoverSection);
        }
        
        // Sort by confidence score
        allSections.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        
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
        // Find artist name from listening history
        for (ListeningHistory history : database.getAllHistory()) {
            if (artistId.equals(history.getArtistId())) {
                return history.getArtistName();
            }
        }
        return null;
    }
}
