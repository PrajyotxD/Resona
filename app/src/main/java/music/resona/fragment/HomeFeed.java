package music.resona.fragment;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipDrawable;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import music.resona.R;
import music.resona.MainActivity;
import music.resona.adapters.HomeSectionAdapter;
import music.resona.adapters.QuickPicksAdapter;
import music.resona.app.App;
import music.resona.cache.SongPrefetchHelper;
import music.resona.online.bridge.models.ChipResult;
import music.resona.online.bridge.models.HomeSectionResult;
import music.resona.online.bridge.models.YTItemResult;
import music.resona.viewmodel.AccountInfoViewModel;
import music.resona.viewmodel.HomeFeedViewModel;

/**
 * Optimized HomeFeed Fragment with improved performance and smooth pagination.
 * 
 * <p>Key improvements:</p>
 * <ul>
 *   <li>Custom skeleton loading instead of shimmer</li>
 *   <li>Debounced scroll-based pagination</li>
 *   <li>Separate loading states for initial and pagination</li>
 *   <li>Better memory management</li>
 *   <li>Optimized view binding and updates</li>
 * </ul>
 */
public class HomeFeed extends Fragment {

    private static final String TAG = "HomeFeed";
    private static final int PAGINATION_THRESHOLD_PX = 300;
    private static final long DATA_LOAD_RETRY_MS = 1000;
    
    // ViewModels
    private HomeFeedViewModel viewModel;
    private AccountInfoViewModel accountInfoViewModel;
    
    // Views
    private SwipeRefreshLayout swipeRefreshLayout;
    private NestedScrollView scrollView;
    private RecyclerView sectionsRecyclerView;
    private RecyclerView quickPicksRecyclerView;
    private LinearLayout quickPicksSection;
    private View skeletonLoader;
    private View paginationLoader;
    private ChipGroup chipGroup;
    private ImageView profileImageView;
    private TextView greetingTextView;
    private TextView usernameTextView;
    
    // Adapters
    private HomeSectionAdapter sectionsAdapter;
    private QuickPicksAdapter quickPicksAdapter;
    
    // State
    private boolean isLoadingInitialData = false;
    private int previousSectionCount = 0;
    private boolean hasLoadedData = false;
    private int savedScrollPosition = 0;
    private List<HomeSectionResult> cachedPersonalizedSections = new ArrayList<>();
    private boolean personalizedSectionsAdded = false;
    
    @NonNull
    public static HomeFeed newInstance() {
        return new HomeFeed();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_feed, container, false);
        
        initializeViews(view);
        setupRecyclerViews();
        setupScrollListener();
        setupHeader();

        initializeViewModels();
        observeViewModels();
        
        // Only load data if not already loaded
        if (!hasLoadedData) {
            loadInitialData();
        } else {
            // Data already loaded, just hide skeleton
            if (skeletonLoader != null) {
                skeletonLoader.setVisibility(View.GONE);
            }
            // Restore scroll position
            if (scrollView != null && savedScrollPosition > 0) {
                scrollView.post(() -> scrollView.scrollTo(0, savedScrollPosition));
            }
        }
        
