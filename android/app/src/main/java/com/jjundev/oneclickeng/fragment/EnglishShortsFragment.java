package com.jjundev.oneclickeng.fragment;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.fragment.shorts.EnglishShortsViewModel;
import com.jjundev.oneclickeng.others.EnglishShortsItem;
import com.jjundev.oneclickeng.others.EnglishShortsPagerAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EnglishShortsFragment extends Fragment {
  private static final String TAG = "EnglishShortsFragment";

  @NonNull private final List<View> progressBars = new ArrayList<>();
  @NonNull private List<EnglishShortsItem> shortsItems = new ArrayList<>();

  @Nullable private ViewPager2 viewPager;
  @Nullable private LinearLayout layoutTopOverlay;
  @Nullable private LinearLayout layoutActionRail;
  @Nullable private LinearLayout layoutBottomMeta;
  @Nullable private LinearLayout progressContainer;
  @Nullable private TextView tvTitle;
  @Nullable private TextView tvTag;
  @Nullable private TextView tvLikeCount;
  @Nullable private ViewPager2.OnPageChangeCallback pageChangeCallback;

  // State views
  @Nullable private View layoutLoading;
  @Nullable private TextView tvEmpty;
  @Nullable private View layoutError;

  @Nullable private EnglishShortsPagerAdapter adapter;
  @Nullable private EnglishShortsViewModel viewModel;
  @Nullable private java.util.concurrent.ExecutorService preloaderExecutor;

  private int topOverlayBasePaddingTop;
  private int actionRailBaseBottomMargin;
  private int bottomMetaBaseBottomMargin;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_english_shorts, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    bindViews(view);
    setupViewPager();
    setupLikeButton(view);
    setupEdgeToEdgeInsets(view);
    setupRetryButton(view);

    viewModel = new ViewModelProvider(this).get(EnglishShortsViewModel.class);
    observeViewModel();

    logDebug("English Shorts fragment initialized.");
  }

  private void bindViews(@NonNull View root) {
    viewPager = root.findViewById(R.id.vp_english_shorts);
    layoutTopOverlay = root.findViewById(R.id.layout_shorts_top_overlay);
    layoutActionRail = root.findViewById(R.id.layout_shorts_action_rail);
    layoutBottomMeta = root.findViewById(R.id.layout_shorts_bottom_meta);
    progressContainer = root.findViewById(R.id.layout_shorts_progress);
    tvTitle = root.findViewById(R.id.tv_shorts_title);
    tvTag = root.findViewById(R.id.tv_shorts_tag);
    tvLikeCount = root.findViewById(R.id.tv_shorts_like_count);

    layoutLoading = root.findViewById(R.id.layout_shorts_loading);
    tvEmpty = root.findViewById(R.id.tv_shorts_empty);
    layoutError = root.findViewById(R.id.layout_shorts_error);
  }

  private void observeViewModel() {
    if (viewModel == null) return;

    viewModel
        .getIsLoading()
        .observe(
            getViewLifecycleOwner(),
            isLoading -> {
              if (layoutLoading != null) {
                layoutLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
              }
            });

    viewModel
        .getErrorMessage()
        .observe(
            getViewLifecycleOwner(),
            errorMsg -> {
              if (layoutError != null) {
                layoutError.setVisibility(errorMsg != null ? View.VISIBLE : View.GONE);
              }
            });

    viewModel
        .getShortsItems()
        .observe(
            getViewLifecycleOwner(),
            items -> {
              if (items == null || items.isEmpty()) {
                showEmptyState();
                return;
              }
              shortsItems = items;
              showContentState(items);
            });
  }

  private void showEmptyState() {
    if (viewPager != null) viewPager.setVisibility(View.GONE);
    if (layoutTopOverlay != null) layoutTopOverlay.setVisibility(View.GONE);
    if (layoutActionRail != null) layoutActionRail.setVisibility(View.GONE);
    if (layoutBottomMeta != null) layoutBottomMeta.setVisibility(View.GONE);

    Boolean isLoading = viewModel != null ? viewModel.getIsLoading().getValue() : Boolean.FALSE;
    String errorMsg = viewModel != null ? viewModel.getErrorMessage().getValue() : null;
    if (tvEmpty != null && !Boolean.TRUE.equals(isLoading) && errorMsg == null) {
      tvEmpty.setVisibility(View.VISIBLE);
    }
  }

  private void showContentState(@NonNull List<EnglishShortsItem> items) {
    if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
    if (viewPager != null) viewPager.setVisibility(View.VISIBLE);
    if (layoutTopOverlay != null) layoutTopOverlay.setVisibility(View.VISIBLE);
    if (layoutActionRail != null) layoutActionRail.setVisibility(View.VISIBLE);
    if (layoutBottomMeta != null) layoutBottomMeta.setVisibility(View.VISIBLE);

    if (adapter != null) {
      adapter.submitList(items);
    }
    setupProgressIndicators();
    if (!items.isEmpty()) {
      renderOverlay(items.get(0), 0);
      playVideoAtPosition(0);
    }
    preloadVideos(items);
  }

  private void preloadVideos(@NonNull List<EnglishShortsItem> items) {
    if (preloaderExecutor == null) {
      preloaderExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    }

    preloaderExecutor.execute(
        () -> {
          try {
            androidx.media3.datasource.cache.SimpleCache cache =
                com.jjundev.oneclickeng.OneClickEngApplication.getCache(
                    requireContext().getApplicationContext());
            androidx.media3.datasource.cache.CacheDataSource cacheDataSource =
                new androidx.media3.datasource.cache.CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(
                        new androidx.media3.datasource.DefaultHttpDataSource.Factory())
                    .createDataSource();

            for (EnglishShortsItem item : items) {
              String videoUrl = item.getVideoUrl();
              if (videoUrl == null || videoUrl.isEmpty()) continue;
              if (videoUrl.startsWith("\"") && videoUrl.endsWith("\"") && videoUrl.length() > 2) {
                videoUrl = videoUrl.substring(1, videoUrl.length() - 1);
              }

              androidx.media3.datasource.DataSpec dataSpec =
                  new androidx.media3.datasource.DataSpec(android.net.Uri.parse(videoUrl));
              androidx.media3.datasource.cache.CacheWriter cacheWriter =
                  new androidx.media3.datasource.cache.CacheWriter(
                      cacheDataSource, dataSpec, null, null);

              logDebug("Preloading video: " + videoUrl);
              cacheWriter.cache();
              logDebug("Preloaded video: " + videoUrl);
            }
          } catch (Exception e) {
            logDebug("Failed to preload video: " + e.getMessage());
          }
        });
  }

  private void setupRetryButton(@NonNull View root) {
    View btnRetry = root.findViewById(R.id.btn_shorts_retry);
    if (btnRetry != null && viewModel != null) {
      btnRetry.setOnClickListener(v -> viewModel.retry());
    }
  }

  private void setupViewPager() {
    ViewPager2 pager = viewPager;
    if (pager == null) return;

    pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
    pager.setOffscreenPageLimit(1);
    adapter = new EnglishShortsPagerAdapter(shortsItems);
    pager.setAdapter(adapter);
    pager.setPageTransformer(
        (page, position) -> {
          float absPosition = Math.abs(position);
          page.setAlpha(1f - Math.min(absPosition * 0.35f, 0.35f));
          page.setScaleY(Math.max(0.94f, 1f - (absPosition * 0.05f)));
        });

    pageChangeCallback =
        new ViewPager2.OnPageChangeCallback() {
          @Override
          public void onPageSelected(int position) {
            super.onPageSelected(position);
            if (position < 0 || position >= shortsItems.size()) return;
            renderOverlay(shortsItems.get(position), position);
            playVideoAtPosition(position);
            logDebug("Short page changed: index=" + position);
          }
        };
    pager.registerOnPageChangeCallback(pageChangeCallback);
  }

  private void playVideoAtPosition(int position) {
    if (viewPager == null || adapter == null) return;
    RecyclerView recyclerView = (RecyclerView) viewPager.getChildAt(0);
    adapter.playAtPosition(recyclerView, position);
  }

  private void setupLikeButton(@NonNull View root) {
    View likeButton = root.findViewById(R.id.btn_shorts_like);
    if (likeButton != null) {
      likeButton.setOnClickListener(
          v -> {
            Toast.makeText(
                    requireContext(),
                    getString(R.string.english_shorts_action_like_toast),
                    Toast.LENGTH_SHORT)
                .show();
          });
    }

    View dislikeButton = root.findViewById(R.id.btn_shorts_dislike);
    if (dislikeButton != null) {
      dislikeButton.setOnClickListener(
          v -> {
            Toast.makeText(
                    requireContext(),
                    getString(R.string.english_shorts_action_dislike_toast),
                    Toast.LENGTH_SHORT)
                .show();
          });
    }
  }

  private void setupProgressIndicators() {
    LinearLayout container = progressContainer;
    if (container == null) return;

    container.removeAllViews();
    progressBars.clear();
    for (int i = 0; i < shortsItems.size(); i++) {
      View bar = new View(requireContext());
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(4), 1f);
      if (i > 0) params.setMarginStart(dp(5));
      bar.setLayoutParams(params);
      container.addView(bar);
      progressBars.add(bar);
    }
    updateProgressIndicators(0);
  }

  private void renderOverlay(@NonNull EnglishShortsItem item, int index) {
    if (tvTitle != null) tvTitle.setText(item.getTitle());
    if (tvTag != null) {
      String tag = item.getTag();
      if (tag != null && !tag.isEmpty()) {
        tvTag.setText(tag);
        tvTag.setVisibility(View.VISIBLE);
      } else {
        tvTag.setVisibility(View.GONE);
      }
    }
    if (tvLikeCount != null) tvLikeCount.setText(formatCount(item.getLikeCount()));
    updateProgressIndicators(index);
  }

  private void updateProgressIndicators(int currentIndex) {
    for (int i = 0; i < progressBars.size(); i++) {
      View bar = progressBars.get(i);
      int colorRes =
          i <= currentIndex ? R.color.shorts_progress_active : R.color.shorts_progress_inactive;
      int color = ContextCompat.getColor(requireContext(), colorRes);
      GradientDrawable drawable = new GradientDrawable();
      drawable.setShape(GradientDrawable.RECTANGLE);
      drawable.setCornerRadius(dp(2));
      drawable.setColor(color);
      bar.setBackground(drawable);
      bar.setAlpha(i < currentIndex ? 0.8f : i == currentIndex ? 1f : 0.42f);
    }
  }

  private void setupEdgeToEdgeInsets(@NonNull View root) {
    LinearLayout topOverlay = layoutTopOverlay;
    LinearLayout actionRail = layoutActionRail;
    LinearLayout bottomMeta = layoutBottomMeta;
    if (topOverlay == null || actionRail == null || bottomMeta == null) return;

    topOverlayBasePaddingTop = topOverlay.getPaddingTop();
    ViewGroup.MarginLayoutParams actionParams =
        (ViewGroup.MarginLayoutParams) actionRail.getLayoutParams();
    actionRailBaseBottomMargin = actionParams.bottomMargin;
    ViewGroup.MarginLayoutParams metaParams =
        (ViewGroup.MarginLayoutParams) bottomMeta.getLayoutParams();
    bottomMetaBaseBottomMargin = metaParams.bottomMargin;

    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (view, insets) -> {
          Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          topOverlay.setPadding(
              topOverlay.getPaddingLeft(),
              topOverlayBasePaddingTop + systemBars.top,
              topOverlay.getPaddingRight(),
              topOverlay.getPaddingBottom());
          ViewGroup.MarginLayoutParams ap =
              (ViewGroup.MarginLayoutParams) actionRail.getLayoutParams();
          ap.bottomMargin = actionRailBaseBottomMargin + systemBars.bottom;
          actionRail.setLayoutParams(ap);
          ViewGroup.MarginLayoutParams mp =
              (ViewGroup.MarginLayoutParams) bottomMeta.getLayoutParams();
          mp.bottomMargin = bottomMetaBaseBottomMargin + systemBars.bottom;
          bottomMeta.setLayoutParams(mp);
          return insets;
        });
    ViewCompat.requestApplyInsets(root);
  }

  @Override
  public void onPause() {
    super.onPause();
    if (viewPager != null && adapter != null) {
      RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
      adapter.pauseAll(rv);
    }
  }

  @Override
  public void onStop() {
    if (viewPager != null && adapter != null) {
      RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
      adapter.pauseAll(rv);
    }
    super.onStop();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (viewPager != null && !shortsItems.isEmpty()) {
      playVideoAtPosition(viewPager.getCurrentItem());
    }
  }

  @NonNull
  private String formatCount(int count) {
    if (count >= 1_000_000) return String.format(Locale.US, "%.1fM", count / 1_000_000f);
    if (count >= 1_000) return String.format(Locale.US, "%.1fK", count / 1_000f);
    return String.valueOf(count);
  }

  private int dp(int dp) {
    return Math.round(dp * getResources().getDisplayMetrics().density);
  }

  @Override
  public void onDestroyView() {
    if (viewPager != null && adapter != null) {
      RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
      adapter.releaseAll(rv);
    }
    if (viewPager != null && pageChangeCallback != null) {
      viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
    }
    pageChangeCallback = null;
    viewPager = null;
    layoutTopOverlay = null;
    layoutActionRail = null;
    layoutBottomMeta = null;
    progressContainer = null;
    tvTitle = null;
    tvTag = null;
    tvLikeCount = null;
    layoutLoading = null;
    tvEmpty = null;
    layoutError = null;
    adapter = null;
    if (preloaderExecutor != null) {
      preloaderExecutor.shutdownNow();
      preloaderExecutor = null;
    }
    progressBars.clear();
    super.onDestroyView();
  }

  private void logDebug(@NonNull String message) {
    Log.d(TAG, message);
  }
}
