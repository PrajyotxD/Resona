package music.resona.database;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Local storage manager for listening history and content relationships.
 * Acts as a simple in-memory database with persistence to SharedPreferences.
 */
public class RecommendationDatabase {
    
    private static final String PREFS_NAME = "resona_recommendations";
    private static final String KEY_LISTENING_HISTORY = "listening_history";
    private static final String KEY_RELATIONSHIPS = "content_relationships";
    private static final String KEY_FAVORITE_ARTISTS = "favorite_artists";
    
    private final SharedPreferences prefs;
    private final Gson gson;
    
    // In-memory caches
    private final Map<String, ListeningHistory> listeningHistoryMap;
    private final Map<String, ContentRelationship> relationshipsMap;
    private final Set<String> favoriteArtists;
    
    private static RecommendationDatabase instance;
    
    private RecommendationDatabase(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        
        this.listeningHistoryMap = new ConcurrentHashMap<>();
        this.relationshipsMap = new ConcurrentHashMap<>();
        this.favoriteArtists = ConcurrentHashMap.newKeySet();
        
        loadData();
    }
    
    public static synchronized RecommendationDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new RecommendationDatabase(context);
        }
        return instance;
    }
    
    // ==================== Listening History ====================
    
    /**
     * Record a song play event.
     */
    public void recordPlay(@NonNull String videoId, @NonNull String title, 
                           String artistName, String artistId, 
                           String albumName, String albumId, 
                           String thumbnail, long playDurationMs, float completionRate) {
        
        ListeningHistory history = listeningHistoryMap.get(videoId);
        
        if (history == null) {
            history = new ListeningHistory();
            history.setVideoId(videoId);
            history.setTitle(title);
            history.setArtistName(artistName);
            history.setArtistId(artistId);
            history.setAlbumName(albumName);
            history.setAlbumId(albumId);
            history.setThumbnail(thumbnail);
        }
        
        history.incrementPlayCount();
        history.addPlayTime(playDurationMs);
        history.setCompletionRate(completionRate);
        history.setLastPlayedAt(System.currentTimeMillis());
        
        listeningHistoryMap.put(videoId, history);
        
        // Create artist relationship if available
        if (artistId != null) {
            addRelationship(videoId, artistId, ContentRelationship.RelationType.SONG_TO_ARTIST);
            
            // Track favorite artists (played more than 10 times)
            if (history.getPlayCount() > 10) {
                favoriteArtists.add(artistId);
            }
        }
        
        // Create album relationship if available
        if (albumId != null) {
            addRelationship(videoId, albumId, ContentRelationship.RelationType.SONG_TO_ALBUM);
        }
        
        saveData();
    }
    
    /**
     * Record a skip event.
     */
    public void recordSkip(@NonNull String videoId) {
        ListeningHistory history = listeningHistoryMap.get(videoId);
        if (history != null) {
            history.incrementSkipCount();
            saveData();
        }
    }
    
    /**
     * Get listening history for a specific song.
     */
    @Nullable
    public ListeningHistory getHistory(@NonNull String videoId) {
        return listeningHistoryMap.get(videoId);
    }
    
    /**
     * Get all listening history sorted by last played time.
     */
    public List<ListeningHistory> getAllHistory() {
        return listeningHistoryMap.values().stream()
                .sorted((a, b) -> Long.compare(b.getLastPlayedAt(), a.getLastPlayedAt()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get recently played songs (last 7 days).
     */
    public List<ListeningHistory> getRecentlyPlayed(int limit) {
        long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        
        return listeningHistoryMap.values().stream()
                .filter(h -> h.getLastPlayedAt() > sevenDaysAgo)
                .sorted((a, b) -> Long.compare(b.getLastPlayedAt(), a.getLastPlayedAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Get most played songs.
     */
    public List<ListeningHistory> getMostPlayed(int limit) {
        return listeningHistoryMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.getPlayCount(), a.getPlayCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Get forgotten favorites - songs played frequently but not recently.
     */
    public List<ListeningHistory> getForgottenFavorites(int limit) {
        return listeningHistoryMap.values().stream()
                .filter(ListeningHistory::isForgottenFavorite)
                .sorted((a, b) -> Integer.compare(b.getPlayCount(), a.getPlayCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Get songs by engagement score.
     */
    public List<ListeningHistory> getTopEngaged(int limit) {
        return listeningHistoryMap.values().stream()
                .sorted((a, b) -> Float.compare(b.getEngagementScore(), a.getEngagementScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    // ==================== Relationships ====================
    
    /**
     * Add or update a content relationship.
     */
    public void addRelationship(@NonNull String sourceId, @NonNull String targetId, 
                                @NonNull ContentRelationship.RelationType type) {
        
        String key = sourceId + "_" + targetId + "_" + type.name();
        ContentRelationship relationship = relationshipsMap.get(key);
        
        if (relationship == null) {
            relationship = new ContentRelationship(sourceId, targetId, type);
            relationshipsMap.put(key, relationship);
        } else {
            relationship.incrementCoOccurrence();
        }
        
        saveData();
    }
    
    /**
     * Get all relationships for a source ID.
     */
    public List<ContentRelationship> getRelationships(@NonNull String sourceId, 
                                                       @Nullable ContentRelationship.RelationType type) {
        return relationshipsMap.values().stream()
                .filter(r -> r.getSourceId().equals(sourceId))
                .filter(r -> type == null || r.getRelationType() == type)
                .sorted((a, b) -> Float.compare(b.getStrength(), a.getStrength()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get related songs based on listening patterns.
     */
    public List<String> getRelatedSongs(@NonNull String videoId, int limit) {
        // Find songs from same artist
        List<ContentRelationship> artistRels = getRelationships(videoId, 
                ContentRelationship.RelationType.SONG_TO_ARTIST);
        
        Set<String> relatedSongs = new HashSet<>();
        
        for (ContentRelationship rel : artistRels) {
            String artistId = rel.getTargetId();
            
            // Find other songs by this artist
            relationshipsMap.values().stream()
                    .filter(r -> r.getTargetId().equals(artistId) && 
                                 r.getRelationType() == ContentRelationship.RelationType.SONG_TO_ARTIST)
                    .filter(r -> !r.getSourceId().equals(videoId))
                    .map(ContentRelationship::getSourceId)
                    .limit(limit)
                    .forEach(relatedSongs::add);
        }
        
        return new ArrayList<>(relatedSongs);
    }
    
    // ==================== Favorite Artists ====================
    
    public Set<String> getFavoriteArtists() {
        return new HashSet<>(favoriteArtists);
    }
    
    public boolean isFavoriteArtist(@NonNull String artistId) {
        return favoriteArtists.contains(artistId);
    }
    
    // ==================== Persistence ====================
    
    private void loadData() {
        try {
            // Load listening history
            String historyJson = prefs.getString(KEY_LISTENING_HISTORY, "[]");
            Type historyType = new TypeToken<List<ListeningHistory>>(){}.getType();
            List<ListeningHistory> historyList = gson.fromJson(historyJson, historyType);
            
            if (historyList != null) {
                for (ListeningHistory h : historyList) {
                    listeningHistoryMap.put(h.getVideoId(), h);
                }
            }
            
            // Load relationships
            String relsJson = prefs.getString(KEY_RELATIONSHIPS, "[]");
            Type relsType = new TypeToken<List<ContentRelationship>>(){}.getType();
            List<ContentRelationship> relsList = gson.fromJson(relsJson, relsType);
            
            if (relsList != null) {
                for (ContentRelationship r : relsList) {
                    relationshipsMap.put(r.getKey(), r);
                }
            }
            
            // Load favorite artists
            String favArtistsJson = prefs.getString(KEY_FAVORITE_ARTISTS, "[]");
            Type favType = new TypeToken<Set<String>>(){}.getType();
            Set<String> favSet = gson.fromJson(favArtistsJson, favType);
            
            if (favSet != null) {
                favoriteArtists.addAll(favSet);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void saveData() {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            
            // Save listening history
            List<ListeningHistory> historyList = new ArrayList<>(listeningHistoryMap.values());
            editor.putString(KEY_LISTENING_HISTORY, gson.toJson(historyList));
            
            // Save relationships
            List<ContentRelationship> relsList = new ArrayList<>(relationshipsMap.values());
            editor.putString(KEY_RELATIONSHIPS, gson.toJson(relsList));
            
            // Save favorite artists
            editor.putString(KEY_FAVORITE_ARTISTS, gson.toJson(favoriteArtists));
            
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Clear all data (for testing or reset).
     */
    public void clearAll() {
        listeningHistoryMap.clear();
        relationshipsMap.clear();
        favoriteArtists.clear();
        prefs.edit().clear().apply();
    }
}
