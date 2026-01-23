package music.resona.playback;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import music.resona.database.QuickPicksDatabase;
import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.callbacks.ExplorePageCallback;
import music.resona.online.bridge.callbacks.HomePageCallback;
import music.resona.online.bridge.exceptions.BridgeException;
import music.resona.online.bridge.models.ExplorePageResult;
import music.resona.online.bridge.models.HomePageResult;
import music.resona.online.bridge.models.HomeSectionResult;
import music.resona.online.bridge.models.YTItemResult;

/**
 * Repository for Quick Picks data management.
 * Handles local database operations and remote API fallback.
 * Implements cold start prevention with preloading and caching.
 */
public class QuickPicksRepository {
    
    private static final String TAG = "QuickPicksRepository";
    private static final int QUICK_PICKS_LIMIT = 100;
    private static final int RECENT_EVENTS_LIMIT = 5;
    private static final int WEEKLY_PLAYED_LIMIT = 20;
    private static final int OVERALL_PLAYED_LIMIT = 30;
    private static final int RELATED_SONGS_LIMIT = 25;
    
    private final Context context;
    private final QuickPicksDatabase database;
    private final AtomicBoolean isPreloading = new AtomicBoolean(false);
    
    // Memory cache for cold start prevention
    private volatile List<YTItemResult> cachedQuickPicks;
    private volatile boolean isCacheValid = false;
    private volatile long cacheTimestamp = 0;
    private static final long CACHE_VALID_DURATION = 30 * 60 * 1000; // 30 minutes
    
