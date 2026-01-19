package music.resona.lyrics;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import music.resona.lyrics.providers.KuGouProvider;
import music.resona.lyrics.providers.LrcLibProvider;
import music.resona.lyrics.providers.YouTubeMusicProvider;
import music.resona.lyrics.providers.YouTubeSubtitlesProvider;

/**
 * LyricsManager - Coordinates multiple lyrics providers
 * 
 * Features:
 * - Multiple provider fallback (LrcLib → KuGou → YouTube Subtitles → YouTube Music)
 * - Caching with LRU cache
 * - Prefers time-synced lyrics when available
 * - Background fetching with callbacks
 */
public class LyricsManager {
    
    private static final String TAG = "LyricsManager";
    private static final int CACHE_SIZE = 50; // Cache 50 songs' lyrics
    
    private static volatile LyricsManager instance;
    
    private final List<LyricsProvider> providers;
    private final LruCache<String, LyricsResult> cache;
    private final ExecutorService executor;
    
    // Video-based providers need special handling
    private final YouTubeMusicProvider youtubeMusicProvider;
    private final YouTubeSubtitlesProvider youtubeSubtitlesProvider;
    
    private LyricsManager() {
        this.cache = new LruCache<>(CACHE_SIZE);
        this.executor = Executors.newFixedThreadPool(2);
        
        // Initialize providers
        this.youtubeMusicProvider = new YouTubeMusicProvider();
        this.youtubeSubtitlesProvider = new YouTubeSubtitlesProvider();
        
        this.providers = new ArrayList<>();
        providers.add(new LrcLibProvider());      // Priority 1 - Best for synced lyrics
        providers.add(new KuGouProvider());       // Priority 2 - Good for Asian music
        providers.add(youtubeSubtitlesProvider);  // Priority 3 - Video subtitles
        providers.add(youtubeMusicProvider);      // Priority 4 - YouTube Music lyrics
        
        // Sort by priority
        Collections.sort(providers, Comparator.comparingInt(LyricsProvider::getPriority));
    }
    
    public static LyricsManager getInstance() {
        if (instance == null) {
            synchronized (LyricsManager.class) {
                if (instance == null) {
                    instance = new LyricsManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Create cache key from song info
     */
    private String createCacheKey(String title, String artist) {
        return (title + "|" + artist).toLowerCase();
    }
    
    /**
     * Get lyrics synchronously (blocks current thread)
     * 
     * @param title Song title
     * @param artist Artist name  
     * @param album Album name (optional)
     * @param durationMs Duration in milliseconds (optional)
     * @param videoId YouTube video ID (optional, for YouTube providers)
     * @param preferSynced If true, prefer synced lyrics over plain text
     * @return LyricsResult or null if not found
     */
    @Nullable
    public LyricsResult getLyricsSync(@NonNull String title, @NonNull String artist,
                                      @Nullable String album, long durationMs,
                                      @Nullable String videoId, boolean preferSynced) {
        
        String cacheKey = createCacheKey(title, artist);
        
        // Check cache first
        LyricsResult cached = cache.get(cacheKey);
        if (cached != null) {
            Log.d(TAG, "Cache hit for: " + title);
            return cached;
        }
        
        // Set video ID for YouTube providers
        youtubeSubtitlesProvider.setVideoId(videoId);
        youtubeMusicProvider.setVideoId(videoId);
        
        Log.d(TAG, "Fetching lyrics for: " + title + " - " + artist);
        
        LyricsResult bestResult = null;
        
        // Try each provider in priority order
        for (LyricsProvider provider : providers) {
            try {
                Log.d(TAG, "Trying provider: " + provider.getName());
                LyricsResult result = provider.fetchLyrics(title, artist, album, durationMs);
                
                if (result != null) {
                    Log.d(TAG, "Found lyrics from: " + provider.getName() + 
                              " (synced: " + result.isSynced() + ")");
                    
                    // If we prefer synced and this is synced, use it immediately
                    if (preferSynced && result.isSynced()) {
                        cache.put(cacheKey, result);
                        return result;
                    }
                    
                    // Keep track of best result
                    if (bestResult == null) {
                        bestResult = result;
                    } else if (result.isSynced() && !bestResult.isSynced()) {
                        // Prefer synced over non-synced
                        bestResult = result;
                    }
                    
                    // If we don't need synced and have a result, stop
                    if (!preferSynced) {
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error from provider " + provider.getName(), e);
            }
        }
        
        if (bestResult != null) {
            cache.put(cacheKey, bestResult);
        }
        
        return bestResult;
    }
    
    /**
     * Callback interface for async lyrics fetching
     */
    public interface LyricsCallback {
        void onLyricsLoaded(@Nullable LyricsResult result);
        void onError(Exception e);
    }
    
    /**
     * Get lyrics asynchronously
     */
    public void getLyricsAsync(@NonNull String title, @NonNull String artist,
                               @Nullable String album, long durationMs,
                               @Nullable String videoId, boolean preferSynced,
                               @NonNull LyricsCallback callback) {
        
        String cacheKey = createCacheKey(title, artist);
        
        // Check cache first (on calling thread)
        LyricsResult cached = cache.get(cacheKey);
        if (cached != null) {
            Log.d(TAG, "Cache hit for: " + title);
            callback.onLyricsLoaded(cached);
            return;
        }
        
        // Fetch in background
        executor.execute(() -> {
            try {
                LyricsResult result = getLyricsSync(title, artist, album, durationMs, videoId, preferSynced);
                callback.onLyricsLoaded(result);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * Clear the lyrics cache
     */
    public void clearCache() {
        cache.evictAll();
        Log.d(TAG, "Cache cleared");
    }
    
    /**
     * Get cache size
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Check if lyrics are cached for a song
     */
    public boolean hasCachedLyrics(@NonNull String title, @NonNull String artist) {
        return cache.get(createCacheKey(title, artist)) != null;
    }
    
    /**
     * Pre-cache lyrics for a song (call this during song loading)
     */
    public void prefetchLyrics(@NonNull String title, @NonNull String artist,
                               @Nullable String album, long durationMs,
                               @Nullable String videoId) {
        if (!hasCachedLyrics(title, artist)) {
            executor.execute(() -> {
                getLyricsSync(title, artist, album, durationMs, videoId, true);
            });
        }
    }
}
