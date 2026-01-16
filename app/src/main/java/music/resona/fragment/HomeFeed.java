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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private NestedScrollView scrollView;
    private RecyclerView sectionsRecyclerView;
    private RecyclerView quickPicksRecyclerView;
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
        scrollView = view.findViewById(R.id.home_scroll_container);
        sectionsRecyclerView = view.findViewById(R.id.recyclerview_sections);
        quickPicksRecyclerView = view.findViewById(R.id.rv_quick_picks);
        skeletonLoader = view.findViewById(R.id.skeleton_loader);
        paginationLoader = view.findViewById(R.id.pagination_loader);
        chipGroup = view.findViewById(R.id.chips);
        profileImageView = view.findViewById(R.id.home_profile_avatar);
        greetingTextView = view.findViewById(R.id.home_greeting_text);
        usernameTextView = view.findViewById(R.id.home_username_text);
        
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
    
    private void setupRecyclerViews() {
        // Setup sections RecyclerView
        sectionsAdapter = new HomeSectionAdapter(requireContext(), new ArrayList<>());
        sectionsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        sectionsRecyclerView.setAdapter(sectionsAdapter);
        sectionsRecyclerView.setNestedScrollingEnabled(false);
        sectionsRecyclerView.setItemAnimator(null); // Disable animations for better performance
        
        // Setup quick picks RecyclerView with grid layout
        androidx.recyclerview.widget.GridLayoutManager gridLayout = 
            new androidx.recyclerview.widget.GridLayoutManager(
                requireContext(), 
                4, // 4 rows
                androidx.recyclerview.widget.GridLayoutManager.HORIZONTAL,
                false
            );
        quickPicksRecyclerView.setLayoutManager(gridLayout);
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
        
        if (picks == null || picks.isEmpty() || quickPicksRecyclerView == null) {
            if (quickPicksRecyclerView != null) {
                quickPicksRecyclerView.setVisibility(View.GONE);
            }
            return;
        }
        
        quickPicksRecyclerView.setVisibility(View.VISIBLE);
        
        // Prefetch for instant playback
        SongPrefetchHelper.getInstance().prefetchFromHomeFeed(picks, 5);
        
        quickPicksAdapter = new QuickPicksAdapter(requireContext(), picks);
        quickPicksRecyclerView.setAdapter(quickPicksAdapter);
    }
    
    private void handleLoadingStateUpdate(@NonNull HomeFeedViewModel.LoadingState state) {
        Log.d(TAG, "Loading state: " + state);
        
        switch (state) {
            case INITIAL_LOAD:
            case REFRESHING:
                // Show skeleton only for initial load
                if (sectionsAdapter == null || sectionsAdapter.getItemCount() == 0) {
                    skeletonLoader.setVisibility(View.VISIBLE);
                }
                paginationLoader.setVisibility(View.GONE);
                break;
                
            case PAGINATING:
                // Show pagination loader at bottom
                skeletonLoader.setVisibility(View.GONE);
                paginationLoader.setVisibility(View.VISIBLE);
                // Ensure it's above navigation
                paginationLoader.setElevation(16);
                paginationLoader.bringToFront();
                break;
                
            case IDLE:
                // Hide all loaders
                skeletonLoader.setVisibility(View.GONE);
                paginationLoader.setVisibility(View.GONE);
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
        
        if (!App.isVisitorDataReady()) {
            Log.d(TAG, "Visitor data not ready, retrying in " + DATA_LOAD_RETRY_MS + "ms");
            new Handler(Looper.getMainLooper()).postDelayed(this::loadInitialData, DATA_LOAD_RETRY_MS);
            return;
        }
        
        isLoadingInitialData = true;
        viewModel.loadHomeData();
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
