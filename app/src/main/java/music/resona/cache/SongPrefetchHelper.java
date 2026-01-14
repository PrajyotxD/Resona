package music.resona.cache;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import music.resona.models.Song;
import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.models.PlaybackDataResult;
import music.resona.online.bridge.models.YTItemResult;

/**
 * Prefetches stream URLs for songs to enable instant playback.
 * Uses a background thread pool to fetch URLs before user clicks.
 */
public class SongPrefetchHelper {
    
    private static final String TAG = "SongPrefetchHelper";
    private static volatile SongPrefetchHelper instance;
    
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, CachedStreamUrl> urlCache = new ConcurrentHashMap<>();
    private final Set<String> prefetchingIds = ConcurrentHashMap.newKeySet();
    
    // Cache entry with expiration
    private static class CachedStreamUrl {
        final String url;
        final long expiryTimeMs;
        final String title;
        final String artist;
        
        CachedStreamUrl(String url, long expiryTimeMs, String title, String artist) {
            this.url = url;
            this.expiryTimeMs = expiryTimeMs;
            this.title = title;
            this.artist = artist;
        }
        
        boolean isValid() {
            // Consider valid if more than 5 minutes before expiry
            return System.currentTimeMillis() < (expiryTimeMs - 5 * 60 * 1000);
        }
    }
    
    private SongPrefetchHelper() {}
    
    public static SongPrefetchHelper getInstance() {
        if (instance == null) {
            synchronized (SongPrefetchHelper.class) {
                if (instance == null) {
                    instance = new SongPrefetchHelper();
                }
            }
        }
        return instance;
    }
    
    /**
     * Prefetch stream URLs for a list of songs visible in the home feed.
     * Only prefetches the first N songs to limit network usage.
     * 
     * @param items List of YTItemResult from home feed
     * @param maxCount Maximum number of songs to prefetch (default 5)
     */
    public void prefetchFromHomeFeed(@NonNull List<YTItemResult> items, int maxCount) {
        int count = 0;
        for (YTItemResult item : items) {
            if (count >= maxCount) break;
            
            if ("song".equals(item.getType()) && item.getId() != null) {
                prefetchStreamUrl(item.getId(), item.getTitle(), getArtistName(item));
                count++;
            }
        }
        
        Log.d(TAG, "Started prefetching " + count + " songs from home feed");
    }
    
    /**
     * Prefetch stream URL for a single video ID.
     */
    public void prefetchStreamUrl(@NonNull String videoId, String title, String artist) {
        // Skip if already cached and valid
        CachedStreamUrl cached = urlCache.get(videoId);
        if (cached != null && cached.isValid()) {
            Log.d(TAG, "Already cached: " + videoId);
            return;
        }
        
        // Skip if already prefetching
        if (!prefetchingIds.add(videoId)) {
            Log.d(TAG, "Already prefetching: " + videoId);
            return;
        }
        
        executor.execute(() -> {
            try {
                Log.d(TAG, "Prefetching stream URL for: " + title);
                PlaybackDataResult result = InnertubeBridge.getPlaybackDataSync(videoId);
                
                if (result != null && result.getStreamUrl() != null && !result.getStreamUrl().isEmpty()) {
                    long expiryTimeMs = System.currentTimeMillis() + (result.getStreamExpiresInSeconds() * 1000L);
                    urlCache.put(videoId, new CachedStreamUrl(
                        result.getStreamUrl(),
                        expiryTimeMs,
                        title,
                        artist
                    ));
                    Log.d(TAG, "Prefetched successfully: " + title);
                } else {
                    Log.w(TAG, "Failed to prefetch: " + title);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error prefetching " + title, e);
            } finally {
                prefetchingIds.remove(videoId);
            }
        });
    }
    
    /**
     * Get cached stream URL if available.
     * 
     * @param videoId The video ID to look up
     * @return The stream URL if cached and valid, null otherwise
     */
    public String getCachedStreamUrl(@NonNull String videoId) {
        CachedStreamUrl cached = urlCache.get(videoId);
        if (cached != null && cached.isValid()) {
            return cached.url;
        }
        return null;
    }
    
    /**
     * Check if a video ID has a valid cached stream URL.
     */
    public boolean isCached(@NonNull String videoId) {
        CachedStreamUrl cached = urlCache.get(videoId);
        return cached != null && cached.isValid();
    }
    
    /**
     * Get cache expiry time for a video ID.
     * 
     * @return Expiry time in milliseconds, or -1 if not cached
     */
    public long getCacheExpiry(@NonNull String videoId) {
        CachedStreamUrl cached = urlCache.get(videoId);
        if (cached != null) {
            return cached.expiryTimeMs;
        }
        return -1;
    }
    
    /**
     * Clear all cached URLs.
     */
    public void clearCache() {
        urlCache.clear();
        Log.d(TAG, "Cache cleared");
    }
    
    /**
     * Get the current cache size.
     */
    public int getCacheSize() {
        return urlCache.size();
    }
    
    /**
     * Clean up expired entries from cache.
     */
    public void cleanupExpiredEntries() {
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, CachedStreamUrl> entry : urlCache.entrySet()) {
            if (!entry.getValue().isValid()) {
                expired.add(entry.getKey());
            }
        }
        
        for (String id : expired) {
            urlCache.remove(id);
        }
        
        if (!expired.isEmpty()) {
            Log.d(TAG, "Cleaned up " + expired.size() + " expired entries");
        }
    }
    
    private String getArtistName(YTItemResult item) {
        if (item.getArtists() != null && !item.getArtists().isEmpty()) {
            return item.getArtists().get(0).getName();
        }
        return null;
    }
    
    /**
     * Shutdown the executor - call when app is destroyed.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
