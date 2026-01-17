package music.resona.playback;

import android.content.Context;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Multi-tier playback cache system with download, player cache, and network streaming.
 * Implements 512KB chunked loading with smart preloading strategies.
 */
public class PlaybackCache {
    
    private static final String TAG = "PlaybackCache";
    private static final int CHUNK_SIZE = 512 * 1024; // 512 KB chunks
    private static final int MEMORY_CACHE_SIZE = 50 * 1024 * 1024; // 50 MB memory cache
    private static final int MAX_CONCURRENT_PRELOADS = 3;
    private static final String CACHE_DIR = "playback_cache";
    
    private final Context context;
    private final LruCache<String, CachedStream> memoryCache;
    private final ConcurrentHashMap<String, String> urlCache;
    private final File cacheDir;
    private final Executor preloadExecutor;
    
    // Track ongoing preloads to avoid duplicates
    private final ConcurrentHashMap<String, Boolean> activePreloads;
    
    public PlaybackCache(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.memoryCache = new LruCache<String, CachedStream>(MEMORY_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, CachedStream stream) {
                return stream.data.length;
            }
        };
        this.urlCache = new ConcurrentHashMap<>();
        this.activePreloads = new ConcurrentHashMap<>();
        this.preloadExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_PRELOADS);
        
        // Create cache directory
        this.cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }
    
    /**
     * Get cached URL for immediate playback (Tier 1: Download Cache).
     */
    @Nullable
    public String getCachedUrl(@NonNull String videoId) {
        // Check if fully downloaded file exists
        File downloadedFile = new File(cacheDir, videoId + "_full.mp3");
        if (downloadedFile.exists()) {
            return downloadedFile.getAbsolutePath();
        }
        
        // Check memory cache (Tier 2: Player Cache)
        CachedStream stream = memoryCache.get(videoId);
        if (stream != null && stream.isComplete()) {
            return stream.getLocalUri();
        }
        
        return null; // Will need network streaming (Tier 3)
    }
    
    /**
     * Preload audio for instant playback start.
     */
    public void preload(@NonNull String videoId, @NonNull String audioUrl) {
        if (activePreloads.putIfAbsent(videoId, true) != null) {
            return; // Already preloading this video
        }
        
        preloadExecutor.execute(() -> {
            try {
                // Load first chunk for instant start
                byte[] firstChunk = downloadChunk(audioUrl, 0, CHUNK_SIZE);
                if (firstChunk != null) {
                    CachedStream stream = new CachedStream(videoId, firstChunk, false);
                    memoryCache.put(videoId, stream);
                    urlCache.put(videoId, audioUrl);
                    
                    // Continue loading in background
                    loadRemainingChunks(videoId, audioUrl, CHUNK_SIZE);
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "Failed to preload " + videoId, e);
            } finally {
                activePreloads.remove(videoId);
            }
        });
    }
    
    /**
     * Get stream with progressive loading support.
     */
    @Nullable
    public CachedStream getStream(@NonNull String videoId) {
        // Check memory cache first
        CachedStream stream = memoryCache.get(videoId);
        if (stream != null) {
            return stream;
        }
        
        // Check if URL is available for streaming
        String audioUrl = urlCache.get(videoId);
        if (audioUrl != null) {
            // Create streaming placeholder
            return new CachedStream(videoId, audioUrl);
        }
        
        return null;
    }
    
    /**
     * Download specific chunk of audio data.
     */
    @Nullable
    private byte[] downloadChunk(@NonNull String audioUrl, int startByte, int chunkSize) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        
        try {
            URL url = new URL(audioUrl);
            connection = (HttpURLConnection) url.openConnection();
            
            // Set range header for chunk download
            String rangeHeader = "bytes=" + startByte + "-" + (startByte + chunkSize - 1);
            connection.setRequestProperty("Range", rangeHeader);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_PARTIAL && responseCode != HttpURLConnection.HTTP_OK) {
                android.util.Log.w(TAG, "Unexpected response code: " + responseCode);
                return null;
            }
            
            inputStream = connection.getInputStream();
            
            // Read chunk data
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to download chunk", e);
            return null;
        } finally {
            if (inputStream != null) {
                try { inputStream.close(); } catch (IOException ignored) {}
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Load remaining chunks after first chunk.
     */
    private void loadRemainingChunks(@NonNull String videoId, @NonNull String audioUrl, int startOffset) {
        preloadExecutor.execute(() -> {
            try {
                CachedStream stream = memoryCache.get(videoId);
                if (stream == null) return;
                
                // Download in chunks until complete
                int offset = startOffset;
                while (!stream.isComplete()) {
                    byte[] chunk = downloadChunk(audioUrl, offset, CHUNK_SIZE);
                    if (chunk == null || chunk.length == 0) {
                        break; // End of file or error
                    }
                    
                    stream.appendChunk(chunk);
                    offset += chunk.length;
                    
                    // Check if we should continue (user might have skipped)
                    if (activePreloads.get(videoId) == null) {
                        break;
                    }
                }
                
                // Mark as complete
                stream.markComplete();
                
                // Optionally save to disk for offline access
                saveToDownloadCache(videoId, stream);
                
            } catch (Exception e) {
                android.util.Log.e(TAG, "Failed to load remaining chunks for " + videoId, e);
            }
        });
    }
    
    /**
     * Save complete stream to download cache for offline access.
     */
    private void saveToDownloadCache(@NonNull String videoId, @NonNull CachedStream stream) {
        if (!stream.isComplete()) return;
        
        File downloadFile = new File(cacheDir, videoId + "_full.mp3");
        
        try (FileOutputStream fos = new FileOutputStream(downloadFile)) {
            fos.write(stream.data);
            fos.flush();
            
            android.util.Log.d(TAG, "Saved complete stream to download cache: " + videoId);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to save to download cache", e);
        }
    }
    
    /**
     * Clear memory cache.
     */
    public void clearMemoryCache() {
        memoryCache.evictAll();
        android.util.Log.d(TAG, "Memory cache cleared");
    }
    
    /**
     * Clear download cache.
     */
    public void clearDownloadCache() {
        if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.delete()) {
                        android.util.Log.d(TAG, "Deleted cached file: " + file.getName());
                    }
                }
            }
        }
    }
    
    /**
     * Get cache statistics.
     */
    public CacheStats getStats() {
        int memoryItems = memoryCache.size();
        long memorySize = memoryCache.maxSize() - memoryCache.size(); // Available space
        
        int downloadItems = 0;
        long downloadSize = 0;
        
        if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                downloadItems = files.length;
                for (File file : files) {
                    downloadSize += file.length();
                }
            }
        }
        
        return new CacheStats(memoryItems, memorySize, downloadItems, downloadSize);
    }
    
    /**
     * Release resources.
     */
    public void release() {
        clearMemoryCache();
        urlCache.clear();
        activePreloads.clear();
        android.util.Log.d(TAG, "PlaybackCache released");
    }
    
    /**
     * Represents a cached audio stream with progressive loading.
     */
    public static class CachedStream {
        private final String videoId;
        private byte[] data;
        private boolean isComplete;
        private final String streamUrl; // For network streaming fallback
        
        // Constructor for progressive loading
        CachedStream(@NonNull String videoId, @NonNull byte[] initialData, boolean complete) {
            this.videoId = videoId;
            this.data = initialData.clone();
            this.isComplete = complete;
            this.streamUrl = null;
        }
        
        // Constructor for network streaming
        CachedStream(@NonNull String videoId, @NonNull String streamUrl) {
            this.videoId = videoId;
            this.data = new byte[0];
            this.isComplete = false;
            this.streamUrl = streamUrl;
        }
        
        public void appendChunk(@NonNull byte[] chunk) {
            byte[] newData = new byte[data.length + chunk.length];
            System.arraycopy(data, 0, newData, 0, data.length);
            System.arraycopy(chunk, 0, newData, data.length, chunk.length);
            this.data = newData;
        }
        
        public void markComplete() {
            this.isComplete = true;
        }
        
        public boolean isComplete() {
            return isComplete;
        }
        
        public String getLocalUri() {
            // Return data URI for small streams or temp file URI for large ones
            if (streamUrl != null) {
                return streamUrl; // Network streaming
            }
            return "data:audio/mpeg;base64," + android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
        }
        
        public int getSize() {
            return data.length;
        }
    }
    
    /**
     * Cache statistics for monitoring.
     */
    public static class CacheStats {
        public final int memoryItems;
        public final long memorySize;
        public final int downloadItems;
        public final long downloadSize;
        
        CacheStats(int memoryItems, long memorySize, int downloadItems, long downloadSize) {
            this.memoryItems = memoryItems;
            this.memorySize = memorySize;
            this.downloadItems = downloadItems;
            this.downloadSize = downloadSize;
        }
        
        @Override
        public String toString() {
            return String.format("Cache Stats: Memory(%d items, %d KB), Download(%d items, %d KB)",
                memoryItems, memorySize / 1024, downloadItems, downloadSize / 1024);
        }
    }
}