package music.resona.cache;

import android.util.Log;
import android.util.LruCache;

import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.models.PlaybackDataResult;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LRU cache for stream URLs to enable instant playback.
 * Prefetches URLs in background when songs are visible.
 */
public class StreamCache {
    private static final String TAG = "StreamCache";
    private static StreamCache instance;

    private final LruCache<String, CachedStream> cache;
    private final ExecutorService executor;
    private final Set<String> prefetchingIds;

    // Cache entries for 10 minutes (YouTube URLs last much longer, but we keep fresh)
    private static final long CACHE_DURATION_MS = 10 * 60 * 1000;

    private StreamCache() {
        // Cache up to 20 streams (~2MB memory for instant playback)
        cache = new LruCache<>(20);
        executor = Executors.newCachedThreadPool();
        prefetchingIds = new HashSet<>();
    }

    public static synchronized StreamCache getInstance() {
        if (instance == null) {
            instance = new StreamCache();
        }
        return instance;
    }

    /**
     * Pre-fetch LOW quality stream URL in background for instant playback.
     * Non-blocking, perfect for adapter bind methods.
     */
    public void prefetch(String videoId) {
        if (videoId == null || videoId.isEmpty()) {
            return;
        }

        // Check if already cached and valid
        CachedStream cached = cache.get(videoId);
        if (cached != null && !cached.isExpired()) {
            return; // Already cached
        }

        // Check if already prefetching
        synchronized (prefetchingIds) {
            if (!prefetchingIds.add(videoId)) {
                return; // Already prefetching
            }
        }

        executor.execute(() -> {
            try {
                Log.d(TAG, "Prefetching LOW quality for instant playback: " + videoId);
                
                // Fetch LOW quality for instant playback (fast fetch)
                PlaybackDataResult result = InnertubeBridge.getPlaybackDataWithQualitySync(
                    videoId, "LOW", false
                );
                
                if (result != null && result.getStreamUrl() != null && !result.getStreamUrl().isEmpty()) {
                    cache.put(videoId, new CachedStream(
                        result.getStreamUrl(),
                        System.currentTimeMillis()
                    ));
                    Log.d(TAG, "✓ Prefetched LOW quality: " + videoId);
                } else {
                    Log.w(TAG, "✗ Prefetch failed: " + videoId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error prefetching: " + videoId, e);
            } finally {
                synchronized (prefetchingIds) {
                    prefetchingIds.remove(videoId);
                }
            }
        });
    }

    /**
     * Get cached stream URL or fetch if not cached.
     * Returns LOW quality for instant playback.
     */
    public String getStreamUrl(String videoId) {
        if (videoId == null || videoId.isEmpty()) {
            return null;
        }

        // Check cache first
        CachedStream cached = cache.get(videoId);
        if (cached != null && !cached.isExpired()) {
            Log.d(TAG, "✓ CACHE HIT: " + videoId + " (expires in " + cached.getTimeUntilExpirySeconds() + "s)");
            return cached.url;
        }

        // Fetch fresh LOW quality
        try {
            Log.d(TAG, "✗ CACHE MISS: Fetching LOW quality for " + videoId);
            long startTime = System.currentTimeMillis();
            
            PlaybackDataResult result = InnertubeBridge.getPlaybackDataWithQualitySync(
                videoId, "LOW", false
            );
            
            long fetchTime = System.currentTimeMillis() - startTime;
            
            if (result != null && result.getStreamUrl() != null && !result.getStreamUrl().isEmpty()) {
                cache.put(videoId, new CachedStream(
                    result.getStreamUrl(),
                    System.currentTimeMillis()
                ));
                Log.d(TAG, "✓ Fetched LOW quality in " + fetchTime + "ms: " + videoId);
                return result.getStreamUrl();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching stream: " + videoId, e);
        }

        return null;
    }

    /**
     * Get cached URL without fetching (returns null if not cached).
     * Useful for checking cache status.
     */
    public String getCachedUrlOrNull(String videoId) {
        CachedStream cached = cache.get(videoId);
        if (cached != null && !cached.isExpired()) {
            return cached.url;
        }
        return null;
    }

    /**
     * Check if URL is cached and valid
     */
    public boolean isCached(String videoId) {
        CachedStream cached = cache.get(videoId);
        return cached != null && !cached.isExpired();
    }

    /**
     * Clear expired entries from cache
     */
    public void clearExpired() {
        cache.evictAll();
        Log.d(TAG, "Cleared expired cache entries");
    }

    /**
     * Clear all cache
     */
    public void clearAll() {
        cache.evictAll();
        Log.d(TAG, "Cleared all cache");
    }

    /**
     * Get cache size
     */
    public int getCacheSize() {
        return cache.size();
    }

    private static class CachedStream {
        final String url;
        final long timestamp;

        CachedStream(String url, long timestamp) {
            this.url = url;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }

        long getTimeUntilExpirySeconds() {
            long remainingMs = CACHE_DURATION_MS - (System.currentTimeMillis() - timestamp);
            return Math.max(0, remainingMs / 1000);
        }
    }
}
