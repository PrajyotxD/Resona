package music.resona.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite database for Quick Picks feature.
 * Stores songs, play events, and song relationships to generate personalized recommendations.
 */
public class QuickPicksDatabase extends SQLiteOpenHelper {
    
    private static final String TAG = "QuickPicksDatabase";
    private static final String DATABASE_NAME = "quick_picks.db";
    private static final int DATABASE_VERSION = 1;
    
    // Table names
    private static final String TABLE_SONG = "song";
    private static final String TABLE_EVENT = "event";
    private static final String TABLE_RELATED_SONG_MAP = "related_song_map";
    
    // Song table columns
    private static final String SONG_ID = "id";
    private static final String SONG_VIDEO_ID = "video_id";
    private static final String SONG_TITLE = "title";
    private static final String SONG_ARTIST = "artist";
    private static final String SONG_ARTIST_ID = "artist_id";
    private static final String SONG_THUMBNAIL = "thumbnail";
    private static final String SONG_DURATION = "duration";
    private static final String SONG_PLAY_COUNT = "play_count";
    private static final String SONG_LAST_PLAYED = "last_played";
    private static final String SONG_ADDED_DATE = "added_date";
    
    // Event table columns
    private static final String EVENT_ID = "id";
    private static final String EVENT_VIDEO_ID = "video_id";
    private static final String EVENT_TYPE = "type"; // play, skip, complete
    private static final String EVENT_TIMESTAMP = "timestamp";
    private static final String EVENT_DURATION = "duration"; // how long played in ms
    
    // Related song map columns
    private static final String MAP_ID = "id";
    private static final String MAP_SOURCE_VIDEO_ID = "source_video_id";
    private static final String MAP_RELATED_VIDEO_ID = "related_video_id";
    private static final String MAP_FREQUENCY = "frequency"; // how often they appear together
    private static final String MAP_LAST_UPDATED = "last_updated";
    
    private static QuickPicksDatabase instance;
    
