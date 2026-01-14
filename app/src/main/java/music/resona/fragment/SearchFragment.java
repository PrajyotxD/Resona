package music.resona.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;

import java.util.List;

import music.resona.R;
import music.resona.activity.Vibe;
import music.resona.databinding.FragmentSearchBinding;
import music.resona.adapters.SearchResultsAdapter;
import music.resona.online.bridge.models.YTItemResult;
import music.resona.viewmodel.SearchViewModel;

/**
 * Search fragment with real-time search, filters, and Spotify-like UI.
 * Uses MVVM architecture with ViewModel and View Binding.
 */
public class SearchFragment extends Fragment implements SearchResultsAdapter.OnItemClickListener {

    private FragmentSearchBinding binding;
    private SearchViewModel viewModel;
    private SearchResultsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViewModel();
        setupRecyclerView();
        setupSearchInput();
        setupFilterChips();
        setupClickListeners();
        observeViewModel();
        applyTypefaces();
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);
    }

    private void setupRecyclerView() {
        adapter = new SearchResultsAdapter();
        adapter.setOnItemClickListener(this);

        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSearchResults.setAdapter(adapter);
    }

    private void setupSearchInput() {
        // Focus search input on fragment start
        binding.searchInput.requestFocus();

        // Real-time search with debouncing
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Show/hide clear button
                binding.btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);

                // Trigger debounced search
                viewModel.searchWithDebounce(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Handle search action on keyboard
        binding.searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                String query = binding.searchInput.getText().toString();
                viewModel.searchImmediately(query);
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void setupFilterChips() {
        binding.chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                int checkedId = checkedIds.get(0);
                String filter;

                if (checkedId == R.id.chip_songs) {
                    filter = "songs";
                } else if (checkedId == R.id.chip_albums) {
                    filter = "albums";
                } else if (checkedId == R.id.chip_artists) {
                    filter = "artists";
                } else if (checkedId == R.id.chip_playlists) {
                    filter = "playlists";
                } else {
                    filter = "songs";
                }

                viewModel.setFilter(filter);
            }
        });
    }

    private void setupClickListeners() {
        binding.btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        binding.btnClear.setOnClickListener(v -> {
            binding.searchInput.setText("");
            viewModel.clearSearch();
        });

        binding.btnRetry.setOnClickListener(v -> {
            String query = binding.searchInput.getText().toString();
            if (!query.isEmpty()) {
                viewModel.searchImmediately(query);
            }
        });
    }

    private void observeViewModel() {
        // Observe search results
        viewModel.getSearchResults().observe(getViewLifecycleOwner(), results -> {
            adapter.submitList(results);
            updateUIState(results);
        });

        // Observe loading state
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (Boolean.TRUE.equals(isLoading)) {
                showLoadingState();
            }
        });

        // Observe errors
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                showErrorState(error);
            }
        });
    }

    private void updateUIState(@NonNull List<YTItemResult> results) {
        String query = binding.searchInput.getText().toString().trim();

        if (query.isEmpty()) {
            // Show empty state for no query
            showEmptyState();
        } else if (results.isEmpty() && !Boolean.TRUE.equals(viewModel.getIsLoading().getValue())) {
            // Show empty state for no results
            showNoResultsState();
        } else if (!results.isEmpty()) {
            // Show results
            showResultsState();
        }
    }

    private void showLoadingState() {
        binding.rvSearchResults.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
        binding.errorState.setVisibility(View.GONE);
        binding.loadingState.setVisibility(View.VISIBLE);
    }

    private void showResultsState() {
        binding.loadingState.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
        binding.errorState.setVisibility(View.GONE);
        binding.rvSearchResults.setVisibility(View.VISIBLE);
    }

    private void showEmptyState() {
        binding.loadingState.setVisibility(View.GONE);
        binding.rvSearchResults.setVisibility(View.GONE);
        binding.errorState.setVisibility(View.GONE);
        binding.txtEmptyTitle.setText(R.string.start_searching);
        binding.txtEmptySubtitle.setText(R.string.search_for_songs_artists_albums);
        binding.emptyState.setVisibility(View.VISIBLE);
    }

    private void showNoResultsState() {
        binding.loadingState.setVisibility(View.GONE);
        binding.rvSearchResults.setVisibility(View.GONE);
        binding.errorState.setVisibility(View.GONE);
        binding.txtEmptyTitle.setText("No results found");
        binding.txtEmptySubtitle.setText("Try different keywords");
        binding.emptyState.setVisibility(View.VISIBLE);
    }

    private void showErrorState(@NonNull String error) {
        binding.loadingState.setVisibility(View.GONE);
        binding.rvSearchResults.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
        binding.txtErrorMessage.setText(error);
        binding.errorState.setVisibility(View.VISIBLE);
    }

    private void applyTypefaces() {
        music.resona.utils.UiUXUtil.typeface(requireContext(), binding.searchInput, "medium.ttf", android.graphics.Typeface.NORMAL);
        music.resona.utils.UiUXUtil.typeface(requireContext(), binding.txtEmptyTitle, "akatski.ttf", android.graphics.Typeface.NORMAL);
        music.resona.utils.UiUXUtil.typeface(requireContext(), binding.txtEmptySubtitle, "medium.ttf", android.graphics.Typeface.NORMAL);
        music.resona.utils.UiUXUtil.typeface(requireContext(), binding.txtErrorMessage, "medium.ttf", android.graphics.Typeface.NORMAL);

        // Apply typeface to chips
        for (int i = 0; i < binding.chipGroupFilters.getChildCount(); i++) {
            View child = binding.chipGroupFilters.getChildAt(i);
            if (child instanceof Chip) {
                music.resona.utils.UiUXUtil.typeface(requireContext(), (Chip) child, "medium.ttf", android.graphics.Typeface.NORMAL);
            }
        }
    }

    private void hideKeyboard() {
        if (getActivity() != null && binding.searchInput != null) {
            android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(binding.searchInput.getWindowToken(), 0);
            }
        }
    }

    @Override
    public void onItemClick(@NonNull YTItemResult item, int position) {
        String type = item.getType();
        android.util.Log.d("SearchFragment", "Item clicked - Type: " + type + ", Title: " + item.getTitle() + ", BrowseId: " + item.getBrowseId());
        
        if ("song".equals(type)) {
            // Play song
            Toast.makeText(requireContext(), "Playing: " + item.getTitle(), Toast.LENGTH_SHORT).show();
            // TODO: Implement playback
        } else if ("album".equals(type) || "playlist".equals(type)) {
            // Open album/playlist detail
            String browseId = item.getBrowseId() != null ? item.getBrowseId() : item.getPlaylistId();
            if (browseId != null) {
                openVibeActivity(browseId, item.getTitle(), item.getThumbnail());
            }
        } else if ("artist".equals(type)) {
            // Open artist page - use browseId if available, otherwise fall back to id
            String artistId = item.getBrowseId() != null ? item.getBrowseId() : item.getId();
            if (artistId != null && !artistId.isEmpty()) {
                openArtistPage(artistId, item.getTitle());
            } else {
                android.util.Log.e("SearchFragment", "Artist has no ID: " + item.getTitle());
                Toast.makeText(requireContext(), "Cannot open artist page", Toast.LENGTH_SHORT).show();
            }
        } else {
            android.util.Log.w("SearchFragment", "Unknown type: " + type);
            Toast.makeText(requireContext(), "Type: " + type, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMoreClick(@NonNull YTItemResult item, int position) {
        Toast.makeText(requireContext(), "More options for: " + item.getTitle(), Toast.LENGTH_SHORT).show();
        // TODO: Show bottom sheet with more options
    }

    private void openVibeActivity(@NonNull String browseId, @NonNull String title, @Nullable String thumbnail) {
        Intent intent = new Intent(requireContext(), Vibe.class);
        intent.putExtra("browseId", browseId);
        intent.putExtra("title", title);
        intent.putExtra("thumbnailUrl", thumbnail);
        startActivity(intent);
    }
    
    private void openArtistPage(@NonNull String artistId, @NonNull String artistName) {
        android.util.Log.d("SearchFragment", "=== openArtistPage called ===");
        android.util.Log.d("SearchFragment", "Artist ID: " + artistId);
        android.util.Log.d("SearchFragment", "Artist Name: " + artistName);
        
        if (getActivity() != null) {
            try {
                ArtistFragment artistFragment = ArtistFragment.newInstance(artistId, artistName);
                android.util.Log.d("SearchFragment", "ArtistFragment created successfully");
                
                androidx.fragment.app.FragmentTransaction transaction = getActivity().getSupportFragmentManager()
                    .beginTransaction();
                transaction.setCustomAnimations(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                );
                transaction.replace(R.id.fragment_container, artistFragment, "ArtistFragment");
                transaction.addToBackStack("artist");
                transaction.commit();
                
                android.util.Log.d("SearchFragment", "Fragment transaction committed successfully");
            } catch (Exception e) {
                android.util.Log.e("SearchFragment", "Error opening artist page", e);
                Toast.makeText(requireContext(), "Error opening artist page: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            android.util.Log.e("SearchFragment", "getActivity() returned null!");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
