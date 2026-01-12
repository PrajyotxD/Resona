package music.resona.activity;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import music.resona.R;
import music.resona.activity.adapters.SongsAdapter;
import music.resona.activity.models.SongItem;
import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.callbacks.HomePageCallback;
import music.resona.online.bridge.exceptions.BridgeException;
import music.resona.online.bridge.models.AlbumPageResult;
import music.resona.online.bridge.models.ArtistResult;
import music.resona.online.bridge.models.HomePageResult;
import music.resona.online.bridge.models.HomeSectionResult;
import music.resona.online.bridge.models.PlaylistResult;
import music.resona.online.bridge.models.YTItemResult;

public class Vibe extends AppCompatActivity implements SongsAdapter.OnSongClickListener {

    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbar;
    private ImageView albumArtwork;
    private TextView playlistTitle;
    private TextView playlistMetadata;
    private TextView toolbarTitle;
    private View gradientBackground;
    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private SongsAdapter songsAdapter;
    private TextInputEditText searchInput;
    private MaterialButton btnSort;
    private ImageView smallThumbnail;
    private ImageView btnAdd;
    private ImageView btnDownload;
    private ImageView btnMore;
    private ImageView btnShuffleIcon;
    private ImageView btnPlay;
    
    private int currentGradientColor = Color.parseColor("#1DB954");
    private float lastOffset = 0f;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    
    // Intent extras
    private String browseId;
    private String title;
    private String subtitle;
    private String thumbnailUrl;
    
