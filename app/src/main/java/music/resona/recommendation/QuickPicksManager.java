package music.resona.recommendation;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import music.resona.database.QuickPicksDatabase;
import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.callbacks.HomePageCallback;
import music.resona.online.bridge.exceptions.BridgeException;
import music.resona.online.bridge.models.HomePageResult;
import music.resona.online.bridge.models.HomeSectionResult;
import music.resona.online.bridge.models.YTItemResult;

/**
 * Manager for Quick Picks feature.
 * Combines local user behavior data with API content to generate personalized recommendations.
 */
public class QuickPicksManager {
    
    private static final String TAG = "QuickPicksManager";
    private static final int QUICK_PICKS_LIMIT = 12; // 6 visible rows (2 per row)
    
    private final Context context;
    private final QuickPicksDatabase database;
    
    public QuickPicksManager(Context context) {
        this.context = context.getApplicationContext();
        this.database = QuickPicksDatabase.getInstance(context);
    }
    
    /**
     * Generate Quick Picks recommendations.
     * Combines recent plays, popular songs, and related content.
     * Falls back to API content for new users.
     */
    public void generateQuickPicks(QuickPicksCallback callback) {
        new GenerateQuickPicksTask(callback).execute();
    }
    
    /**
     * Record a song play event.
     */
    public void recordSongPlay(String videoId, String title, String artist, 
                              String artistId, String thumbnail, int duration, long playDuration) {
        new RecordPlayTask(videoId, title, artist, artistId, thumbnail, duration, playDuration).execute();
    }
    
    /**
     * Record relationship between two songs.
     */
    public void recordSongRelationship(String currentVideoId, String nextVideoId) {
        new RecordRelationshipTask(currentVideoId, nextVideoId).execute();
    }
    
    /**
     * AsyncTask to generate Quick Picks in background.
     */
    private class GenerateQuickPicksTask extends AsyncTask<Void, Void, List<YTItemResult>> {
        
        private final QuickPicksCallback callback;
        private boolean usedLocalData = false;
        
        GenerateQuickPicksTask(QuickPicksCallback callback) {
            this.callback = callback;
        }
        
        @Override
        protected List<YTItemResult> doInBackground(Void... voids) {
            List<YTItemResult> quickPicks = new ArrayList<>();
            
            if (database.hasSufficientData()) {
                Log.d(TAG, "User has sufficient data, generating personalized Quick Picks");
                usedLocalData = true;
                quickPicks = generatePersonalizedPicks();
            }
            
            return quickPicks;
        }
        
        @Override
        protected void onPostExecute(List<YTItemResult> result) {
            if (usedLocalData && !result.isEmpty()) {
                // Have personalized data
                callback.onQuickPicksGenerated(result, true);
            } else {
                // Fall back to API content
                Log.d(TAG, "Falling back to API content for Quick Picks");
                loadAPIFallback(callback);
            }
        }
        
        /**
         * Generate personalized picks from local data.
         */
        private List<YTItemResult> generatePersonalizedPicks() {
            Set<String> addedVideoIds = new HashSet<>();
            List<YTItemResult> picks = new ArrayList<>();
            
            // 1. Add most frequently related songs (highest priority)
            List<QuickPicksDatabase.QuickPickSong> relatedSongs = database.getRelatedSongs(8);
            for (QuickPicksDatabase.QuickPickSong song : relatedSongs) {
                if (addedVideoIds.add(song.videoId)) {
                    picks.add(convertToYTItem(song));
                }
            }
            
            // 2. Add most played songs (if not already added)
            List<QuickPicksDatabase.QuickPickSong> popularSongs = database.getMostPlayed(6);
            for (QuickPicksDatabase.QuickPickSong song : popularSongs) {
                if (addedVideoIds.add(song.videoId)) {
                    picks.add(convertToYTItem(song));
                }
                if (picks.size() >= QUICK_PICKS_LIMIT) break;
            }
            
            // 3. Add recently played songs (if not already added)
            List<QuickPicksDatabase.QuickPickSong> recentSongs = database.getRecentlyPlayed(6);
            for (QuickPicksDatabase.QuickPickSong song : recentSongs) {
                if (addedVideoIds.add(song.videoId)) {
                    picks.add(convertToYTItem(song));
                }
                if (picks.size() >= QUICK_PICKS_LIMIT) break;
            }
            
            Log.d(TAG, "Generated " + picks.size() + " personalized Quick Picks from local data");
            return picks;
        }
        