        return view;
    }
    
    private void initializeViews(@NonNull View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        scrollView = view.findViewById(R.id.home_scroll_container);
        sectionsRecyclerView = view.findViewById(R.id.recyclerview_sections);
        quickPicksRecyclerView = view.findViewById(R.id.rv_quick_picks);
        quickPicksSection = view.findViewById(R.id.ll_quick_picks_section);
        skeletonLoader = view.findViewById(R.id.skeleton_loader);
        paginationLoader = view.findViewById(R.id.pagination_loader);
        chipGroup = view.findViewById(R.id.chips);
        profileImageView = view.findViewById(R.id.home_profile_avatar);
        greetingTextView = view.findViewById(R.id.home_greeting_text);
        usernameTextView = view.findViewById(R.id.home_username_text);
        
        // Setup pull-to-refresh
        setupSwipeRefresh();
        
        // Start animated gradient background
        View rootView = view.findViewById(R.id.home_feed_root);
        if (rootView != null && rootView.getBackground() instanceof android.graphics.drawable.AnimationDrawable) {
            ((android.graphics.drawable.AnimationDrawable) rootView.getBackground()).start();
        }
        
        // Initially show skeleton, hide content and pagination loader
        skeletonLoader.setVisibility(View.VISIBLE);
        paginationLoader.setVisibility(View.GONE);
        
        // Start shimmer animation
        View shimmerOverlay = skeletonLoader.findViewById(R.id.shimmer_overlay);
        if (shimmerOverlay != null) {
            shimmerOverlay.startAnimation(android.view.animation.AnimationUtils.loadAnimation(
                requireContext(), R.anim.skeleton_shimmer_anim));
        }
    }
    
    private void initializeViewModels() {
        viewModel = new ViewModelProvider(this).get(HomeFeedViewModel.class);
        accountInfoViewModel = new ViewModelProvider(requireActivity()).get(AccountInfoViewModel.class);
    }
    
    private void setupSwipeRefresh() {
        if (swipeRefreshLayout == null) return;
        
        // Set refresh colors to match app theme
        swipeRefreshLayout.setColorSchemeColors(
            getResources().getColor(android.R.color.holo_red_light),
            getResources().getColor(android.R.color.holo_blue_light),
            getResources().getColor(android.R.color.holo_orange_light),
            getResources().getColor(android.R.color.holo_green_light)
        );
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(
            getResources().getColor(android.R.color.transparent)
        );
        
        swipeRefreshLayout.setOnRefreshListener(() -> {
            Log.d(TAG, "Pull-to-refresh triggered");
            refreshAllData();
        });
    }
    
    private void refreshAllData() {
        // Reset state for fresh load
        previousSectionCount = 0;
        personalizedSectionsAdded = false;
        cachedPersonalizedSections.clear();
        
        // Refresh API data (home feed sections)
        viewModel.refresh();
        
        // Refresh local data (personalized sections and Quick Picks)
        viewModel.loadPersonalizedSections();
        
        // Force reload Quick Picks from database
        if (requireContext() != null) {
            new music.resona.playback.QuickPicksManager(requireContext())
                .generateQuickPicks(new music.resona.playback.QuickPicksManager.QuickPicksCallback() {
                    @Override
                    public void onQuickPicksGenerated(List<YTItemResult> picks, boolean personalized) {
                        requireActivity().runOnUiThread(() -> {
                            Log.d(TAG, "Quick Picks refreshed: " + picks.size() + " items (personalized: " + personalized + ")");
                            if (!picks.isEmpty()) {
                                viewModel.updateQuickPicks(picks);
                            }
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Failed to refresh Quick Picks: " + error);
                    }
                });
        }
        
        Log.d(TAG, "Refreshing all data: API sections, personalized sections, and Quick Picks");
    }
    
    private void setupRecyclerViews() {
        // Setup sections RecyclerView
        sectionsAdapter = new HomeSectionAdapter(requireContext(), new ArrayList<>());
        sectionsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        sectionsRecyclerView.setAdapter(sectionsAdapter);
        sectionsRecyclerView.setNestedScrollingEnabled(false);
        sectionsRecyclerView.setItemAnimator(null); // Disable animations for better performance
        
        // Setup quick picks RecyclerView with horizontal scrolling grid (4 rows per column)
        androidx.recyclerview.widget.GridLayoutManager quickPicksLayoutManager = 
            new androidx.recyclerview.widget.GridLayoutManager(
                requireContext(),
                4, // 4 songs per column (4 rows)
                androidx.recyclerview.widget.GridLayoutManager.HORIZONTAL, // Scroll horizontally
                false
            );
        quickPicksRecyclerView.setLayoutManager(quickPicksLayoutManager);
        
        // Add spacing between grid items (8dp vertical, 4dp horizontal)
        int verticalSpacingPx = (int) (3 * getResources().getDisplayMetrics().density);
        int horizontalSpacingPx = (int) (2 * getResources().getDisplayMetrics().density);
        quickPicksRecyclerView.addItemDecoration(new androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@androidx.annotation.NonNull android.graphics.Rect outRect,
                                     @androidx.annotation.NonNull android.view.View view,
                                     @androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView parent,
                                     @androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView.State state) {
                androidx.recyclerview.widget.GridLayoutManager layoutManager = 
                    (androidx.recyclerview.widget.GridLayoutManager) parent.getLayoutManager();
                if (layoutManager == null) return;
                
                int position = parent.getChildAdapterPosition(view);
                int spanIndex = layoutManager.getSpanSizeLookup().getSpanIndex(position, layoutManager.getSpanCount());
                
                // Add vertical spacing between rows (except top row)
                if (spanIndex > 0) {
                    outRect.top = verticalSpacingPx;
                }
                
                // Add horizontal spacing between columns (except first column)
                int column = position / 4; // Which column (0-indexed)
                if (column > 0) {
                    outRect.left = horizontalSpacingPx;
                }
            }
        });
        
        // Add scroll listener for Quick Picks pagination (horizontal scrolling grid)
        quickPicksRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // Only trigger on rightward scroll (dx > 0)
                if (dx <= 0) return;
                
                androidx.recyclerview.widget.GridLayoutManager layoutManager = 
                    (androidx.recyclerview.widget.GridLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;
                
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();
                
                // Load more when user is within 2 columns (8 items) from the end
                if ((lastVisibleItemPosition + 8) >= totalItemCount) {
                    if (viewModel.canLoadMoreQuickPicks()) {
                        Log.d(TAG, "Near end of Quick Picks grid, loading more...");
                        viewModel.loadMoreQuickPicks();
                    }
                }
            }
        });
        
        quickPicksRecyclerView.setNestedScrollingEnabled(false);
        quickPicksRecyclerView.setVisibility(View.GONE);
    }
    
    private void setupScrollListener() {
        if (scrollView == null) return;
        
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    // Only trigger on downward scroll
                    if (scrollY <= oldScrollY) return;
                    
                    // Check if near bottom
                    if (isNearBottom(v)) {
                        Log.d(TAG, "Near bottom, requesting more data");
                        viewModel.loadMoreData();
                    }
                });
    }
    
    private boolean isNearBottom(@NonNull NestedScrollView scrollView) {
        if (scrollView.getChildCount() == 0) return false;
        
        View content = scrollView.getChildAt(0);
        int diff = content.getBottom() - (scrollView.getHeight() + scrollView.getScrollY());
        return diff <= PAGINATION_THRESHOLD_PX;
    }
    
    private void setupHeader() {
        updateGreeting();
        
        if (profileImageView != null) {
            profileImageView.setOnClickListener(v -> {
                if (requireActivity() instanceof MainActivity) {
                    ((MainActivity) requireActivity()).requestAuthentication();
                }
            });
        }
        
        // Apply custom fonts
        if (usernameTextView != null) {
            music.resona.utils.UiUXUtil.typeface(requireContext(), usernameTextView, "akatski.ttf", android.graphics.Typeface.BOLD);
        }
        if (greetingTextView != null) {
            music.resona.utils.UiUXUtil.typeface(requireContext(), greetingTextView, "medium.ttf", android.graphics.Typeface.NORMAL);
        }
    }
    
    private void updateGreeting() {
        if (greetingTextView == null) return;
        
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) greeting = "Good Morning!";
        else if (hour < 17) greeting = "Good Afternoon!";
        else if (hour < 21) greeting = "Good Evening!";
        else greeting = "Good Night!";
        
        greetingTextView.setText(greeting);
    }
    
    private void observeViewModels() {
        // Observe home sections
        viewModel.getHomeSections().observe(getViewLifecycleOwner(), this::handleSectionsUpdate);
        
        // Observe personalized sections
        viewModel.getPersonalizedSections().observe(getViewLifecycleOwner(), this::handlePersonalizedSectionsUpdate);
        
        // Observe chips
        viewModel.getChips().observe(getViewLifecycleOwner(), this::handleChipsUpdate);
        
        // Observe quick picks
        viewModel.getQuickPicks().observe(getViewLifecycleOwner(), this::handleQuickPicksUpdate);
        
        // Observe loading state
        viewModel.getLoadingState().observe(getViewLifecycleOwner(), this::handleLoadingStateUpdate);
        
        // Observe errors
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), this::handleErrorUpdate);
        
        // Observe account info
        accountInfoViewModel.getAccountInfo().observe(getViewLifecycleOwner(), state -> {
            if (state != null) {
                updateHeader(state.displayName, state.avatarUrl, state.authenticated);
            }
        });
    }
    
    private void handleSectionsUpdate(@NonNull List<HomeSectionResult> sections) {
        Log.d(TAG, "Sections updated: " + sections.size() + ", previous: " + previousSectionCount);
        
        if (sections.isEmpty()) {
            return;
        }
        
        // Check if this is pagination (more items added) or fresh load
        boolean isPagination = previousSectionCount > 0 && sections.size() > previousSectionCount;
        
        if (sectionsAdapter == null) {
            // First time: create adapter
            sectionsAdapter = new HomeSectionAdapter(requireContext(), sections);
            sectionsRecyclerView.setAdapter(sectionsAdapter);
            previousSectionCount = sections.size();
            
            // Add cached personalized sections if available
            if (!cachedPersonalizedSections.isEmpty() && !personalizedSectionsAdded) {
                sectionsAdapter.addSections(0, cachedPersonalizedSections);
                personalizedSectionsAdded = true;
                Log.d(TAG, "Added cached " + cachedPersonalizedSections.size() + " personalized sections to new adapter");
            }
        } else if (isPagination) {
            // Pagination: add only new sections without reloading existing ones
            List<HomeSectionResult> newSections = sections.subList(previousSectionCount, sections.size());
            Log.d(TAG, "Pagination detected, adding " + newSections.size() + " new sections");
            sectionsAdapter.addSections(newSections);
            previousSectionCount = sections.size();
        } else {
            // Fresh load (filter or refresh): update all sections
            Log.d(TAG, "Fresh load detected, updating all sections");
            sectionsAdapter.updateSections(sections);
            previousSectionCount = sections.size();
            personalizedSectionsAdded = false;
            
            // Re-add personalized sections after refresh
            if (!cachedPersonalizedSections.isEmpty()) {
                sectionsAdapter.addSections(0, cachedPersonalizedSections);
                personalizedSectionsAdded = true;
                Log.d(TAG, "Re-added " + cachedPersonalizedSections.size() + " personalized sections after refresh");
            }
        }
        
        // Hide skeleton on first data
        if (skeletonLoader.getVisibility() == View.VISIBLE && !sections.isEmpty()) {
            skeletonLoader.setVisibility(View.GONE);
            hasLoadedData = true;
        }
    }
    
    private void handleChipsUpdate(@Nullable List<ChipResult> chips) {
        if (chips == null || chips.isEmpty() || chipGroup == null) return;
        
        Log.d(TAG, "Chips updated: " + chips.size());
        chipGroup.removeAllViews();
        chipGroup.setSingleSelection(true);
        
        int[][] states = new int[][] {
            new int[] { android.R.attr.state_checked },
            new int[] { -android.R.attr.state_checked }
        };
        int[] backgroundColors = new int[] {
            Color.parseColor("#50FFFFFF"),
            Color.parseColor("#12FFFFFF")
        };
        int[] strokeColors = new int[] {
            Color.parseColor("#C0FFFFFF"),
            Color.parseColor("#50FFFFFF")
        };
        ColorStateList backgroundStateList = new ColorStateList(states, backgroundColors);
        ColorStateList strokeStateList = new ColorStateList(states, strokeColors);
        
        for (ChipResult chipResult : chips) {
            Chip chip = new Chip(
                new android.view.ContextThemeWrapper(requireContext(), R.style.ResonaChip),
                null, 0
            );
            
            ChipDrawable drawable = ChipDrawable.createFromAttributes(
                requireContext(), null, 0, R.style.ResonaChip
            );
            drawable.setChipBackgroundColor(backgroundStateList);
            drawable.setChipStrokeColor(strokeStateList);
            drawable.setRippleColor(ColorStateList.valueOf(Color.parseColor("#26FFFFFF")));
            chip.setChipDrawable(drawable);
            chip.setTextColor(Color.WHITE);
            chip.setText(chipResult.getTitle());
            music.resona.utils.UiUXUtil.typeface(requireContext(), chip, "xdx.ttf", android.graphics.Typeface.NORMAL);
            chip.setCheckable(true);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setCloseIconVisible(false);
            chip.setChipIconVisible(false);
            
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    Log.d(TAG, "Chip selected: " + chipResult.getTitle());
                    viewModel.filterByChip(chipResult);
                }
            });
            
            chipGroup.addView(chip);
        }
    }
    
    private void handleQuickPicksUpdate(@Nullable List<YTItemResult> picks) {
        Log.d(TAG, "Quick picks updated: " + (picks != null ? picks.size() : 0));
        
        if (picks == null || picks.isEmpty() || quickPicksRecyclerView == null || quickPicksSection == null) {
            Log.d(TAG, "Hiding quick picks - picks: " + (picks == null ? "null" : picks.size()) + 
                  ", recyclerView: " + (quickPicksRecyclerView == null ? "null" : "exists") +
                  ", section: " + (quickPicksSection == null ? "null" : "exists"));
            if (quickPicksSection != null) {
                quickPicksSection.setVisibility(View.GONE);
            }
            return;
        }
        
        Log.d(TAG, "Showing quick picks with " + picks.size() + " items");
        quickPicksSection.setVisibility(View.VISIBLE);
        quickPicksRecyclerView.setVisibility(View.VISIBLE);
        
        // Prefetch for instant playback
        SongPrefetchHelper.getInstance().prefetchFromHomeFeed(picks, 5);
        
        // Update existing adapter or create new one if needed
        if (quickPicksAdapter == null) {
            Log.d(TAG, "Creating new QuickPicksAdapter with " + picks.size() + " items");
            quickPicksAdapter = new QuickPicksAdapter(requireContext(), picks);
            quickPicksRecyclerView.setAdapter(quickPicksAdapter);
        } else {
            Log.d(TAG, "Updating existing QuickPicksAdapter from " + quickPicksAdapter.getItemCount() + " to " + picks.size() + " items");
            quickPicksAdapter.updateItems(picks);
        }
        
        // Force layout update
        quickPicksRecyclerView.post(() -> {
            Log.d(TAG, "QuickPicks RecyclerView - Visibility: " + 
                  (quickPicksRecyclerView.getVisibility() == View.VISIBLE ? "VISIBLE" : "GONE") +
                  ", Height: " + quickPicksRecyclerView.getHeight() +
                  ", Adapter items: " + (quickPicksAdapter != null ? quickPicksAdapter.getItemCount() : "null"));
        });
    }
    
    private void handleLoadingStateUpdate(@NonNull HomeFeedViewModel.LoadingState state) {
        Log.d(TAG, "Loading state: " + state);
        
        switch (state) {
            case INITIAL_LOAD:
                // Show skeleton only for initial load
                if (sectionsAdapter == null || sectionsAdapter.getItemCount() == 0) {
                    skeletonLoader.setVisibility(View.VISIBLE);
                }
                paginationLoader.setVisibility(View.GONE);
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                break;
                
            case REFRESHING:
                // For refresh, don't show skeleton if we already have content
                // SwipeRefreshLayout will show its own indicator
                paginationLoader.setVisibility(View.GONE);
                break;
                
            case PAGINATING:
                // Show pagination loader at bottom
                skeletonLoader.setVisibility(View.GONE);
                paginationLoader.setVisibility(View.VISIBLE);
                // Ensure it's above navigation
                paginationLoader.setElevation(16);
                paginationLoader.bringToFront();
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                break;
                
            case IDLE:
                // Hide all loaders
                skeletonLoader.setVisibility(View.GONE);
                paginationLoader.setVisibility(View.GONE);
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                break;
        }
    }
    
    private void handleErrorUpdate(@Nullable String error) {
        if (error != null && !error.isEmpty()) {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateHeader(@Nullable String displayName, @Nullable String avatarUrl, boolean authenticated) {
        if (usernameTextView != null) {
            String fallback = authenticated ? "User" : "Guest";
            usernameTextView.setText(displayName != null && !displayName.isEmpty() ? displayName : fallback);
        }
        
        if (profileImageView != null) {
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                Glide.with(this)
                    .load(avatarUrl)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.memefi)
                    .error(R.drawable.memefi)
                    .into(profileImageView);
            } else {
                profileImageView.setImageResource(R.drawable.customer);
            }
        }
    }
    
    private void loadInitialData() {
        if (isLoadingInitialData) {
            Log.d(TAG, "Already loading initial data");
            return;
        }
        
        Log.d(TAG, "Loading initial data");
        viewModel.setContext(requireContext());
        
        if (!App.isVisitorDataReady()) {
            Log.d(TAG, "Visitor data not ready, retrying in " + DATA_LOAD_RETRY_MS + "ms");
            new Handler(Looper.getMainLooper()).postDelayed(this::loadInitialData, DATA_LOAD_RETRY_MS);
            return;
        }
        
        isLoadingInitialData = true;
        viewModel.loadHomeData();
        viewModel.loadPersonalizedSections();
    }
    
    private void handlePersonalizedSectionsUpdate(@NonNull List<HomeSectionResult> personalizedSections) {
        Log.d(TAG, "Personalized sections updated: " + personalizedSections.size());
        
        if (personalizedSections.isEmpty()) {
            Log.d(TAG, "No personalized sections available yet");
            return;
        }
        
        // Cache the personalized sections
        cachedPersonalizedSections = new ArrayList<>(personalizedSections);
        
        if (sectionsAdapter == null) {
            Log.d(TAG, "Adapter not ready yet, personalized sections cached and will be added when home sections load");
            return;
        }
        
        if (personalizedSectionsAdded) {
            Log.d(TAG, "Personalized sections already added, skipping");
            return;
        }
        
        // Insert personalized sections at the top
        sectionsAdapter.addSections(0, personalizedSections);
        personalizedSectionsAdded = true;
        Log.d(TAG, "Added " + personalizedSections.size() + " personalized sections at position 0");
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Save scroll position before destroying view
        if (scrollView != null) {
            savedScrollPosition = scrollView.getScrollY();
        }
        
        // Clear references to prevent memory leaks but keep state flags
        // previousSectionCount is kept to track pagination
        // hasLoadedData is kept to prevent reload on tab switch
        swipeRefreshLayout = null;
        scrollView = null;
        sectionsRecyclerView = null;
        quickPicksRecyclerView = null;
        skeletonLoader = null;
        paginationLoader = null;
        chipGroup = null;
        profileImageView = null;
        greetingTextView = null;
        usernameTextView = null;
        sectionsAdapter = null;
        quickPicksAdapter = null;
    }
}
