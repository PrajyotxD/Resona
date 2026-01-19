package music.resona.lyrics.providers;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import music.resona.lyrics.LyricsProvider;
import music.resona.lyrics.LyricsResult;

/**
 * KuGou lyrics provider
 * 
 * Chinese lyrics provider with good support for synced lyrics.
 * Uses KuGou's search and lyrics API.
 */
public class KuGouProvider implements LyricsProvider {
    
    private static final String TAG = "KuGouProvider";
    private static final String SEARCH_URL = "https://mobileservice.kugou.com/api/v3/lyric/search";
    private static final String LYRICS_URL = "https://lyrics.kugou.com/download";
    private static final int TIMEOUT_MS = 10000;
    
    @NonNull
    @Override
    public String getName() {
        return "KuGou";
    }
    
    @Override
    public int getPriority() {
        return 2;
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
            // Step 1: Search for the song
            String keyword = title + " " + artist;
            String searchUrl = SEARCH_URL + "?keyword=" + URLEncoder.encode(keyword, "UTF-8") 
                + "&ver=1&man=yes&client=pc";
            
            Log.d(TAG, "Searching KuGou for: " + keyword);
            
            String searchResponse = httpGet(searchUrl);
            if (searchResponse == null) return null;
            
            JSONObject searchJson = new JSONObject(searchResponse);
            if (searchJson.optInt("status", 0) != 200) {
                return null;
            }
            
            JSONArray candidates = searchJson.optJSONObject("data")
                .optJSONArray("info");
            
            if (candidates == null || candidates.length() == 0) {
                Log.d(TAG, "No results found for: " + keyword);
                return null;
            }
            
            // Find best match considering duration if available
            JSONObject bestMatch = findBestMatch(candidates, title, artist, durationMs);
            if (bestMatch == null) {
                bestMatch = candidates.getJSONObject(0);
            }
            
            String id = bestMatch.optString("id");
            String accessKey = bestMatch.optString("accesskey");
            
            if (id.isEmpty() || accessKey.isEmpty()) {
                return null;
            }
            
            // Step 2: Fetch lyrics
            String lyricsUrl = LYRICS_URL + "?ver=1&client=pc&id=" + id 
                + "&accesskey=" + accessKey + "&fmt=lrc&charset=utf8";
            
            String lyricsResponse = httpGet(lyricsUrl);
            if (lyricsResponse == null) return null;
            
            JSONObject lyricsJson = new JSONObject(lyricsResponse);
            if (lyricsJson.optInt("status", 0) != 200) {
                return null;
            }
            
            String encodedLyrics = lyricsJson.optString("content", "");
            if (encodedLyrics.isEmpty()) {
                return null;
            }
            
            // Decode base64 lyrics
            String lrc = new String(Base64.decode(encodedLyrics, Base64.DEFAULT), StandardCharsets.UTF_8);
            
            var syncedLines = LyricsResult.parseLrc(lrc);
            if (!syncedLines.isEmpty()) {
                Log.d(TAG, "Found synced lyrics with " + syncedLines.size() + " lines");
                return LyricsResult.synced(getName(), syncedLines);
            }
            
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching KuGou lyrics", e);
            return null;
        }
    }
    
    @Nullable
    private JSONObject findBestMatch(JSONArray candidates, String title, String artist, long durationMs) {
        try {
            String titleLower = title.toLowerCase();
            String artistLower = artist.toLowerCase();
            
            JSONObject bestMatch = null;
            int bestScore = -1;
            
            for (int i = 0; i < candidates.length(); i++) {
                JSONObject candidate = candidates.getJSONObject(i);
                String songName = candidate.optString("song", "").toLowerCase();
                String singerName = candidate.optString("singer", "").toLowerCase();
                long duration = candidate.optLong("duration", 0) * 1000;
                
                int score = 0;
                
                // Title match
                if (songName.contains(titleLower) || titleLower.contains(songName)) {
                    score += 3;
                }
                
                // Artist match
                if (singerName.contains(artistLower) || artistLower.contains(singerName)) {
                    score += 2;
                }
                
                // Duration match (within 5 seconds)
                if (durationMs > 0 && duration > 0) {
                    long diff = Math.abs(durationMs - duration);
                    if (diff < 5000) {
                        score += 2;
                    } else if (diff < 15000) {
                        score += 1;
                    }
                }
                
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = candidate;
                }
            }
            
            return bestMatch;
        } catch (Exception e) {
            return null;
        }
    }
    
    @Nullable
    private String httpGet(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "HTTP error", e);
        }
        return null;
    }
}
