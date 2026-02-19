package com.example.test.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import com.example.test.R;
import com.example.test.fragment.dialoguelearning.model.SummaryData;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Binds SummaryData to the Summary Fragment UI. */
public class SessionSummaryBinder {

  public static void bind(View rootView, SummaryData data) {
    if (data == null) return;

    // 1. Score
    TextView tvScore = rootView.findViewById(R.id.tv_total_score);
    tvScore.setText(String.valueOf(data.getTotalScore()));

    // 2. Highlights
    bindHighlights(rootView, data.getHighlights());

    // 3. Expressions
    bindExpressions(rootView, data.getExpressions());

    // 4. Liked Sentences
    bindSentences(rootView, data.getLikedSentences());

    // 5. Future Self Feedback
    bindFutureSelfFeedback(rootView, data.getFutureSelfFeedback());
  }

  private static boolean isBlank(String text) {
    return text == null || text.trim().isEmpty();
  }

  public static void bindFutureSelfFeedback(
      View rootView, SummaryData.FutureSelfFeedback feedback) {
    MaterialCardView feedbackCard = rootView.findViewById(R.id.card_future_feedback_content);
    View positiveBlock = rootView.findViewById(R.id.layout_feedback_positive_block);
    TextView positiveValue = rootView.findViewById(R.id.tv_feedback_positive_value);
    View improveBlock = rootView.findViewById(R.id.layout_feedback_improve_block);
    TextView improveValue = rootView.findViewById(R.id.tv_feedback_improve_value);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_future_feedback);
    TextView errorText = rootView.findViewById(R.id.tv_future_feedback_error);

    if (skeleton != null) {
      skeleton.stopShimmer();
      skeleton.setVisibility(View.GONE);
    }
    if (errorText != null) {
      errorText.setVisibility(View.GONE);
    }
    if (feedbackCard != null) {
      feedbackCard.setVisibility(View.VISIBLE);
    }
    if (positiveBlock == null
        || improveBlock == null
        || positiveValue == null
        || improveValue == null) {
      return;
    }

    if (feedback == null) {
      positiveBlock.setVisibility(View.GONE);
      improveBlock.setVisibility(View.GONE);
      return;
    }

    String positive = feedback.getPositive();
    String toImprove = feedback.getToImprove();

    if (isBlank(positive)) {
      positiveBlock.setVisibility(View.GONE);
    } else {
      positiveBlock.setVisibility(View.VISIBLE);
      positiveValue.setText(positive);
    }

