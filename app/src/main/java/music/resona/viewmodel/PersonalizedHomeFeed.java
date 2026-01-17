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
import music.resona.online.bridge.exceptions.BridgeException;
import music.resona.online.bridge.models.HomePageResult;
import music.resona.online.bridge.models.HomeSectionResult;
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
        isLoading.setValue(true);
        
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
                    // These have video IDs already, fetch their details
                    sections.add(createSectionFromVideoIds(rec));
                    break;
                    
                case BECAUSE_YOU_LISTENED:
                    // Fetch related content from API
                    fetchRelatedContent(rec, sections);
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
        personalizedSections.setValue(sections);
        
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
     * Fetch related content for "Because you listened to X" sections.
     */
    private void fetchRelatedContent(RecommendationSection rec, List<HomeSectionResult> sections) {
        if (rec.getSeedVideoId() == null) return;
        
        InnertubeBridge.getRelatedContentAsync(rec.getSeedVideoId(), new HomePageCallback() {
            @Override
            public void onSuccess(@NonNull HomePageResult result) {
                if (result.getSections() != null && !result.getSections().isEmpty()) {
                    // Use the related songs section
                    for (HomeSectionResult section : result.getSections()) {
                        if (section.getTitle().contains("Song")) {
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
                Log.e(TAG, "Failed to fetch related content: " + exception.getMessage());
            }
        });
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
