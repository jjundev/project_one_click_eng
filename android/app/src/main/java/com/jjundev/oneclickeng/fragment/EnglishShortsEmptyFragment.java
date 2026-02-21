package com.jjundev.oneclickeng.fragment;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.others.EnglishShortsItem;
import com.jjundev.oneclickeng.others.EnglishShortsPagerAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EnglishShortsEmptyFragment extends Fragment {
  private static final String TAG = "JOB_J-20260216-006";

  @NonNull private final List<View> progressBars = new ArrayList<>();
  @NonNull private List<EnglishShortsItem> shortsItems = new ArrayList<>();

  @Nullable private ViewPager2 viewPager;
  @Nullable private LinearLayout layoutTopOverlay;
  @Nullable private LinearLayout layoutActionRail;
  @Nullable private LinearLayout layoutBottomMeta;
  @Nullable private LinearLayout progressContainer;
  @Nullable private TextView tvCreatorHandle;
  @Nullable private TextView tvCaptionTitle;
  @Nullable private TextView tvCaptionSubtitle;
  @Nullable private TextView tvHashtags;
  @Nullable private TextView tvLikeCount;
  @Nullable private TextView tvBookmarkCount;
  @Nullable private TextView tvShareCount;
  @Nullable private ViewPager2.OnPageChangeCallback pageChangeCallback;

  private int topOverlayBasePaddingTop;
  private int actionRailBaseBottomMargin;
  private int bottomMetaBaseBottomMargin;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_english_shorts_empty, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    bindViews(view);
    shortsItems = buildMockItems();
    setupProgressIndicators();
    setupViewPager();
    setupActionButtons(view);
    setupEdgeToEdgeInsets(view);
    if (!shortsItems.isEmpty()) {
      renderOverlay(shortsItems.get(0), 0);
    }
    logDebug("English Shorts fragment initialized. itemCount=" + shortsItems.size());
  }

  private void bindViews(@NonNull View root) {
    viewPager = root.findViewById(R.id.vp_english_shorts);
    layoutTopOverlay = root.findViewById(R.id.layout_shorts_top_overlay);
    layoutActionRail = root.findViewById(R.id.layout_shorts_action_rail);
    layoutBottomMeta = root.findViewById(R.id.layout_shorts_bottom_meta);
    progressContainer = root.findViewById(R.id.layout_shorts_progress);
    tvCreatorHandle = root.findViewById(R.id.tv_shorts_creator_handle);
    tvCaptionTitle = root.findViewById(R.id.tv_shorts_caption_title);
    tvCaptionSubtitle = root.findViewById(R.id.tv_shorts_caption_subtitle);
    tvHashtags = root.findViewById(R.id.tv_shorts_hashtags);
    tvLikeCount = root.findViewById(R.id.tv_shorts_like_count);
    tvBookmarkCount = root.findViewById(R.id.tv_shorts_bookmark_count);
    tvShareCount = root.findViewById(R.id.tv_shorts_share_count);
  }

  private void setupViewPager() {
    ViewPager2 pager = viewPager;
    if (pager == null) {
      return;
    }
    pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
    pager.setOffscreenPageLimit(1);
    pager.setAdapter(new EnglishShortsPagerAdapter(shortsItems));
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
            if (position < 0 || position >= shortsItems.size()) {
              return;
            }
            renderOverlay(shortsItems.get(position), position);
            logDebug("Short page changed: index=" + position);
          }
        };
    pager.registerOnPageChangeCallback(pageChangeCallback);
  }

  private void setupActionButtons(@NonNull View root) {
    bindActionButton(
        root,
        R.id.btn_shorts_like,
        R.string.english_shorts_action_like_toast,
        "like",
        R.string.english_shorts_action_like_content_desc);
    bindActionButton(
        root,
        R.id.btn_shorts_bookmark,
        R.string.english_shorts_action_bookmark_toast,
        "bookmark",
        R.string.english_shorts_action_bookmark_content_desc);
    bindActionButton(
        root,
        R.id.btn_shorts_share,
        R.string.english_shorts_action_share_toast,
        "share",
        R.string.english_shorts_action_share_content_desc);
  }

  private void bindActionButton(
      @NonNull View root,
      @IdRes int buttonId,
      @StringRes int toastMessageRes,
      @NonNull String actionName,
      @StringRes int contentDescriptionRes) {
    View button = root.findViewById(buttonId);
    button.setContentDescription(getString(contentDescriptionRes));
    button.setOnClickListener(
        view -> {
          Toast.makeText(requireContext(), getString(toastMessageRes), Toast.LENGTH_SHORT).show();
          int currentIndex = viewPager == null ? 0 : viewPager.getCurrentItem();
          logDebug("Action tapped: " + actionName + ", index=" + currentIndex);
        });
  }

  private void setupProgressIndicators() {
    LinearLayout container = progressContainer;
    if (container == null) {
      return;
    }
    container.removeAllViews();
    progressBars.clear();
    for (int i = 0; i < shortsItems.size(); i++) {
      View progressBar = new View(requireContext());
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(4), 1f);
      if (i > 0) {
        params.setMarginStart(dp(5));
      }
      progressBar.setLayoutParams(params);
      container.addView(progressBar);
      progressBars.add(progressBar);
    }
    updateProgressIndicators(0);
  }

  private void renderOverlay(@NonNull EnglishShortsItem item, int index) {
    if (tvCreatorHandle != null) {
      tvCreatorHandle.setText(item.getCreatorHandle());
    }
    if (tvCaptionTitle != null) {
      tvCaptionTitle.setText(item.getCaptionTitle());
    }
    if (tvCaptionSubtitle != null) {
      tvCaptionSubtitle.setText(item.getCaptionSubtitle());
    }
    if (tvHashtags != null) {
      tvHashtags.setText(item.getHashtagLine());
    }
    if (tvLikeCount != null) {
      tvLikeCount.setText(formatCount(item.getLikeCount()));
    }
    if (tvBookmarkCount != null) {
      tvBookmarkCount.setText(formatCount(item.getBookmarkCount()));
    }
    if (tvShareCount != null) {
      tvShareCount.setText(formatCount(item.getShareCount()));
    }
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
      if (i < currentIndex) {
        bar.setAlpha(0.8f);
      } else if (i == currentIndex) {
        bar.setAlpha(1f);
      } else {
        bar.setAlpha(0.42f);
      }
    }
  }

  private void setupEdgeToEdgeInsets(@NonNull View root) {
    LinearLayout topOverlay = layoutTopOverlay;
    LinearLayout actionRail = layoutActionRail;
    LinearLayout bottomMeta = layoutBottomMeta;
    if (topOverlay == null || actionRail == null || bottomMeta == null) {
      return;
    }

    topOverlayBasePaddingTop = topOverlay.getPaddingTop();
    ViewGroup.MarginLayoutParams actionParams =
        (ViewGroup.MarginLayoutParams) actionRail.getLayoutParams();
    actionRailBaseBottomMargin = actionParams.bottomMargin;
    ViewGroup.MarginLayoutParams bottomMetaParams =
        (ViewGroup.MarginLayoutParams) bottomMeta.getLayoutParams();
    bottomMetaBaseBottomMargin = bottomMetaParams.bottomMargin;

    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (view, insets) -> {
          Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          topOverlay.setPadding(
              topOverlay.getPaddingLeft(),
              topOverlayBasePaddingTop + systemBars.top,
              topOverlay.getPaddingRight(),
              topOverlay.getPaddingBottom());

          ViewGroup.MarginLayoutParams currentActionParams =
              (ViewGroup.MarginLayoutParams) actionRail.getLayoutParams();
          currentActionParams.bottomMargin = actionRailBaseBottomMargin + systemBars.bottom;
          actionRail.setLayoutParams(currentActionParams);

          ViewGroup.MarginLayoutParams currentMetaParams =
              (ViewGroup.MarginLayoutParams) bottomMeta.getLayoutParams();
          currentMetaParams.bottomMargin = bottomMetaBaseBottomMargin + systemBars.bottom;
          bottomMeta.setLayoutParams(currentMetaParams);
          return insets;
        });
    ViewCompat.requestApplyInsets(root);
  }

  @NonNull
  private List<EnglishShortsItem> buildMockItems() {
    List<EnglishShortsItem> items = new ArrayList<>();
    items.add(
        new EnglishShortsItem(
            "@coachMina",
            "3-second hotel check-in line",
            "Say this once and own the lobby tone.",
            "#travel #checkin #smalltalk",
            "TRAVEL TUNE",
            "I'd like to check in, please.",
            "Stress the word: check IN",
            "Shadow this sentence in one breath",
            18400,
            3100,
            860,
            Color.parseColor("#1A2A6C"),
            Color.parseColor("#0B1229"),
            Color.parseColor("#38BDF8")));
    items.add(
        new EnglishShortsItem(
            "@speakFlow",
            "Coffee order with no awkward pause",
            "Use a friendly rise-fall rhythm.",
            "#cafe #dailyenglish #fluency",
            "CITY SNAP",
            "Can I get an iced latte with oat milk?",
            "Connect: latte-with-oat",
            "Repeat at 0.9x speed, then full speed",
            22600,
            4700,
            1250,
            Color.parseColor("#42275A"),
            Color.parseColor("#1A1130"),
            Color.parseColor("#F59E0B")));
    items.add(
        new EnglishShortsItem(
            "@lineBuilder",
            "Meeting opener that sounds prepared",
            "Start calm, then add confidence.",
            "#officeenglish #meeting #career",
            "PRO MODE",
            "Let's quickly align on today's priorities.",
            "Pause after quickly align",
            "Match the rhythm with your fingertap",
            31200,
            5400,
            1670,
            Color.parseColor("#134E5E"),
            Color.parseColor("#081C24"),
            Color.parseColor("#34D399")));
    items.add(
        new EnglishShortsItem(
            "@dialogueLab",
            "Polite disagreement without sounding cold",
            "Keep your tone soft on the first clause.",
            "#negotiation #businessenglish #tone",
            "TACTIC DROP",
            "I see your point, but I'd approach it differently.",
            "Lift tone on: I see your point",
            "Practice once with a warm smile",
            27900,
            6200,
            1910,
            Color.parseColor("#2C3E50"),
            Color.parseColor("#101820"),
            Color.parseColor("#A78BFA")));
    return items;
  }

  @NonNull
  private String formatCount(int count) {
    if (count >= 1_000_000) {
      return String.format(Locale.US, "%.1fM", count / 1_000_000f);
    }
    if (count >= 1_000) {
      return String.format(Locale.US, "%.1fK", count / 1_000f);
    }
    return String.valueOf(count);
  }

  private int dp(int dp) {
    return Math.round(dp * getResources().getDisplayMetrics().density);
  }

  @Override
  public void onDestroyView() {
    if (viewPager != null && pageChangeCallback != null) {
      viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
    }
    pageChangeCallback = null;
    viewPager = null;
    layoutTopOverlay = null;
    layoutActionRail = null;
    layoutBottomMeta = null;
    progressContainer = null;
    tvCreatorHandle = null;
    tvCaptionTitle = null;
    tvCaptionSubtitle = null;
    tvHashtags = null;
    tvLikeCount = null;
    tvBookmarkCount = null;
    tvShareCount = null;
    progressBars.clear();
    super.onDestroyView();
  }

  private void logDebug(@NonNull String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }
}