    // Song data
    private List<SongItem> allSongs = new ArrayList<>();
    private List<SongItem> filteredSongs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_vibe);
        
        getIntentData();
        initViews();
        setupWindowInsets();
        setupAlbumArtwork();
        setupScrollBehavior();
        setupRecyclerView();
        setupSearchAndSort();
        loadPlaylistData();
    }
    
    private void getIntentData() {
        // Get data from intent (passed from HomeFeed)
        browseId = getIntent().getStringExtra("browseId");
        title = getIntent().getStringExtra("title");
        subtitle = getIntent().getStringExtra("subtitle");
        thumbnailUrl = getIntent().getStringExtra("thumbnailUrl");
    }
    
    private void initViews() {
        appBarLayout = findViewById(R.id.app_bar);
        collapsingToolbar = findViewById(R.id.collapsing_toolbar);
        albumArtwork = findViewById(R.id.album_artwork);
        playlistTitle = findViewById(R.id.playlist_title);
        playlistMetadata = findViewById(R.id.playlist_metadata);
        toolbarTitle = findViewById(R.id.toolbar_title);
        gradientBackground = findViewById(R.id.gradient_background);
        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.rv_songs);
        searchInput = findViewById(R.id.search_input);
        btnSort = findViewById(R.id.btn_sort);
        smallThumbnail = findViewById(R.id.small_thumbnail);
        btnAdd = findViewById(R.id.btn_add);
        btnDownload = findViewById(R.id.btn_download);
        btnMore = findViewById(R.id.btn_more);
        btnShuffleIcon = findViewById(R.id.btn_shuffle_icon);
        btnPlay = findViewById(R.id.btn_play);
        
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        
        // Set initial data
        if (title != null) {
            playlistTitle.setText(title);
            toolbarTitle.setText(title);
        }
        if (subtitle != null) {
            playlistMetadata.setText(subtitle);
        }
    }
    
    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.coordinator_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            appBarLayout.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });
    }
    
    private void setupAlbumArtwork() {
        // Apply rounded corners to album artwork
        albumArtwork.setClipToOutline(true);
        albumArtwork.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 16f);
            }
        });
        
        // Load artwork from URL if available
        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            Glide.with(this)
                    .asBitmap()
                    .load(thumbnailUrl)
                    .transform(new RoundedCorners(16))
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                            albumArtwork.setImageBitmap(resource);
                            smallThumbnail.setImageBitmap(resource);
                            extractAndApplyPaletteColor(resource);
                        }
                    });
        }
    }
    
    private void extractAndApplyPaletteColor(Bitmap bitmap) {
        Palette.from(bitmap).generate(palette -> {
            if (palette != null) {
                // Get vibrant color or fallback to dominant/muted
                int vibrantColor = palette.getVibrantColor(
                    palette.getDominantColor(
                        palette.getMutedColor(Color.parseColor("#1DB954"))
                    )
                );
                
                // Darken the color for better background effect
                int darkenedColor = darkenColor(vibrantColor, 0.6f);
                
                animateGradientColor(currentGradientColor, darkenedColor);
                currentGradientColor = darkenedColor;
            }
        });
    }
    
    private void animateGradientColor(int fromColor, int toColor) {
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        colorAnimation.setDuration(600);
        colorAnimation.addUpdateListener(animator -> {
            int color = (int) animator.getAnimatedValue();
            applyGradient(color);
        });
        colorAnimation.start();
    }
    
    private void applyGradient(int baseColor) {
        int[] colors = {
            baseColor,
            Color.parseColor("#121212")
        };
        
        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            colors
        );
        gradient.setCornerRadius(0f);
        gradientBackground.setBackground(gradient);
    }
    
    private int darkenColor(int color, float factor) {
        int r = (int) (Color.red(color) * factor);
        int g = (int) (Color.green(color) * factor);
        int b = (int) (Color.blue(color) * factor);
        return Color.rgb(r, g, b);
    }
    
    private void setupScrollBehavior() {
        appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            float totalScrollRange = appBarLayout.getTotalScrollRange();
            float offset = Math.abs(verticalOffset);
            float percentage = offset / totalScrollRange;
            
            // Scale and fade album artwork
            float scale = 1.0f - (percentage * 0.3f); // Scale from 1.0 to 0.7
            float alpha = 1.0f - (percentage * 1.5f); // Fade faster
            
            albumArtwork.setScaleX(Math.max(scale, 0.7f));
            albumArtwork.setScaleY(Math.max(scale, 0.7f));
            albumArtwork.setAlpha(Math.max(alpha, 0f));
            
            // Fade in toolbar title when collapsed
            toolbarTitle.setAlpha(Math.min(percentage * 2, 1.0f));
            
            // Fade playlist title
            playlistTitle.setAlpha(1.0f - (percentage * 1.5f));
            playlistMetadata.setAlpha(1.0f - (percentage * 1.5f));
            
            lastOffset = percentage;
        });
    }
    
    private void setupRecyclerView() {
        songsAdapter = new SongsAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(songsAdapter);
    }
    
    private void setupSearchAndSort() {
        // Search functionality
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSongs(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Sort button
        btnSort.setOnClickListener(v -> {
            // TODO: Show sort options dialog
            Toast.makeText(this, "Sort options", Toast.LENGTH_SHORT).show();
        });
        
        // Action buttons
        btnAdd.setOnClickListener(v -> {
            android.util.Log.d("Vibe", "Add to library clicked");
            Toast.makeText(this, "Added to library", Toast.LENGTH_SHORT).show();
        });
        
        btnDownload.setOnClickListener(v -> {
            android.util.Log.d("Vibe", "Download clicked");
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
        });
        
        btnMore.setOnClickListener(v -> {
            android.util.Log.d("Vibe", "More options clicked");
            Toast.makeText(this, "More options", Toast.LENGTH_SHORT).show();
        });
        
        btnShuffleIcon.setOnClickListener(v -> {
            if (!allSongs.isEmpty()) {
                android.util.Log.d("Vibe", "Shuffle clicked, songs: " + allSongs.size());
                Toast.makeText(this, "Shuffling playlist", Toast.LENGTH_SHORT).show();
                // TODO: Start playback with shuffle
            } else {
                android.util.Log.w("Vibe", "Shuffle clicked but no songs available");
            }
        });
        
        btnPlay.setOnClickListener(v -> {
            if (!allSongs.isEmpty()) {
                android.util.Log.d("Vibe", "Play clicked, songs: " + allSongs.size());
                Toast.makeText(this, "Playing playlist", Toast.LENGTH_SHORT).show();
                // TODO: Start playback
            } else {
                android.util.Log.w("Vibe", "Play clicked but no songs available");
            }
        });
    }
    
    private void filterSongs(String query) {
        android.util.Log.d("Vibe", "Filtering songs with query: " + query);
        filteredSongs.clear();
        if (query.isEmpty()) {
            filteredSongs.addAll(allSongs);
        } else {
            String lowerQuery = query.toLowerCase();
            for (SongItem song : allSongs) {
                if (song.getTitle().toLowerCase().contains(lowerQuery) ||
                    song.getArtist().toLowerCase().contains(lowerQuery)) {
                    filteredSongs.add(song);
                }
            }
        }
        android.util.Log.d("Vibe", "Filtered results: " + filteredSongs.size() + " out of " + allSongs.size());
        songsAdapter.setSongs(filteredSongs);
    }
    
    private void loadPlaylistData() {
        if (browseId == null || browseId.isEmpty()) {
            android.util.Log.e("Vibe", "No playlist ID provided");
            Toast.makeText(this, "No playlist ID provided", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.util.Log.d("Vibe", "Loading data for browseId: " + browseId);
        
        executor.execute(() -> {
            try {
                // Determine if it's a playlist or album/artist based on ID prefix
                if (browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RDAMPL") || browseId.startsWith("OLAK") || browseId.startsWith("RDCLAK")) {
                    // It's a playlist/album - use getPlaylistSync
                    android.util.Log.d("Vibe", "Loading as playlist (prefix matched)");
                    PlaylistResult playlist = InnertubeBridge.getPlaylistSync(browseId);
                    runOnUiThread(() -> parsePlaylistResult(playlist));
                } else if (browseId.startsWith("MPREb_")) {
                    // It's an album - use getAlbumSync
                    android.util.Log.d("Vibe", "Loading as album");
                    AlbumPageResult album = InnertubeBridge.getAlbumSync(browseId);
                    runOnUiThread(() -> parseAlbumResult(album));
                } else {
                    // Try browse API as fallback
                    android.util.Log.d("Vibe", "Loading as browse (fallback) for ID: " + browseId);
                    loadViaBrowseApi();
                }
            } catch (Exception e) {
                android.util.Log.e("Vibe", "Error loading data", e);
                runOnUiThread(() -> {
                    Toast.makeText(Vibe.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void loadViaBrowseApi() {
        android.util.Log.d("Vibe", "Calling browseWithParamsAsync for: " + browseId);
        InnertubeBridge.browseWithParamsAsync(browseId, null, new HomePageCallback() {
            @Override
            public void onSuccess(HomePageResult result) {
                android.util.Log.d("Vibe", "Browse API success, sections: " + (result.getSections() != null ? result.getSections().size() : 0));
                runOnUiThread(() -> {
                    parsePlaylistData(result);
                });
            }

            @Override
            public void onError(BridgeException error) {
                android.util.Log.e("Vibe", "Browse API error: " + error.getMessage(), error);
                runOnUiThread(() -> {
                    Toast.makeText(Vibe.this, "Error loading playlist: " + error.getMessage(), 
                                 Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void parsePlaylistResult(PlaylistResult playlist) {
        allSongs.clear();
        
        android.util.Log.d("Vibe", "Playlist: " + playlist.getTitle() + ", songs: " + playlist.getSongs().size());
        android.util.Log.d("Vibe", "Song count: " + playlist.getSongCount() + ", duration: " + playlist.getDuration());
        
        // Update UI with playlist info
        playlistTitle.setText(playlist.getTitle());
        toolbarTitle.setText(playlist.getTitle());
        
        String metadata = playlist.getSongCount() + " songs";
        if (playlist.getDuration() != null) {
            metadata += " • " + playlist.getDuration();
        }
        playlistMetadata.setText(metadata);
        
        // Parse songs
        for (YTItemResult item : playlist.getSongs()) {
            String artist = "Unknown Artist";
            if (item.getArtists() != null && !item.getArtists().isEmpty()) {
                artist = item.getArtists().get(0).getName();
            }
            
            String thumbnail = item.getThumbnail() != null ? item.getThumbnail() : "";
            String duration = item.getDuration() != null ? formatDuration(item.getDuration()) : "";
            
            SongItem song = new SongItem(
                item.getId(),
                item.getTitle(),
                artist,
                thumbnail,
                duration
            );
            allSongs.add(song);
        }
        
        filteredSongs.clear();
        filteredSongs.addAll(allSongs);
        songsAdapter.setSongs(filteredSongs);
    }
    
    private void parseAlbumResult(AlbumPageResult album) {
        allSongs.clear();
        
        android.util.Log.d("Vibe", "Album: " + album.getAlbum().getTitle() + ", songs: " + album.getSongs().size());
        
        // Update UI with album info
        playlistTitle.setText(album.getAlbum().getTitle());
        toolbarTitle.setText(album.getAlbum().getTitle());
        
        String metadata = album.getSongs().size() + " songs";
        if (album.getYear() != null) {
            metadata += " • " + album.getYear();
        }
        playlistMetadata.setText(metadata);
        
        // Parse songs
        for (YTItemResult item : album.getSongs()) {
            String artist = "Unknown Artist";
            if (item.getArtists() != null && !item.getArtists().isEmpty()) {
                artist = item.getArtists().get(0).getName();
            }
            
            String thumbnail = item.getThumbnail() != null ? item.getThumbnail() : "";
            String duration = item.getDuration() != null ? formatDuration(item.getDuration()) : "";
            
            SongItem song = new SongItem(
                item.getId(),
                item.getTitle(),
                artist,
                thumbnail,
                duration
            );
            allSongs.add(song);
        }
        
        filteredSongs.clear();
        filteredSongs.addAll(allSongs);
        songsAdapter.setSongs(filteredSongs);
    }
    
    private void parsePlaylistData(HomePageResult result) {
        allSongs.clear();
        
        android.util.Log.d("Vibe", "parsePlaylistData called, sections: " + (result.getSections() != null ? result.getSections().size() : 0));
        
        if (result != null && result.getSections() != null) {
            for (HomeSectionResult section : result.getSections()) {
                android.util.Log.d("Vibe", "Section: " + section.getTitle() + ", items: " + (section.getItems() != null ? section.getItems().size() : 0));
                if (section.getItems() != null) {
                    for (YTItemResult item : section.getItems()) {
                        // Only add songs/videos
                        if ("song".equalsIgnoreCase(item.getType()) || 
                            "video".equalsIgnoreCase(item.getType())) {
                            
                            String artist = "Unknown Artist";
                            if (item.getArtists() != null && !item.getArtists().isEmpty()) {
                                artist = item.getArtists().get(0).getName();
                            }
                            
                            String thumbnail = item.getThumbnail() != null ? 
                                             item.getThumbnail() : "";
                            
                            String duration = item.getDuration() != null ? 
                                            formatDuration(item.getDuration()) : "";
                            
                            SongItem song = new SongItem(
                                item.getId(),
                                item.getTitle(),
                                artist,
                                thumbnail,
                                duration
                            );
                            
                            allSongs.add(song);
                        }
                    }
                }
            }
            
            // Update metadata
            if (!allSongs.isEmpty()) {
                String metadata = allSongs.size() + " songs";
                playlistMetadata.setText(metadata);
            }
        }
        
        filteredSongs.clear();
        filteredSongs.addAll(allSongs);
        songsAdapter.setSongs(filteredSongs);
    }

    @Override
    public void onSongClick(SongItem song, int position) {
        android.util.Log.d("Vibe", "Song clicked: " + song.getTitle() + " at position " + position);
        Toast.makeText(this, "Playing: " + song.getTitle(), Toast.LENGTH_SHORT).show();
        // TODO: Start playback
    }

    @Override
    public void onSongMoreClick(SongItem song, int position) {
        android.util.Log.d("Vibe", "Song more clicked: " + song.getTitle() + " at position " + position);
        Toast.makeText(this, "More options for: " + song.getTitle(), Toast.LENGTH_SHORT).show();
        // TODO: Show bottom sheet with options
    }
    
    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}