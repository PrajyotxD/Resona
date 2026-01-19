package music.resona.lyrics.providers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import music.resona.lyrics.LyricsProvider;
import music.resona.lyrics.LyricsResult;

/**
 * LrcLib.net lyrics provider
 * 
 * Primary provider for time-synced and plain lyrics.
 * API: https://lrclib.net/api/get?artist_name=X&track_name=Y&album_name=Z&duration=D
 * 
 * Returns both synced (LRC) and plain lyrics when available.
 */
public class LrcLibProvider implements LyricsProvider {
    
    private static final String TAG = "LrcLibProvider";
    private static final String BASE_URL = "https://lrclib.net/api/get";
    private static final int TIMEOUT_MS = 10000;
    
    @NonNull
    @Override
    public String getName() {
        return "LrcLib";
    }
    
    @Override
    public int getPriority() {
        return 1; // Highest priority
    }
    
    @Override
    public boolean supportsSyncedLyrics() {
        return true;
    }
    
    @Nullable
    @Override
    public LyricsResult fetchLyrics(@NonNull String title, @NonNull String artist,
                                    @Nullable String album, long durationMs) {
        try {
            // Build URL with query parameters
            StringBuilder urlBuilder = new StringBuilder(BASE_URL);
            urlBuilder.append("?track_name=").append(URLEncoder.encode(title, "UTF-8"));
            urlBuilder.append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"));
            
            if (album != null && !album.isEmpty()) {
                urlBuilder.append("&album_name=").append(URLEncoder.encode(album, "UTF-8"));
            }
            
            if (durationMs > 0) {
                // LrcLib expects duration in seconds
                urlBuilder.append("&duration=").append(durationMs / 1000);
            }
            
            Log.d(TAG, "Fetching lyrics: " + title + " - " + artist);
            
            URL url = new URL(urlBuilder.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "Resona Music Player/1.0");
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                return parseResponse(response.toString());
            } else if (responseCode == 404) {
                Log.d(TAG, "Lyrics not found for: " + title);
                return null;
            } else {
                Log.w(TAG, "HTTP error: " + responseCode);
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching lyrics", e);
            return null;
        }
    }
    
    @Nullable
    private LyricsResult parseResponse(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            
            String syncedLyrics = obj.optString("syncedLyrics", null);
            String plainLyrics = obj.optString("plainLyrics", null);
            
            if (syncedLyrics != null && !syncedLyrics.isEmpty()) {
                // Has synced lyrics
                var syncedLines = LyricsResult.parseLrc(syncedLyrics);
                if (!syncedLines.isEmpty()) {
                    if (plainLyrics != null && !plainLyrics.isEmpty()) {
                        return LyricsResult.both(getName(), syncedLines, plainLyrics);
                    }
                    return LyricsResult.synced(getName(), syncedLines);
                }
            }
            
            if (plainLyrics != null && !plainLyrics.isEmpty()) {
                return LyricsResult.plain(getName(), plainLyrics);
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing response", e);
            return null;
        }
    }
}