        /**
         * Convert QuickPickSong to YTItemResult.
         */
        private YTItemResult convertToYTItem(QuickPicksDatabase.QuickPickSong song) {
            List<music.resona.online.bridge.models.ArtistResult> artists = new ArrayList<>();
            if (song.artist != null) {
                artists.add(new music.resona.online.bridge.models.ArtistResult(
                    song.artistId != null ? song.artistId : "",
                    song.artist
                ));
            }
            
            return new YTItemResult(
                song.videoId,           // id
                song.title,             // title
                song.thumbnail,         // thumbnail
                "song",                 // type
                artists,                // artists
                null,                   // album
                song.duration,          // duration
                false,                  // explicit
                null,                   // shareLink
                null,                   // browseId
                null,                   // playlistId
                null,                   // chartPosition
                null                    // chartChange
            );
        }
    }
    
    /**
     * Load API fallback content for new users.
     */
    private void loadAPIFallback(QuickPicksCallback callback) {
        InnertubeBridge.getHomeAsync(new HomePageCallback() {
            @Override
            public void onSuccess(@NonNull HomePageResult result) {
                List<YTItemResult> fallbackPicks = new ArrayList<>();
                
                // Extract songs from home page sections
                if (result.getSections() != null) {
                    for (HomeSectionResult section : result.getSections()) {
                        if (section.getItems() != null) {
                            for (YTItemResult item : section.getItems()) {
                                if ("song".equals(item.getType())) {
                                    fallbackPicks.add(item);
                                    if (fallbackPicks.size() >= QUICK_PICKS_LIMIT) {
                                        break;
                                    }
                                }
                            }
                        }
                        if (fallbackPicks.size() >= QUICK_PICKS_LIMIT) {
                            break;
                        }
                    }
                }
                
                if (!fallbackPicks.isEmpty()) {
                    Log.d(TAG, "Loaded " + fallbackPicks.size() + " songs from API fallback");
                    callback.onQuickPicksGenerated(fallbackPicks, false);
                } else {
                    Log.w(TAG, "API fallback returned no songs, using hardcoded defaults");
                    loadHardcodedDefaults(callback);
                }
            }
            
            @Override
            public void onError(@NonNull BridgeException error) {
                Log.e(TAG, "API fallback failed: " + error.getMessage() + ", using hardcoded defaults");
                loadHardcodedDefaults(callback);
            }
        });
    }
    
    /**
     * Emergency fallback: hardcoded popular songs for new users.
     * Used when both local data and API are unavailable.
     */
    private void loadHardcodedDefaults(QuickPicksCallback callback) {
        List<YTItemResult> defaultPicks = createHardcodedSongs();
        Log.d(TAG, "Using " + defaultPicks.size() + " hardcoded default songs");
        callback.onQuickPicksGenerated(defaultPicks, false);
    }
    
