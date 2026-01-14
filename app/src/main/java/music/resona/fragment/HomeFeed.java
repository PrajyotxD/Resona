package music.resona.fragment;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
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

import music.resona.app.App;
import music.resona.cache.SongPrefetchHelper;
import music.resona.R;
import music.resona.adapters.HomeSectionAdapter;
import music.resona.MainActivity;
import music.resona.online.bridge.models.ChipResult;
import music.resona.viewmodel.AccountInfoViewModel;
import music.resona.viewmodel.HomeFeedView;

/**
 * Fragment displaying the YouTube Music home feed.
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Displays home sections in a RecyclerView</li>
 *   <li>Observes ViewModel LiveData for data updates</li>
 *   <li>Handles infinite scroll pagination</li>
 *   <li>Shows loading states and error messages</li>
 * </ul>
 */
public class HomeFeed extends Fragment {

    private static final String TAG = "HomeFeed";
    private static final int PAGINATION_SCROLL_BUFFER_PX = 200;
    
    private HomeFeedView viewModel;
    private RecyclerView recyclerView;
    private RecyclerView quickPicksRecyclerView;
    private dev.ui.obscura.shimmer.ShimmerLayout skeletonLoader;
    private ChipGroup chipGroup;
    private NestedScrollView homeScrollView;
    private HomeSectionAdapter adapter;
    private ImageView profileImageView;
    private TextView greetingTextView;
    private TextView usernameTextView;
    private AccountInfoViewModel accountInfoViewModel;

    @NonNull
    public static HomeFeed newInstance() {
        return new HomeFeed();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView called");
        View view = inflater.inflate(R.layout.fragment_home_feed, container, false);
        
        initializeViews(view);
        setupRecyclerView();
        setupScrollBehavior();
        setupHeaderInteractions();
        
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(TAG, "onActivityCreated called");
        
        initializeViewModel();
        observeViewModel();
        loadHomeData();
        observeAccountInfo();
    }
    
    /**
     * Initializes view references.
     * 
     * @param view the root view
     */
    private void initializeViews(@NonNull View view) {
        recyclerView = view.findViewById(R.id.recyclerview1);
        quickPicksRecyclerView = view.findViewById(R.id.rvQuickPicks);
        skeletonLoader = view.findViewById(R.id.skeleton_loader);
        chipGroup = view.findViewById(R.id.chips);
        homeScrollView = view.findViewById(R.id.home_scroll_container);
        profileImageView = view.findViewById(R.id.home_profile_avatar);
        greetingTextView = view.findViewById(R.id.home_greeting_text);
        usernameTextView = view.findViewById(R.id.home_username_text);
    }
    
    /**
     * Initializes the ViewModel.
     */
    private void initializeViewModel() {
        viewModel = new ViewModelProvider(this).get(HomeFeedView.class);
        Log.d(TAG, "ViewModel initialized");
    }
    
    /**
     * Configures the RecyclerView with adapter and scroll listener.
     */
    private void setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView");
        
        adapter = new HomeSectionAdapter(requireContext(), new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        
        // Setup quick picks as grid with 4 rows
        if (quickPicksRecyclerView != null) {
            androidx.recyclerview.widget.GridLayoutManager gridLayout = 
                new androidx.recyclerview.widget.GridLayoutManager(
                    requireContext(), 
                    4, // 4 rows
                    androidx.recyclerview.widget.GridLayoutManager.HORIZONTAL,
                    false
                );
            quickPicksRecyclerView.setLayoutManager(gridLayout);
            quickPicksRecyclerView.setNestedScrollingEnabled(false);
        }
    }

    private void setupHeaderInteractions() {
        updateGreetingText();
        if (profileImageView != null) {
            profileImageView.setOnClickListener(v -> {
                if (requireActivity() instanceof MainActivity) {
                    ((MainActivity) requireActivity()).requestAuthentication();
                }
            });
        }
        
        // Apply typefaces using UiUXUtil
        if (usernameTextView != null) {
            music.resona.utils.UiUXUtil.typeface(requireContext(), usernameTextView, "akatski.ttf", android.graphics.Typeface.BOLD);
        }
        if (greetingTextView != null) {
            music.resona.utils.UiUXUtil.typeface(requireContext(), greetingTextView, "medium.ttf", android.graphics.Typeface.NORMAL);
        }
    }

