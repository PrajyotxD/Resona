package music.resona.viewmodel;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

import music.resona.database.RecommendationDatabase;
import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.callbacks.HomePageCallback;
import music.resona.online.bridge.callbacks.SearchCallback;
import music.resona.online.bridge.exceptions.BridgeException;
import music.resona.online.bridge.models.HomePageResult;
import music.resona.online.bridge.models.HomeSectionResult;
import music.resona.online.bridge.models.SearchResult;
import music.resona.online.bridge.models.YTItemResult;
import music.resona.recommendation.RecommendationEngine;
import music.resona.recommendation.RecommendationSection;

/**
 * Enhanced home feed manager that combines YouTube Music API with local recommendations.
 * Creates a personalized home feed using user behavior and listening history.
 */
public class PersonalizedHomeFeed {
    
    private static final String TAG = "PersonalizedHomeFeed";
    
    private final Context context;
    private final RecommendationEngine recommendationEngine;
    private final RecommendationDatabase database;
    
    private final MutableLiveData<List<HomeSectionResult>> personalizedSections;
    private final MutableLiveData<Boolean> isLoading;
    private final MutableLiveData<String> errorMessage;
    
    public PersonalizedHomeFeed(Context context) {
        this.context = context.getApplicationContext();
        this.recommendationEngine = RecommendationEngine.getInstance(context);
        this.database = RecommendationDatabase.getInstance(context);
        
        this.personalizedSections = new MutableLiveData<>(new ArrayList<>());
        this.isLoading = new MutableLiveData<>(false);
        this.errorMessage = new MutableLiveData<>();
    }
    
    public LiveData<List<HomeSectionResult>> getPersonalizedSections() {
        return personalizedSections;
    }
    
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Generate personalized home feed sections.
     * This method combines local recommendations with API-fetched content.
     * Always loads both: personalized sections from history + YouTube home feed.
     */
    public void generatePersonalizedFeed() {
        isLoading.postValue(true);
        
        // Get all recommendation sections from engine
        List<RecommendationSection> recommendations = recommendationEngine.generateAllRecommendations();
        
        Log.d(TAG, "Generated " + recommendations.size() + " recommendation sections from history");
        
        // Convert recommendation sections to home sections
        List<HomeSectionResult> sections = new ArrayList<>();
        
        // Add personalized sections from local history (if any)
        for (RecommendationSection rec : recommendations) {
            switch (rec.getType()) {
                case RECENTLY_PLAYED:
                case ON_REPEAT:
                case FORGOTTEN_FAVORITES:
                case BECAUSE_YOU_LISTENED:
                    // Create local section immediately for instant display
                    sections.add(createSectionFromVideoIds(rec));
                    // Also fetch additional content from YouTube API
                    fetchRelatedContentWithArtistId(rec);
                    break;
                    
                case SIMILAR_ARTISTS:
                    // Fetch similar artists from API
                    fetchSimilarArtists(rec, sections);
                    break;
                    
                case PERSONALIZED_MIX:
                    // Fetch mix from multiple artists
                    fetchPersonalizedMix(rec, sections);
                    break;
                    
                case DISCOVER_WEEKLY:
                    // Fetch discovery content
                    fetchDiscoveryContent(rec, sections);
                    break;
            }
        }
        
        // Set initial personalized sections
        personalizedSections.postValue(sections);
        
        // Always fetch YouTube home feed and append to existing sections
        loadYouTubeHomeFeed();
    }
    
    /**
     * Create a section from existing video IDs in the database.
     */
    private HomeSectionResult createSectionFromVideoIds(RecommendationSection rec) {
        List<YTItemResult> items = new ArrayList<>();
        
        for (String videoId : rec.getRelatedVideoIds()) {
            var history = database.getHistory(videoId);
            if (history != null) {
                // Create artist result
                List<music.resona.online.bridge.models.ArtistResult> artists = new ArrayList<>();
                if (history.getArtistName() != null) {
                    artists.add(new music.resona.online.bridge.models.ArtistResult(
                        history.getArtistId() != null ? history.getArtistId() : "",
                        history.getArtistName()
                    ));
                }
                
                // Create YTItemResult with all required parameters
                YTItemResult item = new YTItemResult(
                    videoId,                                    // id
                    history.getTitle(),                         // title
                    history.getThumbnail(),                     // thumbnail
                    "song",                                     // type
                    artists,                                    // artists
                    null,                                       // album
                    null,                                       // duration
                    false,                                      // explicit
                    null,                                       // shareLink
                    null,                                       // browseId
                    null,                                       // playlistId
                    null,                                       // chartPosition
                    null                                        // chartChange
                );
                
                items.add(item);
            }
        }
        
        // Create HomeSectionResult with title and items
        return new HomeSectionResult(rec.getTitle(), items);
    }
    