    private QuickPicksDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    public static synchronized QuickPicksDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new QuickPicksDatabase(context.getApplicationContext());
        }
        return instance;
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create song table
        String createSongTable = "CREATE TABLE " + TABLE_SONG + " (" +
                SONG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                SONG_VIDEO_ID + " TEXT UNIQUE NOT NULL, " +
                SONG_TITLE + " TEXT NOT NULL, " +
                SONG_ARTIST + " TEXT, " +
                SONG_ARTIST_ID + " TEXT, " +
                SONG_THUMBNAIL + " TEXT, " +
                SONG_DURATION + " INTEGER, " +
                SONG_PLAY_COUNT + " INTEGER DEFAULT 0, " +
                SONG_LAST_PLAYED + " INTEGER, " +
                SONG_ADDED_DATE + " INTEGER NOT NULL" +
                ")";
        
        // Create event table
        String createEventTable = "CREATE TABLE " + TABLE_EVENT + " (" +
                EVENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                EVENT_VIDEO_ID + " TEXT NOT NULL, " +
                EVENT_TYPE + " TEXT NOT NULL, " +
                EVENT_TIMESTAMP + " INTEGER NOT NULL, " +
                EVENT_DURATION + " INTEGER, " +
                "FOREIGN KEY(" + EVENT_VIDEO_ID + ") REFERENCES " + TABLE_SONG + "(" + SONG_VIDEO_ID + ")" +
                ")";
        
        // Create related song map table
        String createRelatedMapTable = "CREATE TABLE " + TABLE_RELATED_SONG_MAP + " (" +
                MAP_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                MAP_SOURCE_VIDEO_ID + " TEXT NOT NULL, " +
                MAP_RELATED_VIDEO_ID + " TEXT NOT NULL, " +
                MAP_FREQUENCY + " INTEGER DEFAULT 1, " +
                MAP_LAST_UPDATED + " INTEGER NOT NULL, " +
                "UNIQUE(" + MAP_SOURCE_VIDEO_ID + ", " + MAP_RELATED_VIDEO_ID + "), " +
                "FOREIGN KEY(" + MAP_SOURCE_VIDEO_ID + ") REFERENCES " + TABLE_SONG + "(" + SONG_VIDEO_ID + "), " +
                "FOREIGN KEY(" + MAP_RELATED_VIDEO_ID + ") REFERENCES " + TABLE_SONG + "(" + SONG_VIDEO_ID + ")" +
                ")";
        
        db.execSQL(createSongTable);
        db.execSQL(createEventTable);
        db.execSQL(createRelatedMapTable);
        
        // Create indexes for better performance
        db.execSQL("CREATE INDEX idx_song_video_id ON " + TABLE_SONG + "(" + SONG_VIDEO_ID + ")");
        db.execSQL("CREATE INDEX idx_song_play_count ON " + TABLE_SONG + "(" + SONG_PLAY_COUNT + " DESC)");
        db.execSQL("CREATE INDEX idx_song_last_played ON " + TABLE_SONG + "(" + SONG_LAST_PLAYED + " DESC)");
        db.execSQL("CREATE INDEX idx_event_video_id ON " + TABLE_EVENT + "(" + EVENT_VIDEO_ID + ")");
        db.execSQL("CREATE INDEX idx_event_timestamp ON " + TABLE_EVENT + "(" + EVENT_TIMESTAMP + " DESC)");
        db.execSQL("CREATE INDEX idx_related_source ON " + TABLE_RELATED_SONG_MAP + "(" + MAP_SOURCE_VIDEO_ID + ")");
        db.execSQL("CREATE INDEX idx_related_frequency ON " + TABLE_RELATED_SONG_MAP + "(" + MAP_FREQUENCY + " DESC)");
        
        Log.d(TAG, "Database created successfully");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RELATED_SONG_MAP);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EVENT);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG);
        onCreate(db);
    }
    
    /**
     * Insert or update a song in the database.
     */
    public long insertOrUpdateSong(String videoId, String title, String artist, 
                                   String artistId, String thumbnail, int duration) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SONG_VIDEO_ID, videoId);
        values.put(SONG_TITLE, title);
        values.put(SONG_ARTIST, artist);
        values.put(SONG_ARTIST_ID, artistId);
        values.put(SONG_THUMBNAIL, thumbnail);
        values.put(SONG_DURATION, duration);
        values.put(SONG_ADDED_DATE, System.currentTimeMillis());
        
        long result = db.insertWithOnConflict(TABLE_SONG, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        
        if (result == -1) {
            // Song already exists, update it
            db.update(TABLE_SONG, values, SONG_VIDEO_ID + " = ?", new String[]{videoId});
        }
        
        return result;
    }
    
    /**
     * Record a play event and update song statistics.
     */
    public void recordPlayEvent(String videoId, long duration) {
        SQLiteDatabase db = this.getWritableDatabase();
        long timestamp = System.currentTimeMillis();
        
        // Insert event
        ContentValues eventValues = new ContentValues();
        eventValues.put(EVENT_VIDEO_ID, videoId);
        eventValues.put(EVENT_TYPE, "play");
        eventValues.put(EVENT_TIMESTAMP, timestamp);
        eventValues.put(EVENT_DURATION, duration);
        db.insert(TABLE_EVENT, null, eventValues);
        
        // Update song play count and last played
        ContentValues songValues = new ContentValues();
        songValues.put(SONG_LAST_PLAYED, timestamp);
        db.execSQL("UPDATE " + TABLE_SONG + 
                   " SET " + SONG_PLAY_COUNT + " = " + SONG_PLAY_COUNT + " + 1 " +
                   " WHERE " + SONG_VIDEO_ID + " = ?", new String[]{videoId});
        db.update(TABLE_SONG, songValues, SONG_VIDEO_ID + " = ?", new String[]{videoId});
        
        Log.d(TAG, "Recorded play event for: " + videoId);
    }
    
    /**
     * Record relationship between two songs (typically current and next song).
     */
    public void recordSongRelationship(String sourceVideoId, String relatedVideoId) {
        if (sourceVideoId == null || relatedVideoId == null || sourceVideoId.equals(relatedVideoId)) {
            return;
        }
        
        SQLiteDatabase db = this.getWritableDatabase();
        long timestamp = System.currentTimeMillis();
        
        // Try to update existing relationship
        int updated = db.rawQuery(
            "SELECT COUNT(*) FROM " + TABLE_RELATED_SONG_MAP + 
            " WHERE " + MAP_SOURCE_VIDEO_ID + " = ? AND " + MAP_RELATED_VIDEO_ID + " = ?",
            new String[]{sourceVideoId, relatedVideoId}
        ).getCount();
        
        if (updated > 0) {
            // Increment frequency
            db.execSQL("UPDATE " + TABLE_RELATED_SONG_MAP + 
                      " SET " + MAP_FREQUENCY + " = " + MAP_FREQUENCY + " + 1, " +
                      MAP_LAST_UPDATED + " = ? " +
                      " WHERE " + MAP_SOURCE_VIDEO_ID + " = ? AND " + MAP_RELATED_VIDEO_ID + " = ?",
                      new Object[]{timestamp, sourceVideoId, relatedVideoId});
        } else {
            // Insert new relationship
            ContentValues values = new ContentValues();
            values.put(MAP_SOURCE_VIDEO_ID, sourceVideoId);
            values.put(MAP_RELATED_VIDEO_ID, relatedVideoId);
            values.put(MAP_FREQUENCY, 1);
            values.put(MAP_LAST_UPDATED, timestamp);
            db.insert(TABLE_RELATED_SONG_MAP, null, values);
        }
        
        Log.d(TAG, "Recorded relationship: " + sourceVideoId + " -> " + relatedVideoId);
    }
    
    /**
     * Get recently played songs.
     */
    public List<QuickPickSong> getRecentlyPlayed(int limit) {
        List<QuickPickSong> songs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        Cursor cursor = db.query(
            TABLE_SONG,
            null,
            SONG_LAST_PLAYED + " IS NOT NULL",
            null,
            null,
            null,
            SONG_LAST_PLAYED + " DESC",
            String.valueOf(limit)
        );
        
        while (cursor.moveToNext()) {
            songs.add(cursorToSong(cursor));
        }
        cursor.close();
        
        return songs;
    }
    
    /**
     * Get most played songs.
     */
    public List<QuickPickSong> getMostPlayed(int limit) {
        List<QuickPickSong> songs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        Cursor cursor = db.query(
            TABLE_SONG,
            null,
            SONG_PLAY_COUNT + " > 0",
            null,
            null,
            null,
            SONG_PLAY_COUNT + " DESC, " + SONG_LAST_PLAYED + " DESC",
            String.valueOf(limit)
        );
        
        while (cursor.moveToNext()) {
            songs.add(cursorToSong(cursor));
        }
        cursor.close();
        
        return songs;
    }
    
    /**
     * Get related songs based on user's listening history.
     * Returns songs that frequently appear after songs the user has played.
     */
    public List<QuickPickSong> getRelatedSongs(int limit) {
        List<QuickPickSong> songs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        // Get related songs based on frequency, prioritizing songs related to recently played tracks
        String query = "SELECT s.*, SUM(r." + MAP_FREQUENCY + ") as total_frequency " +
                      "FROM " + TABLE_SONG + " s " +
                      "INNER JOIN " + TABLE_RELATED_SONG_MAP + " r ON s." + SONG_VIDEO_ID + " = r." + MAP_RELATED_VIDEO_ID + " " +
                      "INNER JOIN " + TABLE_SONG + " recent ON r." + MAP_SOURCE_VIDEO_ID + " = recent." + SONG_VIDEO_ID + " " +
                      "WHERE recent." + SONG_LAST_PLAYED + " IS NOT NULL " +
                      "GROUP BY s." + SONG_VIDEO_ID + " " +
                      "ORDER BY total_frequency DESC, r." + MAP_LAST_UPDATED + " DESC " +
                      "LIMIT ?";
        
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(limit)});
        
        while (cursor.moveToNext()) {
            songs.add(cursorToSong(cursor));
        }
        cursor.close();
        
        return songs;
    }
    
    /**
     * Get total number of play events.
     */
    public int getTotalPlayCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_EVENT, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }
    
    /**
     * Check if user has sufficient data for personalized recommendations.
     */
    public boolean hasSufficientData() {
        return getTotalPlayCount() >= 5; // At least 5 plays to start personalizing
    }
    
    /**
     * Get all songs from database.
     */
    public List<QuickPickSong> getAllSongs() {
        List<QuickPickSong> songs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        Cursor cursor = db.query(TABLE_SONG, null, null, null, null, null, SONG_ADDED_DATE + " DESC");
        
        while (cursor.moveToNext()) {
            songs.add(cursorToSong(cursor));
        }
        cursor.close();
        
        return songs;
    }
    
    /**
     * Convert cursor to QuickPickSong object.
     */
    private QuickPickSong cursorToSong(Cursor cursor) {
        QuickPickSong song = new QuickPickSong();
        song.videoId = cursor.getString(cursor.getColumnIndexOrThrow(SONG_VIDEO_ID));
        song.title = cursor.getString(cursor.getColumnIndexOrThrow(SONG_TITLE));
        song.artist = cursor.getString(cursor.getColumnIndexOrThrow(SONG_ARTIST));
        song.artistId = cursor.getString(cursor.getColumnIndexOrThrow(SONG_ARTIST_ID));
        song.thumbnail = cursor.getString(cursor.getColumnIndexOrThrow(SONG_THUMBNAIL));
        song.duration = cursor.getInt(cursor.getColumnIndexOrThrow(SONG_DURATION));
        song.playCount = cursor.getInt(cursor.getColumnIndexOrThrow(SONG_PLAY_COUNT));
        song.lastPlayed = cursor.getLong(cursor.getColumnIndexOrThrow(SONG_LAST_PLAYED));
        return song;
    }
    
    /**
     * Data class for Quick Pick songs.
     */
    public static class QuickPickSong {
        public String videoId;
        public String title;
        public String artist;
        public String artistId;
        public String thumbnail;
        public int duration;
        public int playCount;
        public long lastPlayed;
    }
}