    private void observeAccountInfo() {
        accountInfoViewModel = new ViewModelProvider(requireActivity()).get(AccountInfoViewModel.class);
        accountInfoViewModel.getAccountInfo().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            updateHeader(state.displayName, state.avatarUrl, state.authenticated);
        });
    }

    private void updateHeader(@Nullable String displayName,
                              @Nullable String avatarUrl,
                              boolean authenticated) {
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

    private void updateGreetingText() {
        if (greetingTextView == null) return;
        greetingTextView.setText(getGreetingForNow());
    }

    private String getGreetingForNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good Morning!";
        if (hour < 17) return "Good Afternoon!";
        if (hour < 21) return "Good Evening!";
        return "Good Night!";
    }

    private void handleChipSelection(ChipResult chip) {
        if (chip.getBrowseId() == null && chip.getParams() == null) {
            Log.w(TAG, "Chip has no browseId or params, skipping filter");
            return;
        }
        
        Log.d(TAG, "Filtering feed with chip: " + chip.getTitle());
        if (viewModel != null) {
            viewModel.filterByChip(chip);
        }
    }
    /**
     * Configures scroll behavior when RecyclerView is wrapped in NestedScrollView.
     */
    private void setupScrollBehavior() {
        if (homeScrollView == null) return;

        homeScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (scrollY <= oldScrollY) return;
                    if (isNearBottom(v)) {
                        requestNextPage();
                    }
                });
    }

    private boolean isNearBottom(@NonNull NestedScrollView scrollView) {
        if (scrollView.getChildCount() == 0) return false;
        View content = scrollView.getChildAt(scrollView.getChildCount() - 1);
        int diff = content.getBottom() - (scrollView.getHeight() + scrollView.getScrollY());
        return diff <= PAGINATION_SCROLL_BUFFER_PX;
    }

    private void requestNextPage() {
        if (viewModel != null && viewModel.canLoadMore()) {
            viewModel.loadMoreData();
        }
    }
    
    /**
     * Sets up LiveData observers for ViewModel data changes.
     */
    private void observeViewModel() {
        Log.d(TAG, "Setting up LiveData observers");
        
        viewModel.getHomeSections().observe(getViewLifecycleOwner(), this::handleSectionsUpdate);
        viewModel.getChips().observe(getViewLifecycleOwner(), this::handleChipsUpdate);
        viewModel.getQuickPicks().observe(getViewLifecycleOwner(), this::handleQuickPicksUpdate);
        viewModel.getLoadingState().observe(getViewLifecycleOwner(), this::handleLoadingStateUpdate);
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), this::handleErrorUpdate);
    }
    
    /**
     * Handles home sections data updates.
     * 
     * @param sections the updated list of sections
     */
    private void handleSectionsUpdate(@NonNull java.util.List<music.resona.online.bridge.models.HomeSectionResult> sections) {
        Log.d(TAG, "Sections updated: " + sections.size() + " sections");
        
        adapter = new HomeSectionAdapter(requireContext(), sections);
        recyclerView.setAdapter(adapter);
        
        if (!sections.isEmpty() && skeletonLoader != null) {
            skeletonLoader.setVisibility(View.GONE);
        }
    }
    
    /**
     * Handles chips/filters update.
     * 
     * @param chips the list of filter chips
     */
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
                    handleChipSelection(chipResult);
                }
            });

            chipGroup.addView(chip);
        }
    }
    
    private void handleQuickPicksUpdate(@Nullable List<music.resona.online.bridge.models.YTItemResult> picks) {
        Log.d(TAG, "handleQuickPicksUpdate called");
        Log.d(TAG, "Quick picks: " + (picks != null ? picks.size() : "null"));
        Log.d(TAG, "RecyclerView: " + (quickPicksRecyclerView != null ? "exists" : "null"));
        
        if (picks == null || picks.isEmpty() || quickPicksRecyclerView == null) {
            Log.w(TAG, "Hiding quick picks - picks empty or RV null");
            if (quickPicksRecyclerView != null) {
                quickPicksRecyclerView.setVisibility(View.GONE);
            }
            return;
        }
        
        Log.d(TAG, "Showing quick picks with " + picks.size() + " items");
        quickPicksRecyclerView.setVisibility(View.VISIBLE);
        
        // Prefetch stream URLs for quick picks to enable instant playback
        SongPrefetchHelper.getInstance().prefetchFromHomeFeed(picks, 5);
        
        music.resona.adapters.QuickPicksAdapter quickPicksAdapter = 
            new music.resona.adapters.QuickPicksAdapter(requireContext(), picks);
        quickPicksRecyclerView.setAdapter(quickPicksAdapter);
        Log.d(TAG, "Quick picks adapter set");
    }



    /**
     * Handles loading state updates.
     * 
     * @param isLoading the current loading state
     */
    private void handleLoadingStateUpdate(@Nullable Boolean isLoading) {
        Log.d(TAG, "Loading state: " + isLoading);
        
        if (skeletonLoader != null) {
            skeletonLoader.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE);
        }
    }
    
    /**
     * Handles error message updates.
     * 
     * @param error the error message, or null if no error
     */
    private void handleErrorUpdate(@Nullable String error) {
        if (error != null && !error.isEmpty()) {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Loads home data immediately using cached visitor data or authentication.
     */
    private void loadHomeData() {
        Log.d(TAG, "Loading home data...");
        Log.d(TAG, "Visitor data ready: " + App.isVisitorDataReady());
        
        if (!App.isVisitorDataReady()) {
            Log.d(TAG, "Visitor data not ready yet, retrying in 1 second...");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    loadHomeData(); // Retry
                }
            }, 1000);
            return;
        }
        
        viewModel.loadHomeData();
    }
}