    public QuickPicksRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.database = QuickPicksDatabase.getInstance(context);
    }
    
    /**
     * Generate Quick Picks with advanced local signals and fallback logic.
     */
    public void generateQuickPicks(@NonNull QuickPicksCallback callback) {
        // Return cached results immediately if available
        if (isCacheValid && cachedQuickPicks != null && !cachedQuickPicks.isEmpty()) {
            long cacheAge = System.currentTimeMillis() - cacheTimestamp;
            if (cacheAge < CACHE_VALID_DURATION) {
                Log.d(TAG, "Returning cached Quick Picks (age: " + (cacheAge / 1000) + "s)");
                callback.onSuccess(cachedQuickPicks, true);
                return;
            }
        }
        
        new GenerateQuickPicksTask(callback).execute();
    }
    
    /**
     * Preload Quick Picks during app startup for instant access.
     */
    public void preloadQuickPicks() {
        if (isPreloading.compareAndSet(false, true)) {
            Log.d(TAG, "Preloading Quick Picks for cold start prevention");
            generateQuickPicks(new QuickPicksCallback() {
                @Override
                public void onSuccess(List<YTItemResult> picks, boolean personalized) {
                    Log.d(TAG, "Preloaded " + picks.size() + " Quick Picks successfully");
                    isPreloading.set(false);
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Failed to preload Quick Picks: " + error);
                    isPreloading.set(false);
                }
            });
        }
    }
    
    /**
     * Clear cached Quick Picks (call when user preferences change).
     */
    public void invalidateCache() {
        isCacheValid = false;
        cachedQuickPicks = null;
        cacheTimestamp = 0;
        Log.d(TAG, "Quick Picks cache invalidated");
    }
    
    /**
     * AsyncTask to generate Quick Picks with multiple local signals.
     */
    private class GenerateQuickPicksTask extends AsyncTask<Void, Void, QuickPicksResult> {
        
        private final QuickPicksCallback callback;
        
        GenerateQuickPicksTask(@NonNull QuickPicksCallback callback) {
            this.callback = callback;
        }
        
        @Override
        protected QuickPicksResult doInBackground(Void... voids) {
            Log.d(TAG, "===== STARTING QUICK PICKS GENERATION =====");
            
            List<YTItemResult> quickPicks = new ArrayList<>();
            boolean isPersonalized = false;
            
            boolean hasSufficientData = database.hasSufficientData();
            Log.d(TAG, "Database has sufficient data: " + hasSufficientData);
            
            if (hasSufficientData) {
                Log.d(TAG, "→ Generating personalized Quick Picks from local signals");
                quickPicks = generatePersonalizedPicks();
                isPersonalized = !quickPicks.isEmpty();
                
                Log.d(TAG, "→ Personalized picks generated: " + quickPicks.size() + " items");
                
                if (quickPicks.size() >= QUICK_PICKS_LIMIT) {
                    Log.d(TAG, "✓ Sufficient personalized picks (" + quickPicks.size() + "), skipping trending fetch");
                    return new QuickPicksResult(quickPicks, isPersonalized);
                } else {
                    Log.d(TAG, "→ Personalized picks insufficient (" + quickPicks.size() + "/" + QUICK_PICKS_LIMIT + "), will fetch trending");
                }
            } else {
                Log.d(TAG, "→ No local data available, will fetch trending content only");
            }
            
            Log.d(TAG, "→ Fetching trending content to supplement");
            List<YTItemResult> trendingPicks = fetchTrendingContent();
            
            // Merge personalized and trending (remove duplicates)
            Set<String> addedIds = new HashSet<>();
            List<YTItemResult> finalPicks = new ArrayList<>();
            
            // Add personalized picks first
            int personalizedCount = 0;
            for (YTItemResult pick : quickPicks) {
                if (addedIds.add(pick.getId()) && finalPicks.size() < QUICK_PICKS_LIMIT) {
                    finalPicks.add(pick);
                    personalizedCount++;
                }
            }
            Log.d(TAG, "→ Added " + personalizedCount + " personalized picks to final list");
            
            // Add trending picks to fill remaining slots
            int trendingCount = 0;
            for (YTItemResult pick : trendingPicks) {
                if (addedIds.add(pick.getId()) && finalPicks.size() < QUICK_PICKS_LIMIT) {
                    finalPicks.add(pick);
                    trendingCount++;
                }
            }
            Log.d(TAG, "→ Added " + trendingCount + " trending picks to final list");
            Log.d(TAG, "===== FINAL QUICK PICKS COUNT: " + finalPicks.size() + " =====");
            
            return new QuickPicksResult(finalPicks, isPersonalized);
        }
        
        @Override
        protected void onPostExecute(QuickPicksResult result) {
            if (!result.picks.isEmpty()) {
                // Cache the results
                cachedQuickPicks = new ArrayList<>(result.picks);
                isCacheValid = true;
                cacheTimestamp = System.currentTimeMillis();
                
                Log.d(TAG, "✓✓✓ Quick Picks generated successfully ✓✓✓");
                Log.d(TAG, "  → Total picks: " + result.picks.size());
                Log.d(TAG, "  → Personalized: " + result.isPersonalized);
                Log.d(TAG, "  → Cached for future requests");
                
                // Log first 3 picks as sample
                if (result.picks.size() > 0) {
                    Log.d(TAG, "  → Sample picks:");
                    for (int i = 0; i < Math.min(3, result.picks.size()); i++) {
                        YTItemResult pick = result.picks.get(i);
                        Log.d(TAG, "    " + (i+1) + ". " + pick.getTitle() + " - " + 
                              (pick.getArtists() != null && !pick.getArtists().isEmpty() ? 
                               pick.getArtists().get(0).getName() : "Unknown"));
                    }
                }
                
                callback.onSuccess(result.picks, result.isPersonalized);
            } else {
                Log.e(TAG, "✗✗✗ NO QUICK PICKS GENERATED ✗✗✗");
                Log.e(TAG, "  → This indicates all data sources failed");
                Log.e(TAG, "  → Check network connectivity and API availability");
                callback.onError("No content available");
            }
        }
        
        /**
         * Generate personalized picks from multiple local signals.
         */
        private List<YTItemResult> generatePersonalizedPicks() {
            Log.d(TAG, "--- Generating personalized picks from local database ---");
            
            Set<String> addedVideoIds = new HashSet<>();
            List<YTItemResult> picks = new ArrayList<>();
            
            // 1. Last 5 played songs (recent events) - Highest priority
            Log.d(TAG, "Fetching recent events (limit: " + RECENT_EVENTS_LIMIT + ")");
            List<QuickPicksDatabase.QuickPickSong> recentEvents = database.getRecentlyPlayed(RECENT_EVENTS_LIMIT);
            Log.d(TAG, "  Database returned " + recentEvents.size() + " recent events");
            
            int addedRecent = 0;
            for (QuickPicksDatabase.QuickPickSong song : recentEvents) {
                if (addedVideoIds.add(song.videoId) && picks.size() < QUICK_PICKS_LIMIT) {
                    picks.add(convertToYTItem(song));
                    addedRecent++;
                }
            }
            Log.d(TAG, "  ✓ Added " + addedRecent + " recent events to picks");
            
            // 2. Weekly most played songs (last 7 days) 
            // TODO: Implement getWeeklyMostPlayed in database
            // Log.d(TAG, "Fetching weekly played songs (limit: " + WEEKLY_PLAYED_LIMIT + ")");
            // List<QuickPicksDatabase.QuickPickSong> weeklyPlayed = database.getWeeklyMostPlayed(WEEKLY_PLAYED_LIMIT);
            // Log.d(TAG, "  Database returned " + weeklyPlayed.size() + " weekly played songs");
            // int addedWeekly = 0;
            // for (QuickPicksDatabase.QuickPickSong song : weeklyPlayed) {
            //     if (addedVideoIds.add(song.videoId) && picks.size() < QUICK_PICKS_LIMIT) {
            //         picks.add(convertToYTItem(song));
            //         addedWeekly++;
            //     }
            // }
            // Log.d(TAG, "  ✓ Added " + addedWeekly + " weekly played songs");
            
            // 3. Overall most played songs (by total play time)
            // TODO: Implement getMostPlayedByTime in database
            // Log.d(TAG, "Fetching overall most played songs (limit: " + OVERALL_PLAYED_LIMIT + ")");
            // List<QuickPicksDatabase.QuickPickSong> overallPlayed = database.getMostPlayedByTime(OVERALL_PLAYED_LIMIT);
            // Log.d(TAG, "  Database returned " + overallPlayed.size() + " overall played songs");
            // int addedOverall = 0;
            // for (QuickPicksDatabase.QuickPickSong song : overallPlayed) {
            //     if (addedVideoIds.add(song.videoId) && picks.size() < QUICK_PICKS_LIMIT) {
            //         picks.add(convertToYTItem(song));
            //         addedOverall++;
            //     }
            // }
            // Log.d(TAG, "  ✓ Added " + addedOverall + " overall played songs, total: " + picks.size());
            
            // 4. Related songs ranked by reference count
            Log.d(TAG, "Fetching related songs (limit: " + RELATED_SONGS_LIMIT + ")");
            List<QuickPicksDatabase.QuickPickSong> relatedSongs = database.getRelatedSongs(RELATED_SONGS_LIMIT);
            Log.d(TAG, "  Database returned " + relatedSongs.size() + " related songs");
            
            int addedRelated = 0;
            for (QuickPicksDatabase.QuickPickSong song : relatedSongs) {
                if (addedVideoIds.add(song.videoId) && picks.size() < QUICK_PICKS_LIMIT) {
                    picks.add(convertToYTItem(song));
                    addedRelated++;
                }
            }
            Log.d(TAG, "  ✓ Added " + addedRelated + " related songs");
            
            Log.d(TAG, "--- Personalized picks generation complete: " + picks.size() + " total ---");
            return picks;
        }
        
        /**
         * Fetch trending content from YouTube as fallback.
         * Fetches from multiple sources in PARALLEL for faster loading.
         */
        private List<YTItemResult> fetchTrendingContent() {
            Log.d(TAG, "===== Fetching Trending Content (PARALLEL) =====");
            
            final List<YTItemResult> homePicks = new ArrayList<>();
            final List<YTItemResult> explorePicks = new ArrayList<>();
            final List<YTItemResult> historyPicks = new ArrayList<>();
            
            // Create threads for parallel fetching
            Thread homeThread = new Thread(() -> {
                try {
                    Log.d(TAG, "[Thread-Home] Fetching home content...");
                    long start = System.currentTimeMillis();
                    List<YTItemResult> picks = fetchHomeContent();
                    synchronized (homePicks) {
                        homePicks.addAll(picks);
                    }
                    Log.d(TAG, "[Thread-Home] ✓ Fetched " + picks.size() + " items in " + (System.currentTimeMillis() - start) + "ms");
                } catch (Exception e) {
                    Log.w(TAG, "[Thread-Home] ✗ Failed: " + e.getMessage());
                }
            });
            
            Thread exploreThread = new Thread(() -> {
                try {
                    Log.d(TAG, "[Thread-Explore] Fetching explore content...");
                    long start = System.currentTimeMillis();
                    List<YTItemResult> picks = fetchExploreContent();
                    synchronized (explorePicks) {
                        explorePicks.addAll(picks);
                    }
                    Log.d(TAG, "[Thread-Explore] ✓ Fetched " + picks.size() + " items in " + (System.currentTimeMillis() - start) + "ms");
                } catch (Exception e) {
                    Log.w(TAG, "[Thread-Explore] ✗ Failed: " + e.getMessage());
                }
            });
            
            Thread historyThread = new Thread(() -> {
                try {
                    Log.d(TAG, "[Thread-History] Fetching music history...");
                    long start = System.currentTimeMillis();
                    List<YTItemResult> picks = fetchMusicHistory();
                    synchronized (historyPicks) {
                        historyPicks.addAll(picks);
                    }
                    Log.d(TAG, "[Thread-History] ✓ Fetched " + picks.size() + " items in " + (System.currentTimeMillis() - start) + "ms");
                } catch (Exception e) {
                    Log.w(TAG, "[Thread-History] ✗ Failed: " + e.getMessage());
                }
            });
            
            // Start all threads
            long overallStart = System.currentTimeMillis();
            homeThread.start();
            exploreThread.start();
            historyThread.start();
            
            // Wait for all threads to complete
            try {
                homeThread.join();
                exploreThread.join();
                historyThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Thread interrupted while waiting for API calls", e);
                Thread.currentThread().interrupt();
            }
            
            long totalTime = System.currentTimeMillis() - overallStart;
            Log.d(TAG, "All API calls completed in " + totalTime + "ms (parallel execution)");
            
            // Combine results: prioritize Home → Music History → Explore
            List<YTItemResult> trendingPicks = new ArrayList<>();
            Set<String> addedIds = new HashSet<>();
            
            // Add from home first (most relevant)
            for (YTItemResult item : homePicks) {
                if (addedIds.add(item.getId()) && trendingPicks.size() < QUICK_PICKS_LIMIT) {
                    trendingPicks.add(item);
                }
            }
            
            // Add from music history (personalized)
            for (YTItemResult item : historyPicks) {
                if (addedIds.add(item.getId()) && trendingPicks.size() < QUICK_PICKS_LIMIT) {
                    trendingPicks.add(item);
                }
            }
            
            // Add from explore (trending)
            for (YTItemResult item : explorePicks) {
                if (addedIds.add(item.getId()) && trendingPicks.size() < QUICK_PICKS_LIMIT) {
                    trendingPicks.add(item);
                }
            }
            
            Log.d(TAG, "===== Final trending picks: " + trendingPicks.size() + " items (from " + 
                  homePicks.size() + " home, " + historyPicks.size() + " history, " + 
                  explorePicks.size() + " explore) =====");
            return trendingPicks;
        }
        
        /**
         * Fetch content from YouTube home page.
         */
        private List<YTItemResult> fetchHomeContent() {
            List<YTItemResult> homePicks = new ArrayList<>();
            
            try {
                Log.d(TAG, "Calling InnertubeBridge.getHomeSync()...");
                HomePageResult homeResult = InnertubeBridge.getHomeSync();
                
                if (homeResult == null) {
                    Log.w(TAG, "getHomeSync() returned null");
                    return homePicks;
                }
                
                if (homeResult.getSections() == null) {
                    Log.w(TAG, "Home sections are null");
                    return homePicks;
                }
                
                Log.d(TAG, "Home page returned " + homeResult.getSections().size() + " sections");
                
                int songCount = 0;
                for (HomeSectionResult section : homeResult.getSections()) {
                    if (section.getItems() != null) {
                        Log.d(TAG, "  Section '" + section.getTitle() + "' has " + section.getItems().size() + " items");
                        for (YTItemResult item : section.getItems()) {
                            if ("song".equals(item.getType()) && homePicks.size() < QUICK_PICKS_LIMIT) {
                                homePicks.add(item);
                                songCount++;
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Extracted " + songCount + " songs from home page");
            } catch (Exception e) {
                Log.e(TAG, "Error fetching home content", e);
                throw e;
            }
            
            return homePicks;
        }
        
        /**
         * Fetch content from YouTube explore page.
         */
        private List<YTItemResult> fetchExploreContent() {
            List<YTItemResult> explorePicks = new ArrayList<>();
            
            try {
                Log.d(TAG, "Calling InnertubeBridge.getExplorePage()...");
                ExplorePageResult exploreResult = InnertubeBridge.getExplorePage();
                
                if (exploreResult == null) {
                    Log.w(TAG, "getExplorePage() returned null");
                    return explorePicks;
                }
                
                if (exploreResult.getSections() == null) {
                    Log.w(TAG, "Explore sections are null");
                    return explorePicks;
                }
                
                Log.d(TAG, "Explore page returned " + exploreResult.getSections().size() + " sections");
                
                int songCount = 0;
                for (HomeSectionResult section : exploreResult.getSections()) {
                    if (section.getItems() != null) {
                        Log.d(TAG, "  Section '" + section.getTitle() + "' has " + section.getItems().size() + " items");
                        for (YTItemResult item : section.getItems()) {
                            if ("song".equals(item.getType()) && explorePicks.size() < QUICK_PICKS_LIMIT) {
                                explorePicks.add(item);
                                songCount++;
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Extracted " + songCount + " songs from explore page");
            } catch (Exception e) {
                Log.e(TAG, "Error fetching explore content", e);
                throw e;
            }
            
            return explorePicks;
        }
        
        /**
         * Fetch user's music history from YouTube.
         */
        private List<YTItemResult> fetchMusicHistory() {
            List<YTItemResult> historyPicks = new ArrayList<>();
            
            try {
                Log.d(TAG, "Calling InnertubeBridge.getMusicHistorySync()...");
                HomePageResult historyResult = InnertubeBridge.getMusicHistorySync();
                
                if (historyResult == null) {
                    Log.w(TAG, "getMusicHistorySync() returned null");
                    return historyPicks;
                }
                
                if (historyResult.getSections() == null) {
                    Log.w(TAG, "Music history sections are null");
                    return historyPicks;
                }
                
                Log.d(TAG, "Music history returned " + historyResult.getSections().size() + " sections");
                
                int songCount = 0;
                for (HomeSectionResult section : historyResult.getSections()) {
                    if (section.getItems() != null) {
                        Log.d(TAG, "  Section '" + section.getTitle() + "' has " + section.getItems().size() + " items");
                        for (YTItemResult item : section.getItems()) {
                            if ("song".equals(item.getType()) && historyPicks.size() < QUICK_PICKS_LIMIT) {
                                historyPicks.add(item);
                                songCount++;
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Extracted " + songCount + " songs from music history");
            } catch (Exception e) {
                Log.e(TAG, "Error fetching music history", e);
                throw e;
            }
            
            return historyPicks;
        }
        
        /**
         * Fetch user's recent activity from YouTube.
         */
        private List<YTItemResult> fetchRecentActivity() {
            List<YTItemResult> activityPicks = new ArrayList<>();
            
            try {
                Log.d(TAG, "Calling InnertubeBridge.getRecentActivitySync()...");
                HomePageResult activityResult = InnertubeBridge.getRecentActivitySync();
                
                if (activityResult == null) {
                    Log.w(TAG, "getRecentActivitySync() returned null");
                    return activityPicks;
                }
                
                if (activityResult.getSections() == null) {
                    Log.w(TAG, "Recent activity sections are null");
                    return activityPicks;
                }
                
                Log.d(TAG, "Recent activity returned " + activityResult.getSections().size() + " sections");
                
                int songCount = 0;
                for (HomeSectionResult section : activityResult.getSections()) {
                    if (section.getItems() != null) {
                        Log.d(TAG, "  Section '" + section.getTitle() + "' has " + section.getItems().size() + " items");
                        for (YTItemResult item : section.getItems()) {
                            if ("song".equals(item.getType()) && activityPicks.size() < QUICK_PICKS_LIMIT) {
                                activityPicks.add(item);
                                songCount++;
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Extracted " + songCount + " songs from recent activity");
            } catch (Exception e) {
                Log.e(TAG, "Error fetching recent activity", e);
                throw e;
            }
            
            return activityPicks;
        }
        
        /**
         * Convert QuickPickSong to YTItemResult.
         */
        private YTItemResult convertToYTItem(QuickPicksDatabase.QuickPickSong song) {
            List<music.resona.online.bridge.models.ArtistResult> artists = new ArrayList<>();
            
            // Use "Unknown Artist" if artist is null or empty (for old database records)
            String artistName = song.artist;
            if (artistName == null || artistName.trim().isEmpty()) {
                artistName = "Unknown Artist";
            }
            
            artists.add(new music.resona.online.bridge.models.ArtistResult(
                song.artistId != null ? song.artistId : "",
                artistName
            ));
            
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
     * Result wrapper for Quick Picks generation.
     */
    private static class QuickPicksResult {
        final List<YTItemResult> picks;
        final boolean isPersonalized;
        
        QuickPicksResult(List<YTItemResult> picks, boolean isPersonalized) {
            this.picks = picks;
            this.isPersonalized = isPersonalized;
        }
    }
    
    /**
     * Callback interface for Quick Picks operations.
     */
    public interface QuickPicksCallback {
        /**
         * Called when Quick Picks are successfully generated.
         * @param picks List of recommended songs
         * @param personalized Whether picks are personalized or trending fallback
         */
        void onSuccess(List<YTItemResult> picks, boolean personalized);
        
        /**
         * Called when an error occurs.
         * @param error Error message
         */
        void onError(String error);
    }
}