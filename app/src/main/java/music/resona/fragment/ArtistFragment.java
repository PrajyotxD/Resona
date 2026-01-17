package music.resona.fragment;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import music.resona.R;
import music.resona.activity.Vibe;
import music.resona.adapters.ArtistContentAdapter;
import music.resona.databinding.FragmentArtistBinding;
import music.resona.online.bridge.models.YTItemResult;
import music.resona.utils.UiUXUtil;
import music.resona.viewmodel.ArtistViewModel;

import java.util.List;

/**
 * Fragment displaying artist information including songs, albums, singles, and videos.
 * Features a Spotify-inspired design with artist header and content sections.
 */
public class ArtistFragment extends Fragment {
    
    private static final String TAG = "ArtistFragment";
    private static final String ARG_ARTIST_ID = "artist_id";
    private static final String ARG_ARTIST_NAME = "artist_name";
    
    private FragmentArtistBinding binding;
    private ArtistViewModel viewModel;
    
    // Adapters for different content sections
    private ArtistContentAdapter songsAdapter;
    private ArtistContentAdapter albumsAdapter;
    private ArtistContentAdapter singlesAdapter;
    private ArtistContentAdapter videosAdapter;
    
    private String artistId;
    private String artistName;
    
    /**
     * Creates a new instance of ArtistFragment.
     *
     * @param artistId   The artist's browse ID
     * @param artistName The artist's name (for initial display)
     * @return A new ArtistFragment instance
     */
    public static ArtistFragment newInstance(@NonNull String artistId, @NonNull String artistName) {
        ArtistFragment fragment = new ArtistFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ARTIST_ID, artistId);
        args.putString(ARG_ARTIST_NAME, artistName);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            artistId = getArguments().getString(ARG_ARTIST_ID);
            artistName = getArguments().getString(ARG_ARTIST_NAME);
        }
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentArtistBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupViewModel();
        setupUI();
        setupAdapters();
        setupObservers();
        
        // Load artist data
        if (artistId != null) {
            viewModel.loadArtist(artistId);
        } else {
            Log.e(TAG, "Artist ID is null");
            showError("Artist ID is missing");
        }
    }
    
    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(ArtistViewModel.class);
    }
    
    private void setupUI() {
        // Set initial artist name if available
        if (artistName != null) {
            binding.artistName.setText(artistName);
        }
        
        // Apply custom fonts using UiUXUtil
        // Header/Title uses akatski.ttf
        UiUXUtil.typeface(requireContext(), binding.artistName, "akatski.ttf", android.graphics.Typeface.NORMAL);
        
        // Other info uses medium.ttf
        UiUXUtil.typeface(requireContext(), binding.descriptionText, "medium.ttf", android.graphics.Typeface.NORMAL);
        
        // Back button
        binding.backButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });
        
        // Retry button
        binding.retryButton.setOnClickListener(v -> viewModel.retry());
        
        // Shuffle button
        binding.shuffleButton.setOnClickListener(v -> {
            String endpoint = viewModel.getShuffleEndpoint();
            if (endpoint != null) {
                // TODO: Play shuffle playlist
                Log.d(TAG, "Shuffle endpoint: " + endpoint);
            }
        });
        
        // Radio button
        binding.radioButton.setOnClickListener(v -> {
            String endpoint = viewModel.getRadioEndpoint();
            if (endpoint != null) {
                // TODO: Play radio
                Log.d(TAG, "Radio endpoint: " + endpoint);
            }
        });
        
        // More button
        binding.moreButton.setOnClickListener(v -> {
            // TODO: Show more options menu
            Log.d(TAG, "More options clicked");
        });
    }
    
    private void setupAdapters() {
        // Songs adapter
        songsAdapter = new ArtistContentAdapter("song");
        songsAdapter.setOnItemClickListener(new ArtistContentAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(YTItemResult item) {
                // TODO: Play song
                Log.d(TAG, "Song clicked: " + item.getTitle());
            }
            
            @Override
            public void onMoreClick(YTItemResult item) {
                // TODO: Show song options
                Log.d(TAG, "Song more clicked: " + item.getTitle());
            }
        });
        
        binding.songsRecycler.setLayoutManager(
            new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        binding.songsRecycler.setAdapter(songsAdapter);
        
        // Albums adapter
        albumsAdapter = new ArtistContentAdapter("album");
        albumsAdapter.setOnItemClickListener(new ArtistContentAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(YTItemResult item) {
                openAlbum(item);
            }
            
            @Override
            public void onMoreClick(YTItemResult item) {
                // TODO: Show album options
                Log.d(TAG, "Album more clicked: " + item.getTitle());
            }
        });
        
        binding.albumsRecycler.setLayoutManager(
            new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        binding.albumsRecycler.setAdapter(albumsAdapter);
        
        // Singles adapter
        singlesAdapter = new ArtistContentAdapter("single");
        singlesAdapter.setOnItemClickListener(new ArtistContentAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(YTItemResult item) {
                openAlbum(item);
            }
            
            @Override
            public void onMoreClick(YTItemResult item) {
                // TODO: Show single options
                Log.d(TAG, "Single more clicked: " + item.getTitle());
            }
        });
        
        binding.singlesRecycler.setLayoutManager(
            new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        binding.singlesRecycler.setAdapter(singlesAdapter);
        
        // Videos adapter
        videosAdapter = new ArtistContentAdapter("video");
        videosAdapter.setOnItemClickListener(new ArtistContentAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(YTItemResult item) {
                // TODO: Play video
                Log.d(TAG, "Video clicked: " + item.getTitle());
            }
            
            @Override
            public void onMoreClick(YTItemResult item) {
                // TODO: Show video options
                Log.d(TAG, "Video more clicked: " + item.getTitle());
            }
        });
        
        binding.videosRecycler.setLayoutManager(
            new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        binding.videosRecycler.setAdapter(videosAdapter);
    }
    
    private void setupObservers() {
        // Observe loading state
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            Log.d(TAG, "Loading state changed: " + isLoading);
            binding.loadingLayout.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.contentLayout.setVisibility(isLoading ? View.GONE : View.VISIBLE);
            binding.errorLayout.setVisibility(View.GONE);
        });
        
        // Observe error message
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                showError(errorMessage);
            }
        });
        
        // Observe artist data
        viewModel.getArtist().observe(getViewLifecycleOwner(), artist -> {
            if (artist != null) {
                Log.d(TAG, "Artist data received: " + artist.getTitle());
                binding.artistName.setText(artist.getTitle());
            } else {
                Log.d(TAG, "Artist data is null");
            }
        });
        
        // Observe thumbnails
        viewModel.getThumbnails().observe(getViewLifecycleOwner(), thumbnails -> {
            if (thumbnails != null && !thumbnails.isEmpty()) {
                Log.d(TAG, "Thumbnails received: " + thumbnails.size());
                String thumbnailUrl = thumbnails.get(0);
                Glide.with(this)
                    .load(thumbnailUrl)
                    .into(binding.artistImage);
            } else {
                Log.d(TAG, "No thumbnails available");
            }
        });
        
        // Observe description
        viewModel.getDescription().observe(getViewLifecycleOwner(), description -> {
            if (description != null && !description.isEmpty()) {
                // Check if description is long (more than 5-10 lines worth of text)
                int maxChars = 300; // Approximately 5-10 lines
                
                if (description.length() > maxChars) {
                    // Show truncated version with "Read More"
                    String truncated = description.substring(0, maxChars) + "...";
                    binding.descriptionText.setText(truncated);
                    binding.descriptionText.setMaxLines(10);
                    
                    // Add click listener to show full description
                    binding.descriptionText.setOnClickListener(v -> {
                        if (binding.descriptionText.getMaxLines() == 10) {
                            // Expand
                            binding.descriptionText.setText(description);
                            binding.descriptionText.setMaxLines(Integer.MAX_VALUE);
                        } else {
                            // Collapse
                            binding.descriptionText.setText(truncated);
                            binding.descriptionText.setMaxLines(10);
                        }
                    });
                } else {
                    // Show full description
                    binding.descriptionText.setText(description);
                    binding.descriptionText.setMaxLines(Integer.MAX_VALUE);
                }
                binding.descriptionText.setVisibility(View.VISIBLE);
            } else {
                Log.d(TAG, "No description available");
                binding.descriptionText.setVisibility(View.GONE);
            }
        });
        
        // Observe songs
        viewModel.getSongs().observe(getViewLifecycleOwner(), songs -> {
            Log.d(TAG, "Songs received: " + (songs != null ? songs.size() : "null"));
            updateSection(binding.songsSection, songs, songsAdapter);
        });
        
        // Observe albums
        viewModel.getAlbums().observe(getViewLifecycleOwner(), albums -> {
            Log.d(TAG, "Albums received: " + (albums != null ? albums.size() : "null"));
            updateSection(binding.albumsSection, albums, albumsAdapter);
        });
        
        // Observe singles
        viewModel.getSingles().observe(getViewLifecycleOwner(), singles -> {
            Log.d(TAG, "Singles received: " + (singles != null ? singles.size() : "null"));
            updateSection(binding.singlesSection, singles, singlesAdapter);
        });
        
        // Observe videos
        viewModel.getVideos().observe(getViewLifecycleOwner(), videos -> {
            Log.d(TAG, "Videos received: " + (videos != null ? videos.size() : "null"));
            updateSection(binding.videosSection, videos, videosAdapter);
        });
    }
    
    private void updateSection(@NonNull View sectionLayout, @Nullable List<YTItemResult> items,
                               @NonNull ArtistContentAdapter adapter) {
        if (items != null && !items.isEmpty()) {
            Log.d(TAG, "Showing section with " + items.size() + " items");
            sectionLayout.setVisibility(View.VISIBLE);
            adapter.submitList(items);
        } else {
            Log.d(TAG, "Hiding section - items: " + (items == null ? "null" : "empty"));
            sectionLayout.setVisibility(View.GONE);
        }
    }
    
    private void showError(@NonNull String message) {
        binding.loadingLayout.setVisibility(View.GONE);
        binding.contentLayout.setVisibility(View.GONE);
        binding.errorLayout.setVisibility(View.VISIBLE);
        binding.errorMessage.setText(message);
    }
    
    private void openAlbum(@NonNull YTItemResult album) {
        Intent intent = new Intent(requireContext(), Vibe.class);
        intent.putExtra("playlistId", album.getPlaylistId());
        intent.putExtra("browseId", album.getBrowseId());
        intent.putExtra("albumName", album.getTitle());
        startActivity(intent);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