    /**
     * Fetch related content using artist browse ID for additional YouTube content.
     */
    private void fetchRelatedContentWithArtistId(RecommendationSection rec) {
        // Try artist ID first
        String seedId = rec.getSeedArtistId();
        
        // If no artist ID, try to extract from section title
        if (seedId == null || seedId.isEmpty()) {
            seedId = extractArtistFromTitle(rec.getTitle());
        }
        
        if (seedId == null || seedId.isEmpty()) {
            Log.d(TAG, "No artist ID available for section: " + rec.getTitle());
            return;
        }
        
        Log.d(TAG, "Fetching YouTube content with seed: " + seedId + " for section: " + rec.getTitle());
        
        // Check if seedId looks like a browse ID (starts with MP, UC, etc.) or is just a search term
        if (seedId.startsWith("MP") || seedId.startsWith("UC") || seedId.startsWith("OLAK")) {
            // Use browse API for valid browse IDs
            InnertubeBridge.getRelatedContentAsync(seedId, new HomePageCallback() {
                @Override
                public void onSuccess(@NonNull HomePageResult result) {
                    handleYouTubeContentSuccess(rec, result, "browse");
                }
                
                @Override
                public void onError(@NonNull BridgeException exception) {
                    Log.d(TAG, "Could not fetch additional YouTube content for \"" + rec.getTitle() + "\": " + exception.getMessage());
                }
            });
        } else {
            // Use search API for artist names/song titles  
            final String finalSeedId = seedId; // Make final for inner class
            Log.d(TAG, "Using search API for: " + finalSeedId);
            InnertubeBridge.searchAsync(finalSeedId + " artist", new SearchCallback() {
                @Override
                public void onSuccess(@NonNull SearchResult result) {
                    // Convert search result to home page format for consistency
                    if (result.getItems() != null && !result.getItems().isEmpty()) {
                        Log.d(TAG, "Found " + result.getItems().size() + " search results for: " + finalSeedId);
                        
                        // Create a fake HomePageResult with search results
                        List<HomeSectionResult> sections = new ArrayList<>();
                        List<YTItemResult> searchItems = result.getItems();
                        
                        // Limit to first 10 results to avoid overwhelming
                        if (searchItems.size() > 10) {
                            searchItems = searchItems.subList(0, 10);
                        }
                        
                        sections.add(new HomeSectionResult("Related to " + finalSeedId, searchItems));
                        
                        HomePageResult fakeResult = new HomePageResult(sections, null, null);
                        handleYouTubeContentSuccess(rec, fakeResult, "search");
                    } else {
                        Log.d(TAG, "No search results found for: " + finalSeedId);
                    }
                }
                
                @Override
                public void onError(@NonNull BridgeException exception) {
                    Log.d(TAG, "Search failed for \"" + finalSeedId + "\": " + exception.getMessage());
                }
            });
        }
    }
    