    if (isBlank(toImprove)) {
      improveBlock.setVisibility(View.GONE);
    } else {
      improveBlock.setVisibility(View.VISIBLE);
      improveValue.setText(toImprove);
    }
  }

  public static void showFutureFeedbackLoading(View rootView) {
    MaterialCardView feedbackCard = rootView.findViewById(R.id.card_future_feedback_content);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_future_feedback);
    TextView errorText = rootView.findViewById(R.id.tv_future_feedback_error);

    if (feedbackCard != null) {
      feedbackCard.setVisibility(View.GONE);
    }
    if (errorText != null) {
      errorText.setVisibility(View.GONE);
    }
    if (skeleton != null) {
      skeleton.setVisibility(View.VISIBLE);
      skeleton.startShimmer();
    }
  }

  public static void showFutureFeedbackError(View rootView, String message) {
    MaterialCardView feedbackCard = rootView.findViewById(R.id.card_future_feedback_content);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_future_feedback);
    TextView errorText = rootView.findViewById(R.id.tv_future_feedback_error);

    if (feedbackCard != null) {
      feedbackCard.setVisibility(View.GONE);
    }
    if (skeleton != null) {
      skeleton.stopShimmer();
      skeleton.setVisibility(View.GONE);
    }
    if (errorText != null) {
      String finalMessage =
          isBlank(message)
              ? rootView.getContext().getString(R.string.summary_future_feedback_load_error)
              : message;
      errorText.setText(finalMessage);
      errorText.setVisibility(View.VISIBLE);
    }
  }

  private static void bindHighlights(View rootView, List<SummaryData.HighlightItem> items) {
    TextView titleView = rootView.findViewById(R.id.tv_summary_highlight_title);
    LinearLayout container = rootView.findViewById(R.id.layout_highlight_container);
    container.removeAllViews();
    if (items == null || items.isEmpty()) {
      if (titleView != null) {
        titleView.setVisibility(View.GONE);
      }
      container.setVisibility(View.GONE);
      return;
    }

    if (titleView != null) {
      titleView.setVisibility(View.VISIBLE);
    }
    container.setVisibility(View.VISIBLE);

    LayoutInflater inflater = LayoutInflater.from(rootView.getContext());
    for (SummaryData.HighlightItem item : items) {
      View itemView = inflater.inflate(R.layout.item_summary_highlight, container, false);
      ((TextView) itemView.findViewById(R.id.tv_highlight_english)).setText(item.getEnglish());
      ((TextView) itemView.findViewById(R.id.tv_highlight_korean)).setText(item.getKorean());
      ((TextView) itemView.findViewById(R.id.tv_highlight_reason)).setText(item.getReason());
      container.addView(itemView);
    }
  }

  private static void bindExpressions(View rootView, List<SummaryData.ExpressionItem> items) {
    LinearLayout container = rootView.findViewById(R.id.layout_expression_container);
    container.removeAllViews();
    if (items == null) return;

    LayoutInflater inflater = LayoutInflater.from(rootView.getContext());
    for (SummaryData.ExpressionItem item : items) {
      View itemView = inflater.inflate(R.layout.item_summary_expression, container, false);
      TextView expressionTypeTextView = itemView.findViewById(R.id.tv_expression_type);
      ((TextView) itemView.findViewById(R.id.tv_expression_prompt)).setText(item.getKoreanPrompt());
      ((TextView) itemView.findViewById(R.id.tv_expression_before)).setText(item.getBefore());
      TextView afterTextView = itemView.findViewById(R.id.tv_expression_after);
      ((TextView) itemView.findViewById(R.id.tv_expression_explanation))
          .setText(item.getExplanation());
      boolean isPreciseType =
          applyExpressionTheme(itemView, expressionTypeTextView, item.getType());
      if (afterTextView != null) {
        afterTextView.setText(buildHighlightedAfterText(itemView, item, isPreciseType));
      }
      bindSaveButton(itemView, R.id.btn_save_expression);
      container.addView(itemView);
    }
  }

  private static boolean applyExpressionTheme(
      View itemView, TextView expressionTypeTextView, String expressionType) {
    String normalizedType = normalizeExpressionType(expressionType);
    boolean isPreciseType = isPreciseExpressionType(normalizedType);
    ImageView naturalIcon = itemView.findViewById(R.id.iv_expression_type_natural);
    ImageView preciseIcon = itemView.findViewById(R.id.iv_expression_type_precise);
    TextView afterLabel = itemView.findViewById(R.id.tv_expression_after_label);
    MaterialCardView afterCard = itemView.findViewById(R.id.card_expression_after);
    if (naturalIcon != null) {
      naturalIcon.setVisibility(isPreciseType ? View.GONE : View.VISIBLE);
    }
    if (preciseIcon != null) {
      preciseIcon.setVisibility(isPreciseType ? View.VISIBLE : View.GONE);
    }
    if (expressionTypeTextView != null) {
      expressionTypeTextView.setText(normalizedType);
      int typeColorRes =
          isPreciseType ? R.color.expression_precise_accent : R.color.expression_natural_accent;
      expressionTypeTextView.setTextColor(
          ContextCompat.getColor(itemView.getContext(), typeColorRes));
    }
    if (afterLabel != null) {
      int labelColorRes =
          isPreciseType ? R.color.expression_precise_accent : R.color.expression_natural_accent;
      afterLabel.setTextColor(ContextCompat.getColor(itemView.getContext(), labelColorRes));
    }
    if (afterCard != null) {
      int bgColorRes =
          isPreciseType ? R.color.expression_precise_after_bg : R.color.expression_natural_after_bg;
      afterCard.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), bgColorRes));
    }
    return isPreciseType;
  }

  private static String normalizeExpressionType(String expressionType) {
    if (expressionType == null || expressionType.trim().isEmpty()) {
      return "\uC790\uC5F0\uC2A4\uB7EC\uC6B4 \uD45C\uD604";
    }
    return expressionType.trim();
  }

  private static boolean isPreciseExpressionType(String expressionType) {
    return expressionType.contains("\uC815\uD655")
        || expressionType.toLowerCase().contains("precise");
  }

  private static CharSequence buildHighlightedAfterText(
      View itemView, SummaryData.ExpressionItem item, boolean isPreciseType) {
    if (item == null) {
      return "";
    }
    String after = trimToNull(item.getAfter());
    if (after == null) {
      return "";
    }

    List<String> highlightPhrases = new ArrayList<>();
    if (item.getAfterHighlights() != null) {
      for (String phrase : item.getAfterHighlights()) {
        addUniquePhrase(highlightPhrases, phrase);
      }
    }
    if (highlightPhrases.isEmpty()) {
      String before = trimToNull(item.getBefore());
      if (before != null) {
        addUniquePhrase(highlightPhrases, inferDiffPhrase(before, after));
      }
    }
    if (highlightPhrases.isEmpty()) {
      return after;
    }

    SpannableStringBuilder builder = new SpannableStringBuilder(after);
    int accentRes =
        isPreciseType ? R.color.expression_precise_accent : R.color.expression_natural_accent;
    int accentColor = ContextCompat.getColor(itemView.getContext(), accentRes);
    int highlightBgColor = (accentColor & 0x00FFFFFF) | 0x33000000;
    for (String phrase : highlightPhrases) {
      applyPhraseHighlight(builder, after, phrase, highlightBgColor);
    }
    return builder;
  }

  private static void applyPhraseHighlight(
      SpannableStringBuilder builder, String fullText, String phrase, int highlightBgColor) {
    String target = trimToNull(phrase);
    if (target == null) {
      return;
    }
    String textLower = fullText.toLowerCase();
    String targetLower = target.toLowerCase();
    int searchStart = 0;
    while (true) {
      int start = textLower.indexOf(targetLower, searchStart);
      if (start < 0) {
        break;
      }
      int end = start + target.length();
      builder.setSpan(
          new BackgroundColorSpan(highlightBgColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      searchStart = end;
    }
  }

  private static String inferDiffPhrase(String before, String after) {
    Set<String> beforeTokens = new HashSet<>();
    for (String token : before.split("\\s+")) {
      String normalized = normalizeToken(token);
      if (normalized != null) {
        beforeTokens.add(normalized);
      }
    }

    String best = null;
    StringBuilder current = new StringBuilder();
    for (String token : after.split("\\s+")) {
      String normalized = normalizeToken(token);
      boolean changed = normalized == null || !beforeTokens.contains(normalized);
      if (changed) {
        if (current.length() > 0) {
          current.append(' ');
        }
        current.append(token);
      } else if (current.length() > 0) {
        String phrase = trimToNull(current.toString());
        if (phrase != null && (best == null || phrase.length() > best.length())) {
          best = phrase;
        }
        current.setLength(0);
      }
    }
    if (current.length() > 0) {
      String phrase = trimToNull(current.toString());
      if (phrase != null && (best == null || phrase.length() > best.length())) {
        best = phrase;
      }
    }
    return best;
  }

  private static String normalizeToken(String token) {
    String trimmed = trimToNull(token);
    if (trimmed == null) {
      return null;
    }
    String stripped = trimmed.replaceAll("^[^\\p{L}\\p{N}']+|[^\\p{L}\\p{N}']+$", "");
    return stripped.isEmpty() ? null : stripped.toLowerCase();
  }

  private static void addUniquePhrase(List<String> phrases, String phrase) {
    String trimmed = trimToNull(phrase);
    if (trimmed == null) {
      return;
    }
    String normalized = trimmed.toLowerCase();
    for (String existing : phrases) {
      if (existing != null && existing.trim().toLowerCase().equals(normalized)) {
        return;
      }
    }
    phrases.add(trimmed);
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public static void bindWordsContent(View rootView, List<SummaryData.WordItem> items) {
    LinearLayout container = rootView.findViewById(R.id.layout_word_container);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_summary_words);
    TextView errorText = rootView.findViewById(R.id.tv_summary_words_error);
    if (container == null) {
      return;
    }

    if (skeleton != null) {
      skeleton.stopShimmer();
      skeleton.setVisibility(View.GONE);
    }
    if (errorText != null) {
      errorText.setVisibility(View.GONE);
    }

    container.removeAllViews();
    if (items == null || items.isEmpty()) {
      container.setVisibility(View.GONE);
      return;
    }
    container.setVisibility(View.VISIBLE);

    LayoutInflater inflater = LayoutInflater.from(rootView.getContext());
    for (SummaryData.WordItem item : items) {
      View itemView = inflater.inflate(R.layout.item_summary_word, container, false);
      ((TextView) itemView.findViewById(R.id.tv_summary_word_en)).setText(item.getEnglish());
      ((TextView) itemView.findViewById(R.id.tv_summary_word_ko)).setText(item.getKorean());
      ((TextView) itemView.findViewById(R.id.tv_summary_word_example))
          .setText(item.getExampleEnglish());
      ((TextView) itemView.findViewById(R.id.tv_summary_word_example_ko))
          .setText(item.getExampleKorean());
      bindSaveButton(itemView, R.id.btn_save_word);
      container.addView(itemView);
    }
  }

  public static void showWordsLoading(View rootView) {
    LinearLayout container = rootView.findViewById(R.id.layout_word_container);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_summary_words);
    TextView errorText = rootView.findViewById(R.id.tv_summary_words_error);

    if (container != null) {
      container.removeAllViews();
      container.setVisibility(View.GONE);
    }
    if (errorText != null) {
      errorText.setVisibility(View.GONE);
    }
    if (skeleton != null) {
      skeleton.setVisibility(View.VISIBLE);
      skeleton.startShimmer();
    }
  }

  public static void showWordsError(View rootView, String message) {
    LinearLayout container = rootView.findViewById(R.id.layout_word_container);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_summary_words);
    TextView errorText = rootView.findViewById(R.id.tv_summary_words_error);

    if (container != null) {
      container.removeAllViews();
      container.setVisibility(View.GONE);
    }
    if (skeleton != null) {
      skeleton.stopShimmer();
      skeleton.setVisibility(View.GONE);
    }
    if (errorText != null) {
      String finalMessage =
          isBlank(message)
              ? rootView.getContext().getString(R.string.summary_words_load_error)
              : message;
      errorText.setText(finalMessage);
      errorText.setVisibility(View.VISIBLE);
    }
  }

  private static void bindSentences(View rootView, List<SummaryData.SentenceItem> items) {
    TextView titleView = rootView.findViewById(R.id.tv_summary_sentence_title);
    LinearLayout container = rootView.findViewById(R.id.layout_sentence_container);
    TextView emptyView = rootView.findViewById(R.id.tv_summary_sentence_empty);
    container.removeAllViews();
    if (items == null || items.isEmpty()) {
      if (titleView != null) {
        titleView.setVisibility(View.GONE);
      }
      container.setVisibility(View.GONE);
      if (emptyView != null) {
        emptyView.setVisibility(View.GONE);
      }
      return;
    }
    if (titleView != null) {
      titleView.setVisibility(View.VISIBLE);
    }
    container.setVisibility(View.VISIBLE);
    if (emptyView != null) {
      emptyView.setVisibility(View.GONE);
    }

    LayoutInflater inflater = LayoutInflater.from(rootView.getContext());
    for (SummaryData.SentenceItem item : items) {
      View itemView = inflater.inflate(R.layout.item_summary_sentence, container, false);
      ((TextView) itemView.findViewById(R.id.tv_summary_sentence_en)).setText(item.getEnglish());
      ((TextView) itemView.findViewById(R.id.tv_summary_sentence_ko)).setText(item.getKorean());
      bindSaveButton(itemView, R.id.btn_save_sentence);
      container.addView(itemView);
    }
  }

  private static void bindSaveButton(View itemView, int buttonResId) {
    ImageButton saveButton = itemView.findViewById(buttonResId);
    if (saveButton == null) {
      return;
    }

    updateSaveButtonState(saveButton, false);
    updateSaveStatusLabel(itemView, false);
    updateSavedCardBorder(itemView, false);

    Runnable toggleSaveState =
        () -> {
          playSaveWaveAnimation(itemView, saveButton);
          boolean isSaved = !saveButton.isSelected();
          saveButton.setSelected(isSaved);
          updateSaveButtonState(saveButton, isSaved);
          updateSaveStatusLabel(itemView, isSaved);
          updateSavedCardBorder(itemView, isSaved);
        };

    saveButton.setOnClickListener(v -> toggleSaveState.run());
    itemView.setOnClickListener(v -> toggleSaveState.run());
  }

  private static void updateSaveButtonState(ImageButton saveButton, boolean isSaved) {
    int iconRes = isSaved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_border;
    int tintColorRes = isSaved ? R.color.save_icon_gold : R.color.grey_400;
    int tintColor = ContextCompat.getColor(saveButton.getContext(), tintColorRes);
    saveButton.setImageResource(iconRes);
    ImageViewCompat.setImageTintList(saveButton, ColorStateList.valueOf(tintColor));
  }

  private static void updateSaveStatusLabel(View itemView, boolean isSaved) {
    TextView saveStatusText = itemView.findViewById(R.id.tv_save_status);
    if (saveStatusText == null) {
      return;
    }
    saveStatusText.setVisibility(isSaved ? View.VISIBLE : View.GONE);
  }

  private static void updateSavedCardBorder(View itemView, boolean isSaved) {
    if (!(itemView instanceof MaterialCardView)) {
      return;
    }

    MaterialCardView cardView = (MaterialCardView) itemView;
    int strokeWidthPx = isSaved ? dpToPx(itemView, 2) : 0;
    int strokeColor = ContextCompat.getColor(itemView.getContext(), R.color.save_icon_gold);
    cardView.setStrokeWidth(strokeWidthPx);
    cardView.setStrokeColor(strokeColor);
  }

  private static int dpToPx(View view, int dp) {
    float density = view.getResources().getDisplayMetrics().density;
    return (int) (dp * density);
  }

  private static void playSaveWaveAnimation(View cardView, View sourceView) {
    int[] cardLocation = new int[2];
    int[] sourceLocation = new int[2];
    cardView.getLocationOnScreen(cardLocation);
    sourceView.getLocationOnScreen(sourceLocation);

    final int centerX = sourceLocation[0] - cardLocation[0] + sourceView.getWidth() / 2;
    final int centerY = sourceLocation[1] - cardLocation[1] + sourceView.getHeight() / 2;

    final float maxRadius = (float) Math.hypot(cardView.getWidth(), cardView.getHeight());
    final int baseColor = ContextCompat.getColor(cardView.getContext(), R.color.save_icon_gold);

    final android.graphics.drawable.GradientDrawable cardFlash =
        new android.graphics.drawable.GradientDrawable();
    cardFlash.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
    cardFlash.setColor(baseColor);
    cardFlash.setAlpha(0);
    cardFlash.setBounds(0, 0, cardView.getWidth(), cardView.getHeight());

    final android.graphics.drawable.GradientDrawable wave =
        new android.graphics.drawable.GradientDrawable();
    wave.setShape(android.graphics.drawable.GradientDrawable.OVAL);
    wave.setColor(baseColor);
    wave.setAlpha(0);

    cardView.getOverlay().add(cardFlash);
    cardView.getOverlay().add(wave);

    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(680L);
    animator.setInterpolator(new DecelerateInterpolator());
    animator.addUpdateListener(
        animation -> {
          float progress = (float) animation.getAnimatedValue();
          float radius = maxRadius * progress;
          int waveAlpha = (int) (88 * (1f - progress));
          int cardAlpha = (int) (42 * (1f - progress));
          wave.setAlpha(Math.max(0, waveAlpha));
          cardFlash.setAlpha(Math.max(0, cardAlpha));
          wave.setBounds(
              (int) (centerX - radius),
              (int) (centerY - radius),
              (int) (centerX + radius),
              (int) (centerY + radius));
        });

    ObjectAnimator pressX = ObjectAnimator.ofFloat(cardView, View.SCALE_X, 1f, 0.985f, 1f);
    ObjectAnimator pressY = ObjectAnimator.ofFloat(cardView, View.SCALE_Y, 1f, 0.985f, 1f);
    pressX.setDuration(260L);
    pressY.setDuration(260L);
    pressX.setInterpolator(new DecelerateInterpolator());
    pressY.setInterpolator(new DecelerateInterpolator());

    AnimatorSet pressSet = new AnimatorSet();
    pressSet.playTogether(pressX, pressY);

    AnimatorSet fullSet = new AnimatorSet();
    fullSet.playTogether(animator, pressSet);
    fullSet.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            cardView.setScaleX(1f);
            cardView.setScaleY(1f);
            cardView.getOverlay().remove(wave);
            cardView.getOverlay().remove(cardFlash);
          }
        });

    fullSet.start();
  }

  public static SummaryData createDummyData() {
    SummaryData data = new SummaryData();
    data.setTotalScore(95);

    data.setHighlights(
        Arrays.asList(
            new SummaryData.HighlightItem(
                "I was wondering if you could help me with the project.",
                "프로젝트를 도와주실 수 있을지 여쭤봤어요.",
                "완곡한 표현인 'I was wondering if'를 사용해 정중하게 요청한 점이 좋았습니다.")));

    data.setExpressions(
        Arrays.asList(
            new SummaryData.ExpressionItem(
                "자연스러운 표현",
                "이 문장을 영어로 자연스럽게 바꿔보세요.",
                "Before: I am very waiting for the result.",
                "After: I'm really looking forward to the result.",
                "'wait for'보다 'look forward to'가 실제 대화에서 더 자연스럽게 들립니다."),
            new SummaryData.ExpressionItem(
                "정확한 표현",
                "의미가 정확하게 전달되도록 문장을 다듬어보세요.",
                "Before: The data are not enough reliable.",
                "After: The data are not sufficiently reliable.",
                "'enough reliable'은 문법적으로 어색하므로 'sufficiently reliable'로 바꾸는 것이 정확합니다.")));

    data.setWords(
        Arrays.asList(
            new SummaryData.WordItem(
                "Favorable",
                "유리한, 호의적인",
                "The conditions were favorable for the experiment.",
                "그 조건들은 실험에 유리했습니다."),
            new SummaryData.WordItem(
                "Incorporate",
                "포함하다, 통합하다",
                "We should incorporate your ideas into the plan.",
                "우리는 당신의 아이디어를 계획에 포함시켜야 합니다.")));

    data.setLikedSentences(
        Arrays.asList(
            new SummaryData.SentenceItem(
                "It's not just a matter of time; it's about priorities.",
                "그건 단순히 시간의 문제가 아니라 우선순위의 문제입니다."),
            new SummaryData.SentenceItem(
                "Small steps every day lead to big results in the long run.",
                "매일의 작은 실천이 장기적으로 큰 결과를 만듭니다.")));

    data.setFutureSelfFeedback(
        new SummaryData.FutureSelfFeedback(
            "예의 있는 표현을 선택하는 능력이 좋아졌고, 문장 흐름도 훨씬 안정적입니다.",
            "복합 문장에서 수일치와 부사 위치만 조금 더 점검하면 완성도가 크게 올라갑니다."));

    return data;
  }
}
