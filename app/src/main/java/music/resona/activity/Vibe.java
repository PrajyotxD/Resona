package music.resona.activity;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.core.content.FileProvider;

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

    private ImageView albumArtwork;
    private TextView playlistTitle;
    private TextView playlistMetadata;
    private TextView creatorName;
    private ImageView creatorAvatar;
    private View gradientBackground;
    private ImageView btnBack;
    private ImageView btnSearch;
    private RelativeLayout topBar;
    private RelativeLayout searchBar;
    private EditText searchInput;
    private ImageView btnCloseSearch;
    private ImageView btnClearSearch;
    private androidx.core.widget.NestedScrollView scrollView;
    private RecyclerView recyclerView;
    private SongsAdapter songsAdapter;
    private ImageView btnDownload;
    private ImageView btnEdit;
    private ImageView btnMore;
    private ImageView btnShare;
    private ImageView btnPlay;
    
    private int currentGradientColor = Color.parseColor("#1DB954");
    private float lastOffset = 0f;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    
    // Intent extras
    private String browseId;
    private String title;
    private String subtitle;
    private String thumbnailUrl;
    private String transitionName;
    
    // Song data
    private List<SongItem> allSongs = new ArrayList<>();
    private List<SongItem> filteredSongs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable shared element transitions with custom animations
        getWindow().requestFeature(android.view.Window.FEATURE_CONTENT_TRANSITIONS);
        getWindow().setSharedElementEnterTransition(
            android.transition.TransitionInflater.from(this)
                .inflateTransition(R.transition.shared_image_transition)
        );
        getWindow().setSharedElementExitTransition(
            android.transition.TransitionInflater.from(this)
                .inflateTransition(R.transition.shared_image_transition)
        );
        getWindow().setEnterTransition(
            android.transition.TransitionInflater.from(this)
                .inflateTransition(R.transition.fade_in)
        );
        getWindow().setExitTransition(
            android.transition.TransitionInflater.from(this)
                .inflateTransition(R.transition.fade_out)
        );
        
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
        transitionName = getIntent().getStringExtra("transitionName");
    }
    
    private void initViews() {
        scrollView = findViewById(R.id.scroll_view);
        btnBack = findViewById(R.id.btn_back);
        btnSearch = findViewById(R.id.btn_search);
        topBar = findViewById(R.id.top_bar);
        searchBar = findViewById(R.id.search_bar);
        searchInput = findViewById(R.id.search_input);
        btnCloseSearch = findViewById(R.id.btn_close_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        albumArtwork = findViewById(R.id.album_artwork);
        playlistTitle = findViewById(R.id.playlist_title);
        playlistMetadata = findViewById(R.id.playlist_metadata);
        creatorName = findViewById(R.id.creator_name);
        creatorAvatar = findViewById(R.id.creator_avatar);
        gradientBackground = findViewById(R.id.gradient_background);
        recyclerView = findViewById(R.id.rv_songs);
        btnDownload = findViewById(R.id.btn_download);
        btnEdit = findViewById(R.id.btn_edit);
        btnMore = findViewById(R.id.btn_more);
        btnShare = findViewById(R.id.btn_share);
        btnPlay = findViewById(R.id.btn_play);
        
        // Set transition name for shared element animation
        if (transitionName != null) {
            albumArtwork.setTransitionName(transitionName);
        }
        
        btnBack.setOnClickListener(v -> onBackPressed());
        btnSearch.setOnClickListener(v -> showSearchBar());
        btnCloseSearch.setOnClickListener(v -> hideSearchBar());
        btnClearSearch.setOnClickListener(v -> {
            searchInput.setText("");
            btnClearSearch.setVisibility(View.GONE);
        });
        
        // Search text change listener
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSongs(s.toString());
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Apply typeface to search input
        music.resona.utils.UiUXUtil.typeface(this, searchInput, "medium.ttf", android.graphics.Typeface.NORMAL);
        
        // Set initial data
        if (title != null) {
            playlistTitle.setText(title);
        }
        if (subtitle != null) {
            creatorName.setText(subtitle);
            playlistMetadata.setText("Loading...");
        }
        
        // Apply typefaces
        music.resona.utils.UiUXUtil.typeface(this, playlistTitle, "akatski.ttf", android.graphics.Typeface.NORMAL);
        music.resona.utils.UiUXUtil.typeface(this, creatorName, "medium.ttf", android.graphics.Typeface.NORMAL);
        music.resona.utils.UiUXUtil.typeface(this, playlistMetadata, "copy.ttf", android.graphics.Typeface.NORMAL);
    }
    
    private void setupWindowInsets() {
        // No toolbar padding needed since icons scroll with content
    }
    
    private void setupAlbumArtwork() {
        // Apply corner radius
        music.resona.utils.UiUXUtil.imageradius(albumArtwork, 16);
        music.resona.utils.UiUXUtil.imageradius(creatorAvatar, 14);
        
        // Load artwork from URL if available
        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            Glide.with(this)
                    .asBitmap()
                    .load(thumbnailUrl)
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                            albumArtwork.setImageBitmap(resource);
                            creatorAvatar.setImageBitmap(resource);
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
    
    private void setupScrollBehavior() {
        scrollView.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            // Calculate scroll percentage (0 to 1)
            float maxScroll = 400f; // Approximate header height
            float percentage = Math.min(scrollY / maxScroll, 1.0f);
            
            // Scale and fade album artwork
            float scale = 1.0f - (percentage * 0.3f);
            float alpha = 1.0f - (percentage * 1.5f);
            
            albumArtwork.setScaleX(Math.max(scale, 0.7f));
            albumArtwork.setScaleY(Math.max(scale, 0.7f));
            albumArtwork.setAlpha(Math.max(alpha, 0f));
            

            // Fade playlist info
            playlistTitle.setAlpha(1.0f - (percentage * 1.5f));
            playlistMetadata.setAlpha(1.0f - (percentage * 1.5f));
            creatorName.setAlpha(1.0f - (percentage * 1.5f));
            creatorAvatar.setAlpha(1.0f - (percentage * 1.5f));
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
    
    private void setupRecyclerView() {
        songsAdapter = new SongsAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(songsAdapter);
    }
    
    private void setupSearchAndSort() {
        // Action buttons
        btnDownload.setOnClickListener(v -> {
            android.util.Log.d("Vibe", "Download clicked");
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
        });
        
        btnEdit.setOnClickListener(v -> {
            android.util.Log.d("Vibe", "Edit clicked");
            Toast.makeText(this, "Edit playlist", Toast.LENGTH_SHORT).show();
        });
        
        btnMore.setOnClickListener(v -> {
            android.util.Log.d("Vibe", "More options clicked");
            showPlaylistOptionsBottomSheet();
        });
        
        btnShare.setOnClickListener(v -> {
            android.util.Log.d("Vibe", "Share clicked");
            showShareBottomSheet();
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
    
    private void showSearchBar() {
        topBar.setVisibility(View.GONE);
        searchBar.setVisibility(View.VISIBLE);
        searchInput.requestFocus();
        
        // Show keyboard
        android.view.inputmethod.InputMethodManager imm = 
            (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }
    
    private void hideSearchBar() {
        searchBar.setVisibility(View.GONE);
        topBar.setVisibility(View.VISIBLE);
        searchInput.setText("");
        btnClearSearch.setVisibility(View.GONE);
        
        // Hide keyboard
        android.view.inputmethod.InputMethodManager imm = 
            (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
        
        // Reset to show all songs
        filterSongs("");
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
                if (browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RDAMPL") || 
                    browseId.startsWith("OLAK") || browseId.startsWith("RDCLAK") || browseId.equals("LM") || 
                    browseId.equals("SE")) {
                    // It's a playlist/album - use getPlaylistSync (includes Liked Music "LM" and other special playlists)
                    android.util.Log.d("Vibe", "Loading as playlist (prefix matched)");
                    PlaylistResult playlist = InnertubeBridge.getPlaylistSync(browseId);
                    runOnUiThread(() -> parsePlaylistResult(playlist));
                } else if (browseId.startsWith("MPREb_")) {
                    // It's an album - use getAlbumSync
                    android.util.Log.d("Vibe", "Loading as album");
                    AlbumPageResult album = InnertubeBridge.getAlbumSync(browseId);
                    runOnUiThread(() -> parseAlbumResult(album));
                } else {
                    // For unknown IDs, try playlist API first (works for most saved/liked playlists)
                    android.util.Log.d("Vibe", "Trying playlist API first for unknown ID: " + browseId);
                    try {
                        PlaylistResult playlist = InnertubeBridge.getPlaylistSync(browseId);
                        runOnUiThread(() -> parsePlaylistResult(playlist));
                    } catch (Exception playlistError) {
                        android.util.Log.w("Vibe", "Playlist API failed, trying browse API: " + playlistError.getMessage());
                        loadViaBrowseApi();
                    }
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
        
        // Update metadata with actual song count
        int songCount = allSongs.size();
        String metadata = songCount + (songCount == 1 ? " song" : " songs");
        if (playlist.getDuration() != null) {
            metadata += " • " + playlist.getDuration();
        }
        playlistMetadata.setText(metadata);
        
        // Update song count dynamically
        songCount = filteredSongs.size();
        playlistMetadata.setText(songCount + (songCount == 1 ? " song" : " songs"));
    }
    
    private void parseAlbumResult(AlbumPageResult album) {
        allSongs.clear();
        
        android.util.Log.d("Vibe", "Album: " + album.getAlbum().getTitle() + ", songs: " + album.getSongs().size());
        
        // Update UI with album info
        runOnUiThread(() -> {
            playlistTitle.setText(album.getAlbum().getTitle());
        });
        
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
        
        // Update metadata with song count
        runOnUiThread(() -> {
            playlistMetadata.setText(allSongs.size() + (allSongs.size() == 1 ? " song" : " songs"));
        });
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
        }
        
        filteredSongs.clear();
        filteredSongs.addAll(allSongs);
        songsAdapter.setSongs(filteredSongs);
        
        // Update metadata with song count
        runOnUiThread(() -> {
            if (!allSongs.isEmpty()) {
                playlistMetadata.setText(allSongs.size() + (allSongs.size() == 1 ? " song" : " songs"));
            }
        });
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
    
    private void showPlaylistOptionsBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_playlist_options, null);
        bottomSheet.setContentView(bottomSheetView);
        
        // Set title and subtitle
        TextView sheetTitle = bottomSheetView.findViewById(R.id.bottom_sheet_title);
        TextView sheetSubtitle = bottomSheetView.findViewById(R.id.bottom_sheet_subtitle);
        
        if (playlistTitle != null && playlistTitle.getText() != null) {
            sheetTitle.setText(playlistTitle.getText());
        }
        
        int songCount = allSongs.size();
        sheetSubtitle.setText("YouTube Music • " + songCount + (songCount == 1 ? " song" : " songs"));
        
        // Apply typefaces
        music.resona.utils.UiUXUtil.typeface(this, sheetTitle, "akatski.ttf", android.graphics.Typeface.NORMAL);
        music.resona.utils.UiUXUtil.typeface(this, sheetSubtitle, "medium.ttf", android.graphics.Typeface.NORMAL);
        
        // Close button
        ImageView btnClose = bottomSheetView.findViewById(R.id.btn_close);
        btnClose.setOnClickListener(v -> bottomSheet.dismiss());
        
        // Get all option TextViews and apply typeface
        TextView txtShufflePlay = ((LinearLayout) bottomSheetView.findViewById(R.id.option_shuffle_play)).getChildAt(1) instanceof TextView ? 
            (TextView) ((LinearLayout) bottomSheetView.findViewById(R.id.option_shuffle_play)).getChildAt(1) : null;
        TextView txtFindInPlaylist = ((LinearLayout) bottomSheetView.findViewById(R.id.option_find_in_playlist)).getChildAt(1) instanceof TextView ? 
            (TextView) ((LinearLayout) bottomSheetView.findViewById(R.id.option_find_in_playlist)).getChildAt(1) : null;
        TextView txtStartMix = ((LinearLayout) bottomSheetView.findViewById(R.id.option_start_mix)).getChildAt(1) instanceof TextView ? 
            (TextView) ((LinearLayout) bottomSheetView.findViewById(R.id.option_start_mix)).getChildAt(1) : null;
        TextView txtPlayNext = ((LinearLayout) bottomSheetView.findViewById(R.id.option_play_next)).getChildAt(1) instanceof TextView ? 
            (TextView) ((LinearLayout) bottomSheetView.findViewById(R.id.option_play_next)).getChildAt(1) : null;
        TextView txtAddToQueue = ((LinearLayout) bottomSheetView.findViewById(R.id.option_add_to_queue)).getChildAt(1) instanceof TextView ? 
            (TextView) ((LinearLayout) bottomSheetView.findViewById(R.id.option_add_to_queue)).getChildAt(1) : null;
        TextView txtSaveToPlaylist = ((LinearLayout) bottomSheetView.findViewById(R.id.option_save_to_playlist)).getChildAt(1) instanceof TextView ? 
            (TextView) ((LinearLayout) bottomSheetView.findViewById(R.id.option_save_to_playlist)).getChildAt(1) : null;
        TextView txtPinToSpeedDial = ((LinearLayout) bottomSheetView.findViewById(R.id.option_pin_to_speed_dial)).getChildAt(1) instanceof TextView ? 
            (TextView) ((LinearLayout) bottomSheetView.findViewById(R.id.option_pin_to_speed_dial)).getChildAt(1) : null;
        
        if (txtShufflePlay != null) music.resona.utils.UiUXUtil.typeface(this, txtShufflePlay, "medium.ttf", android.graphics.Typeface.NORMAL);
        if (txtFindInPlaylist != null) music.resona.utils.UiUXUtil.typeface(this, txtFindInPlaylist, "medium.ttf", android.graphics.Typeface.NORMAL);
        if (txtStartMix != null) music.resona.utils.UiUXUtil.typeface(this, txtStartMix, "medium.ttf", android.graphics.Typeface.NORMAL);
        if (txtPlayNext != null) music.resona.utils.UiUXUtil.typeface(this, txtPlayNext, "medium.ttf", android.graphics.Typeface.NORMAL);
        if (txtAddToQueue != null) music.resona.utils.UiUXUtil.typeface(this, txtAddToQueue, "medium.ttf", android.graphics.Typeface.NORMAL);
        if (txtSaveToPlaylist != null) music.resona.utils.UiUXUtil.typeface(this, txtSaveToPlaylist, "medium.ttf", android.graphics.Typeface.NORMAL);
        if (txtPinToSpeedDial != null) music.resona.utils.UiUXUtil.typeface(this, txtPinToSpeedDial, "medium.ttf", android.graphics.Typeface.NORMAL);
        
        // Option click listeners
        LinearLayout optionShufflePlay = bottomSheetView.findViewById(R.id.option_shuffle_play);
        optionShufflePlay.setOnClickListener(v -> {
            Toast.makeText(this, "Shuffle play", Toast.LENGTH_SHORT).show();
            bottomSheet.dismiss();
        });
        
        LinearLayout optionFindInPlaylist = bottomSheetView.findViewById(R.id.option_find_in_playlist);
        optionFindInPlaylist.setOnClickListener(v -> {
            Toast.makeText(this, "Find in playlist", Toast.LENGTH_SHORT).show();
            bottomSheet.dismiss();
        });
        
        LinearLayout optionStartMix = bottomSheetView.findViewById(R.id.option_start_mix);
        optionStartMix.setOnClickListener(v -> {
            Toast.makeText(this, "Start mix", Toast.LENGTH_SHORT).show();
            bottomSheet.dismiss();
        });
        
        LinearLayout optionPlayNext = bottomSheetView.findViewById(R.id.option_play_next);
        optionPlayNext.setOnClickListener(v -> {
            Toast.makeText(this, "Play next", Toast.LENGTH_SHORT).show();
            bottomSheet.dismiss();
        });
        
        LinearLayout optionAddToQueue = bottomSheetView.findViewById(R.id.option_add_to_queue);
        optionAddToQueue.setOnClickListener(v -> {
            Toast.makeText(this, "Add to queue", Toast.LENGTH_SHORT).show();
            bottomSheet.dismiss();
        });
        
        LinearLayout optionSaveToPlaylist = bottomSheetView.findViewById(R.id.option_save_to_playlist);
        optionSaveToPlaylist.setOnClickListener(v -> {
            Toast.makeText(this, "Save to playlist", Toast.LENGTH_SHORT).show();
            bottomSheet.dismiss();
        });
        
        LinearLayout optionPinToSpeedDial = bottomSheetView.findViewById(R.id.option_pin_to_speed_dial);
        optionPinToSpeedDial.setOnClickListener(v -> {
            Toast.makeText(this, "Pin to Speed dial", Toast.LENGTH_SHORT).show();
            bottomSheet.dismiss();
        });
        
        bottomSheet.show();
    }
    
    private void showShareBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_share, null);
        bottomSheet.setContentView(bottomSheetView);
        
        // Get views
        TextView shareTitle = bottomSheetView.findViewById(R.id.share_title);
        TextView txtBackgroundStyle = bottomSheetView.findViewById(R.id.txt_background_style);
        TextView txtShareTo = bottomSheetView.findViewById(R.id.txt_share_to);
        ImageView shareCardPreview = bottomSheetView.findViewById(R.id.share_card_preview);
        ImageView btnClose = bottomSheetView.findViewById(R.id.btn_close_share);
        TextView txtSnapchat = bottomSheetView.findViewById(R.id.txt_snapchat);
        TextView txtWhatsapp = bottomSheetView.findViewById(R.id.txt_whatsapp);
        TextView txtInstagram = bottomSheetView.findViewById(R.id.txt_instagram);
        TextView txtMore = bottomSheetView.findViewById(R.id.txt_more);
        
        // Gradient containers and views
        FrameLayout gradientContainer1 = bottomSheetView.findViewById(R.id.gradient_container_1);
        FrameLayout gradientContainer2 = bottomSheetView.findViewById(R.id.gradient_container_2);
        FrameLayout gradientContainer3 = bottomSheetView.findViewById(R.id.gradient_container_3);
        FrameLayout gradientContainer4 = bottomSheetView.findViewById(R.id.gradient_container_4);
        FrameLayout gradientContainer5 = bottomSheetView.findViewById(R.id.gradient_container_5);
        
        // Track selected gradient (default is 1)
        final int[] selectedGradient = {R.drawable.share_gradient_1};
        
        // Apply typefaces
        music.resona.utils.UiUXUtil.typeface(this, shareTitle, "akatski.ttf", android.graphics.Typeface.NORMAL);
        music.resona.utils.UiUXUtil.typeface(this, txtBackgroundStyle, "akatski.ttf", android.graphics.Typeface.NORMAL);
        music.resona.utils.UiUXUtil.typeface(this, txtShareTo, "akatski.ttf", android.graphics.Typeface.NORMAL);
        music.resona.utils.UiUXUtil.typeface(this, txtSnapchat, "akatski.ttf", android.graphics.Typeface.NORMAL);
        music.resona.utils.UiUXUtil.typeface(this, txtWhatsapp, "akatski.ttf", android.graphics.Typeface.NORMAL);
        music.resona.utils.UiUXUtil.typeface(this, txtInstagram, "akatski.ttf", android.graphics.Typeface.NORMAL);
        music.resona.utils.UiUXUtil.typeface(this, txtMore, "akatski.ttf", android.graphics.Typeface.NORMAL);
        
        // Generate initial preview
        executor.execute(() -> {
            Bitmap previewBitmap = generateShareCardBitmapWithGradient(selectedGradient[0]);
            runOnUiThread(() -> shareCardPreview.setImageBitmap(previewBitmap));
        });
        
        // Close button
        btnClose.setOnClickListener(v -> bottomSheet.dismiss());
        
        // Gradient selection listeners
        gradientContainer1.setOnClickListener(v -> {
            selectedGradient[0] = R.drawable.share_gradient_1;
            executor.execute(() -> {
                Bitmap previewBitmap = generateShareCardBitmapWithGradient(selectedGradient[0]);
                runOnUiThread(() -> shareCardPreview.setImageBitmap(previewBitmap));
            });
        });
        
        gradientContainer2.setOnClickListener(v -> {
            selectedGradient[0] = R.drawable.share_gradient_2;
            executor.execute(() -> {
                Bitmap previewBitmap = generateShareCardBitmapWithGradient(selectedGradient[0]);
                runOnUiThread(() -> shareCardPreview.setImageBitmap(previewBitmap));
            });
        });
        
        gradientContainer3.setOnClickListener(v -> {
            selectedGradient[0] = R.drawable.share_gradient_3;
            executor.execute(() -> {
                Bitmap previewBitmap = generateShareCardBitmapWithGradient(selectedGradient[0]);
                runOnUiThread(() -> shareCardPreview.setImageBitmap(previewBitmap));
            });
        });
        
        gradientContainer4.setOnClickListener(v -> {
            selectedGradient[0] = R.drawable.share_gradient_4;
            executor.execute(() -> {
                Bitmap previewBitmap = generateShareCardBitmapWithGradient(selectedGradient[0]);
                runOnUiThread(() -> shareCardPreview.setImageBitmap(previewBitmap));
            });
        });
        
        gradientContainer5.setOnClickListener(v -> {
            selectedGradient[0] = R.drawable.share_gradient_5;
            executor.execute(() -> {
                Bitmap previewBitmap = generateShareCardBitmapWithGradient(selectedGradient[0]);
                runOnUiThread(() -> shareCardPreview.setImageBitmap(previewBitmap));
            });
        });
        
        // Social media click listeners
        LinearLayout shareSnapchat = bottomSheetView.findViewById(R.id.share_snapchat);
        shareSnapchat.setOnClickListener(v -> {
            bottomSheet.dismiss();
            generateAndShareCardWithGradient("snapchat", selectedGradient[0]);
        });
        
        LinearLayout shareWhatsapp = bottomSheetView.findViewById(R.id.share_whatsapp);
        shareWhatsapp.setOnClickListener(v -> {
            bottomSheet.dismiss();
            generateAndShareCardWithGradient("whatsapp", selectedGradient[0]);
        });
        
        LinearLayout shareInstagram = bottomSheetView.findViewById(R.id.share_instagram);
        shareInstagram.setOnClickListener(v -> {
            bottomSheet.dismiss();
            generateAndShareCardWithGradient("instagram", selectedGradient[0]);
        });
        
        LinearLayout shareMore = bottomSheetView.findViewById(R.id.share_more);
        shareMore.setOnClickListener(v -> {
            bottomSheet.dismiss();
            generateAndShareCardWithGradient("generic", selectedGradient[0]);
        });
        
        bottomSheet.show();
    }
    
    private void generateAndShareCard(String platform) {
        generateAndShareCardWithGradient(platform, R.drawable.share_gradient_1);
    }
    
    private void generateAndShareCardWithGradient(String platform, int gradientDrawable) {
        // Show loading toast
        Toast.makeText(this, "Generating share card...", Toast.LENGTH_SHORT).show();
        
        executor.execute(() -> {
            try {
                Bitmap shareCardBitmap = generateShareCardBitmapWithGradient(gradientDrawable);
                File imageFile = saveBitmapToCache(shareCardBitmap);
                
                runOnUiThread(() -> {
                    if (imageFile != null) {
                        shareToApp(platform, imageFile);
                    } else {
                        Toast.makeText(this, "Failed to generate share card", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("Vibe", "Error generating share card", e);
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }
    
    private Bitmap generateShareCardBitmapWithGradient(int gradientDrawable) {
        // Convert dp to pixels
        float density = getResources().getDisplayMetrics().density;
        
        // Card dimensions in dp, converted to px
        final int WIDTH = (int) (330 * density);
        final int HEIGHT = (int) (600 * density);
        
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        // Draw gradient background
        int[] gradientColors = getGradientColors(gradientDrawable);
        android.graphics.LinearGradient gradient = new android.graphics.LinearGradient(
            0, 0, WIDTH, HEIGHT,
            gradientColors,
            null,
            android.graphics.Shader.TileMode.CLAMP
        );
        android.graphics.Paint gradientPaint = new android.graphics.Paint();
        gradientPaint.setShader(gradient);
        canvas.drawRect(0, 0, WIDTH, HEIGHT, gradientPaint);
        
        // Draw inner card with rounded corners - 200x300dp with 15dp radius
        int cardWidth = (int) (200 * density);
        int cardHeight = (int) (300 * density);
        int cardRadius = (int) (15 * density);
        
        int cardLeft = (WIDTH - cardWidth) / 2;
        int cardTop = (HEIGHT - cardHeight) / 2;
        int cardRight = cardLeft + cardWidth;
        int cardBottom = cardTop + cardHeight;
        
        android.graphics.Paint cardPaint = new android.graphics.Paint();
        cardPaint.setColor(0xDD000000); // Semi-transparent black
        cardPaint.setAntiAlias(true);
        android.graphics.RectF cardRect = new android.graphics.RectF(cardLeft, cardTop, cardRight, cardBottom);
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardPaint);
        
        // Load and draw album artwork - 150x190dp with 14dp radius
        int artWidth = (int) (150 * density);
        int artHeight = (int) (190 * density);
        int artRadius = (int) (14 * density);
        
        int artX = (WIDTH - artWidth) / 2;
        int artY = cardTop + (int) (20 * density);
        
        try {
            Bitmap albumBitmap = null;
            
            if (albumArtwork.getDrawable() != null && albumArtwork.getDrawable() instanceof BitmapDrawable) {
                albumBitmap = ((BitmapDrawable) albumArtwork.getDrawable()).getBitmap();
            } else if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                albumBitmap = Glide.with(this)
                    .asBitmap()
                    .load(thumbnailUrl)
                    .submit(artWidth, artHeight)
                    .get();
            }
            
            if (albumBitmap != null) {
                // Create rounded bitmap for album art
                Bitmap roundedAlbum = Bitmap.createBitmap(artWidth, artHeight, Bitmap.Config.ARGB_8888);
                Canvas albumCanvas = new Canvas(roundedAlbum);
                
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setAntiAlias(true);
                
                android.graphics.RectF rect = new android.graphics.RectF(0, 0, artWidth, artHeight);
                albumCanvas.drawRoundRect(rect, artRadius, artRadius, paint);
                
                paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
                android.graphics.Rect src = new android.graphics.Rect(0, 0, albumBitmap.getWidth(), albumBitmap.getHeight());
                android.graphics.Rect dst = new android.graphics.Rect(0, 0, artWidth, artHeight);
                albumCanvas.drawBitmap(albumBitmap, src, dst, paint);
                
                canvas.drawBitmap(roundedAlbum, artX, artY, null);
            }
        } catch (Exception e) {
            android.util.Log.e("Vibe", "Error loading album art", e);
        }
        
        // Draw title
        int textY = artY + artHeight + (int) (25 * density);
        
        android.graphics.Paint titlePaint = new android.graphics.Paint();
        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTextSize(20 * density);
        titlePaint.setAntiAlias(true);
        titlePaint.setTextAlign(android.graphics.Paint.Align.CENTER);
        
        // Apply custom typeface
        try {
            android.graphics.Typeface titleTypeface = android.graphics.Typeface.createFromAsset(getAssets(), "akatski.ttf");
            titlePaint.setTypeface(android.graphics.Typeface.create(titleTypeface, android.graphics.Typeface.BOLD));
        } catch (Exception e) {
            titlePaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        }
        
        String titleText = title != null ? title : "Playlist";
        canvas.drawText(titleText, WIDTH / 2, textY, titlePaint);
        
        // Draw artist
        textY += (int) (32 * density);
        
        android.graphics.Paint artistPaint = new android.graphics.Paint();
        artistPaint.setColor(0xFFCCCCCC);
        artistPaint.setTextSize(14 * density);
        artistPaint.setAntiAlias(true);
        artistPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
        
        try {
            android.graphics.Typeface artistTypeface = android.graphics.Typeface.createFromAsset(getAssets(), "medium.ttf");
            artistPaint.setTypeface(artistTypeface);
        } catch (Exception e) {
            artistPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        }
        
        String artistText = subtitle != null ? subtitle : "Various Artists";
        canvas.drawText(artistText, WIDTH / 2, textY, artistPaint);
        
        // Draw metadata
        textY += (int) (24 * density);
        
        android.graphics.Paint metaPaint = new android.graphics.Paint();
        metaPaint.setColor(0xFFAAAAAA);
        metaPaint.setTextSize(12 * density);
        metaPaint.setAntiAlias(true);
        metaPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
        
        try {
            android.graphics.Typeface metaTypeface = android.graphics.Typeface.createFromAsset(getAssets(), "copy.ttf");
            metaPaint.setTypeface(metaTypeface);
        } catch (Exception e) {
            metaPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        }
        
        canvas.drawText("Playlist", WIDTH / 2, textY, metaPaint);
        
        // Draw watermark at top-right
        android.graphics.Paint watermarkPaint = new android.graphics.Paint();
        watermarkPaint.setColor(0x99FFFFFF); // 60% opacity white
        watermarkPaint.setTextSize(28 * density);
        watermarkPaint.setAntiAlias(true);
        watermarkPaint.setTextAlign(android.graphics.Paint.Align.RIGHT);
        
        try {
            android.graphics.Typeface watermarkTypeface = android.graphics.Typeface.createFromAsset(getAssets(), "akatski.ttf");
            watermarkPaint.setTypeface(android.graphics.Typeface.create(watermarkTypeface, android.graphics.Typeface.BOLD));
        } catch (Exception e) {
            watermarkPaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        }
        
        canvas.drawText("Resona", WIDTH - (int) (20 * density), (int) (45 * density), watermarkPaint);
        
        return bitmap;
    }
    
    private int[] getGradientColors(int gradientDrawable) {
        if (gradientDrawable == R.drawable.share_gradient_1) {
            return new int[]{0xFF1a1a2e, 0xFF16213e, 0xFF0f3460};
        } else if (gradientDrawable == R.drawable.share_gradient_2) {
            return new int[]{0xFFee0979, 0xFFff6a00, 0xFFff8c00};
        } else if (gradientDrawable == R.drawable.share_gradient_3) {
            return new int[]{0xFF11998e, 0xFF38ef7d, 0xFF56ab2f};
        } else if (gradientDrawable == R.drawable.share_gradient_4) {
            return new int[]{0xFF8E2DE2, 0xFFc94aa3, 0xFFff6a88};
        } else if (gradientDrawable == R.drawable.share_gradient_5) {
            return new int[]{0xFF2C3E50, 0xFFBDC3C7, 0xFFF39C12};
        }
        return new int[]{0xFF1a1a2e, 0xFF16213e, 0xFF0f3460};
    }
    
    private File saveBitmapToCache(Bitmap bitmap) {
        try {
            // Create share_images directory in cache
            File cacheDir = new File(getCacheDir(), "share_images");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            
            // Create file
            File imageFile = new File(cacheDir, "share_card_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.flush();
            fos.close();
            
            return imageFile;
        } catch (IOException e) {
            android.util.Log.e("Vibe", "Error saving bitmap", e);
            return null;
        }
    }
    
    private void shareToApp(String platform, File imageFile) {
        Uri imageUri = FileProvider.getUriForFile(
            this,
            getPackageName() + ".fileprovider",
            imageFile
        );
        
        String deepLink = "resona://playlist/" + browseId;
        
        switch (platform.toLowerCase()) {
            case "instagram":
                shareToInstagramStories(imageUri, deepLink);
                break;
            case "snapchat":
                shareToSnapchat(imageUri);
                break;
            case "whatsapp":
                shareToWhatsApp(imageUri);
                break;
            default:
                shareGeneric(imageUri);
                break;
        }
    }
    
    private void shareToInstagramStories(Uri imageUri, String deepLink) {
        // Use Instagram's official story sharing intent
        android.content.Intent intent = new android.content.Intent("com.instagram.share.ADD_TO_STORY");
        intent.setDataAndType(imageUri, "image/jpeg");
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        
        // Add sticker asset (the share card image)
        intent.putExtra("interactive_asset_uri", imageUri);
        
        // Add attribution link (deep link to Resona)
        intent.putExtra("content_url", deepLink);
        
        // Add background colors for story
        intent.putExtra("top_background_color", "#1a1a2e");
        intent.putExtra("bottom_background_color", "#0f3460");
        
        // Check if Instagram is installed
        if (isAppInstalled("com.instagram.android")) {
            intent.setPackage("com.instagram.android");
            grantUriPermission("com.instagram.android", imageUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(intent);
                Toast.makeText(this, "Opening Instagram Stories...", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                android.util.Log.e("Vibe", "Error sharing to Instagram Stories", e);
                // Fallback to regular Instagram share
                android.content.Intent fallbackIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                fallbackIntent.setType("image/jpeg");
                fallbackIntent.putExtra(android.content.Intent.EXTRA_STREAM, imageUri);
                fallbackIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                fallbackIntent.setPackage("com.instagram.android");
                try {
                    startActivity(fallbackIntent);
                } catch (Exception e2) {
                    shareGeneric(imageUri);
                }
            }
        } else {
            Toast.makeText(this, "Instagram not installed", Toast.LENGTH_SHORT).show();
            shareGeneric(imageUri);
        }
    }
    
    private void shareToSnapchat(Uri imageUri) {
        // Try Snapchat Creative Kit first (for direct story posting)
        android.content.Intent intent = new android.content.Intent("com.snapchat.intent.action.SNAP_SHARE");
        intent.setType("image/jpeg");
        intent.putExtra(android.content.Intent.EXTRA_STREAM, imageUri);
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        
        if (isAppInstalled("com.snapchat.android")) {
            intent.setPackage("com.snapchat.android");
            grantUriPermission("com.snapchat.android", imageUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(intent);
                Toast.makeText(this, "Opening Snapchat...", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                android.util.Log.e("Vibe", "Error sharing to Snapchat with Creative Kit, trying standard share", e);
                // Fallback to standard share
                android.content.Intent fallbackIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                fallbackIntent.setType("image/jpeg");
                fallbackIntent.putExtra(android.content.Intent.EXTRA_STREAM, imageUri);
                fallbackIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                fallbackIntent.setPackage("com.snapchat.android");
                try {
                    startActivity(fallbackIntent);
                } catch (Exception e2) {
                    shareGeneric(imageUri);
                }
            }
        } else {
            Toast.makeText(this, "Snapchat not installed", Toast.LENGTH_SHORT).show();
            shareGeneric(imageUri);
        }
    }
    
    private void shareToWhatsApp(Uri imageUri) {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("image/jpeg");
        intent.putExtra(android.content.Intent.EXTRA_STREAM, imageUri);
        intent.putExtra(android.content.Intent.EXTRA_TEXT, "Check out this playlist on Resona!");
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        if (isAppInstalled("com.whatsapp")) {
            intent.setPackage("com.whatsapp");
            grantUriPermission("com.whatsapp", imageUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(intent);
            } catch (Exception e) {
                android.util.Log.e("Vibe", "Error sharing to WhatsApp", e);
                shareGeneric(imageUri);
            }
        } else {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
            shareGeneric(imageUri);
        }
    }
    
    private void shareGeneric(Uri imageUri) {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("image/jpeg");
        intent.putExtra(android.content.Intent.EXTRA_STREAM, imageUri);
        intent.putExtra(android.content.Intent.EXTRA_TEXT, "Check out this playlist on Resona!");
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(android.content.Intent.createChooser(intent, "Share via"));
    }
    
    private boolean isAppInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        }
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