    /**
     * Create hardcoded popular songs for emergency fallback.
     * These are universally popular tracks that work for new users.
     */
    private List<YTItemResult> createHardcodedSongs() {
        List<YTItemResult> hardcodedSongs = new ArrayList<>();
        String[][] songData = {
            // Format: {videoId, title, artist, thumbnail, duration}
            {"60ItHLz5WEA", "Faded", "Alan Walker", "https://i.ytimg.com/vi/60ItHLz5WEA/maxresdefault.jpg", "212"},
            {"RgKAFK5djSk", "Shape of You", "Ed Sheeran", "https://i.ytimg.com/vi/RgKAFK5djSk/maxresdefault.jpg", "233"},
            {"kJQP7kiw5Fk", "Despacito", "Luis Fonsi ft. Daddy Yankee", "https://i.ytimg.com/vi/kJQP7kiw5Fk/maxresdefault.jpg", "282"},
            {"pRpeEdMmmQ0", "Shakira: Waka Waka", "Shakira", "https://i.ytimg.com/vi/pRpeEdMmmQ0/maxresdefault.jpg", "217"},
            {"hLQl3WQQoQ0", "Someone Like You", "Adele", "https://i.ytimg.com/vi/hLQl3WQQoQ0/maxresdefault.jpg", "285"},
            {"YQHsXMglC9A", "Hello", "Adele", "https://i.ytimg.com/vi/YQHsXMglC9A/maxresdefault.jpg", "367"},
            {"JGwWNGJdvx8", "See You Again", "Wiz Khalifa ft. Charlie Puth", "https://i.ytimg.com/vi/JGwWNGJdvx8/maxresdefault.jpg", "229"},
            {"OPf0YbXqDm0", "Uptown Funk", "Mark Ronson ft. Bruno Mars", "https://i.ytimg.com/vi/OPf0YbXqDm0/maxresdefault.jpg", "270"},
            {"CevxZvSJLk8", "Roar", "Katy Perry", "https://i.ytimg.com/vi/CevxZvSJLk8/maxresdefault.jpg", "223"},
            {"iLBBRuVDOo4", "Cheap Thrills", "Sia", "https://i.ytimg.com/vi/iLBBRuVDOo4/maxresdefault.jpg", "210"},
            {"SlPhMPnQ58k", "Stressed Out", "Twenty One Pilots", "https://i.ytimg.com/vi/SlPhMPnQ58k/maxresdefault.jpg", "202"},
            {"Zi_XLOBDo_Y", "Billie Jean", "Michael Jackson", "https://i.ytimg.com/vi/Zi_XLOBDo_Y/maxresdefault.jpg", "294"}
        };
        
        for (String[] song : songData) {
            // Create artist list
            List<music.resona.online.bridge.models.ArtistResult> artists = new ArrayList<>();
            if (song[2] != null && !song[2].isEmpty()) {
                artists.add(new music.resona.online.bridge.models.ArtistResult(
                    "", // artistId - can be empty
                    song[2] // artist name
                ));
            }

            YTItemResult item = new YTItemResult(
                song[0],           // id
                song[1],           // title
                song[3],           // thumbnail
                "song",           // type
                artists,           // artists
                null,              // album
                Integer.parseInt(song[4]), // duration
                false,             // explicit
                null,              // shareLink
                null,              // browseId
                null,              // playlistId
                null,              // chartPosition
                null               // chartChange
            );
            
            hardcodedSongs.add(item);
            
            if (hardcodedSongs.size() >= QUICK_PICKS_LIMIT) {
                break;
            }
        }
        
        return hardcodedSongs;
    }
    
    /**
     * AsyncTask to record play event in background.
     */
    private class RecordPlayTask extends AsyncTask<Void, Void, Void> {
        private final String videoId;
        private final String title;
        private final String artist;
        private final String artistId;
        private final String thumbnail;
        private final int duration;
        private final long playDuration;
        
        RecordPlayTask(String videoId, String title, String artist, String artistId, 
                      String thumbnail, int duration, long playDuration) {
            this.videoId = videoId;
            this.title = title;
            this.artist = artist;
            this.artistId = artistId;
            this.thumbnail = thumbnail;
            this.duration = duration;
            this.playDuration = playDuration;
        }
        
        @Override
        protected Void doInBackground(Void... voids) {
            // Insert or update song
            database.insertOrUpdateSong(videoId, title, artist, artistId, thumbnail, duration);
            
            // Record play event
            database.recordPlayEvent(videoId, playDuration);
            
            return null;
        }
    }
    
    /**
     * AsyncTask to record song relationship in background.
     */
    private class RecordRelationshipTask extends AsyncTask<Void, Void, Void> {
        private final String currentVideoId;
        private final String nextVideoId;
        
        RecordRelationshipTask(String currentVideoId, String nextVideoId) {
            this.currentVideoId = currentVideoId;
            this.nextVideoId = nextVideoId;
        }
        
        @Override
        protected Void doInBackground(Void... voids) {
            database.recordSongRelationship(currentVideoId, nextVideoId);
            return null;
        }
    }
    
    /**
     * Callback interface for Quick Picks generation.
     */
    public interface QuickPicksCallback {
        /**
         * Called when Quick Picks are successfully generated.
         * @param picks List of recommended songs
         * @param personalized Whether picks are personalized or API fallback
         */
        void onQuickPicksGenerated(List<YTItemResult> picks, boolean personalized);
        
        /**
         * Called when an error occurs.
         * @param error Error message
         */
        void onError(String error);
    }
}