    /**
     * Handle successful YouTube content fetch (both browse and search results).
     */
    private void handleYouTubeContentSuccess(RecommendationSection rec, HomePageResult result, String source) {
        if (result.getSections() != null && !result.getSections().isEmpty()) {
            Log.d(TAG, "Fetched additional YouTube content for: " + rec.getTitle() + " (via " + source + ")");
            
            // Find the existing section and append new items to it
            List<HomeSectionResult> current = personalizedSections.getValue();
            if (current != null) {
                List<HomeSectionResult> updated = new ArrayList<>(current);
                
                // Find the existing section with matching title
                for (int i = 0; i < updated.size(); i++) {
                    HomeSectionResult existingSection = updated.get(i);
                    if (existingSection.getTitle().equals(rec.getTitle())) {
                        // Append additional songs from YouTube API
                        List<YTItemResult> combinedItems = new ArrayList<>(existingSection.getItems());
                        
                        for (HomeSectionResult apiSection : result.getSections()) {
                            if (apiSection.getTitle().contains("Song") || 
                                apiSection.getTitle().contains("Related") ||
                                apiSection.getTitle().contains("artist") ||
                                source.equals("search")) {
                                
                                // Limit additional items to avoid overwhelming the section
                                List<YTItemResult> additionalItems = apiSection.getItems();
                                if (additionalItems.size() > 8) {
                                    additionalItems = additionalItems.subList(0, 8);
                                }
                                
                                combinedItems.addAll(additionalItems);
                                break;
                            }
                        }
                        
                        // Update the section with combined items
                        updated.set(i, new HomeSectionResult(rec.getTitle(), combinedItems));
                        personalizedSections.postValue(updated);
                        Log.d(TAG, "Enhanced \"" + rec.getTitle() + "\" with " + (combinedItems.size() - existingSection.getItems().size()) + " additional items");
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * Fetch similar artists.
     */
    private void fetchSimilarArtists(RecommendationSection rec, List<HomeSectionResult> sections) {
        if (rec.getSeedArtistId() == null) return;
        
        InnertubeBridge.getRelatedContentAsync(rec.getSeedArtistId(), new HomePageCallback() {
            @Override
            public void onSuccess(@NonNull HomePageResult result) {
                if (result.getSections() != null && !result.getSections().isEmpty()) {
                    // Use the related artists section
                    for (HomeSectionResult section : result.getSections()) {
                        if (section.getTitle().contains("Artist")) {
                            // Create new section with custom title
                            HomeSectionResult customSection = new HomeSectionResult(
                                rec.getTitle(),
                                section.getItems()
                            );
                            
                            List<HomeSectionResult> current = personalizedSections.getValue();
                            if (current != null) {
                                List<HomeSectionResult> updated = new ArrayList<>(current);
                                updated.add(customSection);
                                personalizedSections.postValue(updated);
                            }
                            break;
                        }
                    }
                }
            }
            
            @Override
            public void onError(@NonNull BridgeException exception) {
                Log.e(TAG, "Failed to fetch similar artists: " + exception.getMessage());
            }
        });
    }
    
    /**
     * Fetch personalized mix from multiple artists.
     */
    private void fetchPersonalizedMix(RecommendationSection rec, List<HomeSectionResult> sections) {
        if (rec.getSeedArtistIds() == null || rec.getSeedArtistIds().isEmpty()) return;
        
        // Use first artist to get related content
        String firstArtist = rec.getSeedArtistIds().get(0);
        
        InnertubeBridge.getRelatedContentAsync(firstArtist, new HomePageCallback() {
            @Override
            public void onSuccess(@NonNull HomePageResult result) {
                if (result.getSections() != null && !result.getSections().isEmpty()) {
                    // Combine songs and playlists for a mix
                    List<YTItemResult> mixItems = new ArrayList<>();
                    
                    for (HomeSectionResult section : result.getSections()) {
                        if (section.getTitle().contains("Song") || section.getTitle().contains("Playlist")) {
                            mixItems.addAll(section.getItems());
                        }
                    }
                    
                    if (!mixItems.isEmpty()) {
                        HomeSectionResult customSection = new HomeSectionResult(
                            rec.getTitle(),
                            mixItems
                        );
                        
                        List<HomeSectionResult> current = personalizedSections.getValue();
                        if (current != null) {
                            List<HomeSectionResult> updated = new ArrayList<>(current);
                            updated.add(customSection);
                            personalizedSections.postValue(updated);
                        }
                    }
                }
            }
            
            @Override
            public void onError(@NonNull BridgeException exception) {
                Log.e(TAG, "Failed to fetch personalized mix: " + exception.getMessage());
            }
        });
    }
    
    /**
     * Fetch discovery content.
     */
    private void fetchDiscoveryContent(RecommendationSection rec, List<HomeSectionResult> sections) {
        if (rec.getSeedArtistIds() == null || rec.getSeedArtistIds().isEmpty()) return;
        
        // Use first artist for discovery
        String seedArtist = rec.getSeedArtistIds().get(0);
        
        InnertubeBridge.getRelatedContentAsync(seedArtist, new HomePageCallback() {
            @Override
            public void onSuccess(@NonNull HomePageResult result) {
                if (result.getSections() != null && !result.getSections().isEmpty()) {
                    // Get all related content for discovery
                    List<YTItemResult> discoveryItems = new ArrayList<>();
                    
                    for (HomeSectionResult section : result.getSections()) {
                        discoveryItems.addAll(section.getItems());
                        if (discoveryItems.size() >= 20) break; // Limit to 20 items
                    }
                    
                    if (!discoveryItems.isEmpty()) {
                        HomeSectionResult customSection = new HomeSectionResult(
                            rec.getTitle(),
                            discoveryItems.subList(0, Math.min(20, discoveryItems.size()))
                        );
                        
                        List<HomeSectionResult> current = personalizedSections.getValue();
                        if (current != null) {
                            List<HomeSectionResult> updated = new ArrayList<>(current);
                            updated.add(customSection);
                            personalizedSections.postValue(updated);
                        }
                    }
                }
            }
            
            @Override
            public void onError(@NonNull BridgeException exception) {
                Log.e(TAG, "Failed to fetch discovery content: " + exception.getMessage());
            }
        });
    }
    
    /**
     * Load YouTube Music home feed and append to personalized sections.
     * This is called after loading personalized sections to combine both data sources.
     */
    private void loadYouTubeHomeFeed() {
        InnertubeBridge.getHomeAsync(new HomePageCallback() {
            @Override
            public void onSuccess(@NonNull HomePageResult result) {
                List<HomeSectionResult> current = personalizedSections.getValue();
                if (current != null) {
                    // Combine personalized sections + YouTube home feed
                    List<HomeSectionResult> combined = new ArrayList<>(current);
                    combined.addAll(result.getSections());
                    personalizedSections.postValue(combined);
                } else {
                    // No personalized sections, just use YouTube home feed
                    personalizedSections.postValue(result.getSections());
                }
                isLoading.postValue(false);
                Log.d(TAG, "Added " + result.getSections().size() + " YouTube home sections");
            }
            
            @Override
            public void onError(@NonNull BridgeException exception) {
                errorMessage.postValue("Failed to load YouTube content: " + exception.getMessage());
                isLoading.postValue(false);
                Log.e(TAG, "Failed to fetch YouTube home feed: " + exception.getMessage());
            }
        });
    }
    
    /**
     * Extract artist name from section title for API lookups.
     */
    private String extractArtistFromTitle(String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }
        
        // Extract artist name from titles like "Because you listened to Artist Name"
        if (title.startsWith("Because you listened to ")) {
            String artistName = title.substring("Because you listened to ".length()).trim();
            if (!artistName.isEmpty()) {
                Log.d(TAG, "Extracted artist name: " + artistName);
                return artistName; // Use artist name as search term
            }
        }
        
        // Extract from "Similar to Artist Name"
        if (title.startsWith("Similar to ")) {
            String artistName = title.substring("Similar to ".length()).trim();
            if (!artistName.isEmpty()) {
                Log.d(TAG, "Extracted similar artist: " + artistName);
                return artistName;
            }
        }
        
        return null;
    }
    
    /**
     * Track a song play for building recommendations.
     */
    public void trackSongPlay(String videoId, String title, String artistName, 
                              String artistId, String albumName, String albumId,
                              String thumbnail, long playDurationMs, float completionRate) {
        
        database.recordPlay(videoId, title, artistName, artistId, 
                          albumName, albumId, thumbnail, playDurationMs, completionRate);
        
        Log.d(TAG, "Tracked play: " + title + " (" + completionRate + "% completion)");
    }
    
    /**
     * Track a song skip for understanding user preferences.
     */
    public void trackSongSkip(String videoId) {
        database.recordSkip(videoId);
        Log.d(TAG, "Tracked skip: " + videoId);
    }
}
