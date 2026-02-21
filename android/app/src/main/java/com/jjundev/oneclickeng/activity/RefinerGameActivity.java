package com.jjundev.oneclickeng.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.game.refiner.RefinerConstraintValidator;
import com.jjundev.oneclickeng.game.refiner.RefinerGameViewModel;
import com.jjundev.oneclickeng.game.refiner.RefinerGameViewModelFactory;
import com.jjundev.oneclickeng.game.refiner.RefinerStatsStore;
import com.jjundev.oneclickeng.game.refiner.model.RefinerConstraints;
import com.jjundev.oneclickeng.game.refiner.model.RefinerDifficulty;
import com.jjundev.oneclickeng.game.refiner.model.RefinerEvaluation;
import com.jjundev.oneclickeng.game.refiner.model.RefinerLevel;
import com.jjundev.oneclickeng.game.refiner.model.RefinerLevelExample;
import com.jjundev.oneclickeng.game.refiner.model.RefinerQuestion;
import com.jjundev.oneclickeng.game.refiner.model.RefinerRoundResult;
import com.jjundev.oneclickeng.game.refiner.model.RefinerWordLimit;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RefinerGameActivity extends AppCompatActivity {
  @Nullable private RefinerGameViewModel viewModel;
  @NonNull
  private RefinerConstraintValidator.ValidationResult currentValidation =
      RefinerConstraintValidator.validate(new RefinerConstraints(null, null, null), "");

  @Nullable private View layoutLoading;
  @Nullable private View layoutError;
  @Nullable private View layoutContent;
  @Nullable private View layoutCompleted;

  @Nullable private TextView tvLoadingMessage;
  @Nullable private TextView tvErrorMessage;
  @Nullable private View btnRetry;

  @Nullable private TextView tvProgress;
  @Nullable private TextView tvScore;
  @Nullable private TextView tvDifficulty;
  @Nullable private TextView tvSourceSentence;
  @Nullable private TextView tvContext;
  @Nullable private TextView tvConstraints;
  @Nullable private TextView tvWordCount;
  @Nullable private TextInputEditText etAnswerInput;
  @Nullable private View btnHint;
  @Nullable private TextView tvHint;
  @Nullable private View btnSubmit;

  @Nullable private View cardFeedback;
  @Nullable private TextView tvFeedbackLevel;
  @Nullable private TextView tvFeedbackSentence;
  @Nullable private TextView tvFeedbackLexical;
  @Nullable private TextView tvFeedbackSyntax;
  @Nullable private TextView tvFeedbackNaturalness;
  @Nullable private TextView tvFeedbackCompliance;
  @Nullable private ProgressBar progressLexical;
  @Nullable private ProgressBar progressSyntax;
  @Nullable private ProgressBar progressNaturalness;
  @Nullable private ProgressBar progressCompliance;
  @Nullable private TextView tvQuestionScore;
  @Nullable private TextView tvFeedbackBonus;
  @Nullable private LinearLayout examplesContainer;
  @Nullable private TextView tvInsight;
  @Nullable private View btnNext;

  @Nullable private TextView tvCompletedTotalScore;
  @Nullable private TextView tvCompletedLexicalAvg;
  @Nullable private TextView tvCompletedSyntaxAvg;
  @Nullable private TextView tvCompletedNaturalnessAvg;
  @Nullable private TextView tvCompletedComplianceAvg;
  @Nullable private View btnFinish;

  @NonNull private String renderedQuestionSignature = "";

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_refiner_game);

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                showExitConfirmDialog();
              }
            });

    bindViews();
    initViewModel();
    bindListeners();
    showDifficultyDialog();
  }

  private void bindViews() {
    layoutLoading = findViewById(R.id.layout_refiner_loading);
    layoutError = findViewById(R.id.layout_refiner_error);
    layoutContent = findViewById(R.id.layout_refiner_content);
    layoutCompleted = findViewById(R.id.layout_refiner_completed);

    tvLoadingMessage = findViewById(R.id.tv_refiner_loading_message);
    tvErrorMessage = findViewById(R.id.tv_refiner_error_message);
    btnRetry = findViewById(R.id.btn_refiner_retry);

    tvProgress = findViewById(R.id.tv_refiner_progress);
    tvScore = findViewById(R.id.tv_refiner_score);
    tvDifficulty = findViewById(R.id.tv_refiner_difficulty);
    tvSourceSentence = findViewById(R.id.tv_refiner_source_sentence);
    tvContext = findViewById(R.id.tv_refiner_context);
    tvConstraints = findViewById(R.id.tv_refiner_constraints);
    tvWordCount = findViewById(R.id.tv_refiner_word_count);
    etAnswerInput = findViewById(R.id.et_refiner_answer);
    btnHint = findViewById(R.id.btn_refiner_hint);
    tvHint = findViewById(R.id.tv_refiner_hint);
    btnSubmit = findViewById(R.id.btn_refiner_submit);

    cardFeedback = findViewById(R.id.card_refiner_feedback);
    tvFeedbackLevel = findViewById(R.id.tv_refiner_feedback_level);
    tvFeedbackSentence = findViewById(R.id.tv_refiner_feedback_sentence);
    tvFeedbackLexical = findViewById(R.id.tv_refiner_feedback_lexical);
    tvFeedbackSyntax = findViewById(R.id.tv_refiner_feedback_syntax);
    tvFeedbackNaturalness = findViewById(R.id.tv_refiner_feedback_naturalness);
    tvFeedbackCompliance = findViewById(R.id.tv_refiner_feedback_compliance);
    progressLexical = findViewById(R.id.progress_refiner_lexical);
    progressSyntax = findViewById(R.id.progress_refiner_syntax);
    progressNaturalness = findViewById(R.id.progress_refiner_naturalness);
    progressCompliance = findViewById(R.id.progress_refiner_compliance);
    tvQuestionScore = findViewById(R.id.tv_refiner_question_score);
    tvFeedbackBonus = findViewById(R.id.tv_refiner_feedback_bonus);
    examplesContainer = findViewById(R.id.layout_refiner_examples_container);
    tvInsight = findViewById(R.id.tv_refiner_insight);
    btnNext = findViewById(R.id.btn_refiner_next);

    tvCompletedTotalScore = findViewById(R.id.tv_refiner_completed_total_score);
    tvCompletedLexicalAvg = findViewById(R.id.tv_refiner_completed_lexical_avg);
    tvCompletedSyntaxAvg = findViewById(R.id.tv_refiner_completed_syntax_avg);
    tvCompletedNaturalnessAvg = findViewById(R.id.tv_refiner_completed_naturalness_avg);
    tvCompletedComplianceAvg = findViewById(R.id.tv_refiner_completed_compliance_avg);
    btnFinish = findViewById(R.id.btn_refiner_finish);
  }

  private void initViewModel() {
    AppSettings settings = new AppSettingsStore(getApplicationContext()).getSettings();
    RefinerGameViewModelFactory factory =
        new RefinerGameViewModelFactory(
            LearningDependencyProvider.provideRefinerGenerationManager(
                getApplicationContext(),
                settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
                settings.getLlmModelRefiner()),
            new RefinerStatsStore(getApplicationContext()));
    viewModel = new ViewModelProvider(this, factory).get(RefinerGameViewModel.class);
    viewModel.getUiState().observe(this, this::renderState);
  }

  private void bindListeners() {
    if (btnRetry != null) {
      btnRetry.setOnClickListener(
          v -> {
            RefinerGameViewModel vm = viewModel;
            if (vm != null) {
              vm.retry();
            }
          });
    }

    if (btnHint != null) {
      btnHint.setOnClickListener(
          v -> {
            RefinerGameViewModel vm = viewModel;
            if (vm != null) {
              vm.onHintRequested();
            }
          });
    }

    if (btnSubmit != null) {
      btnSubmit.setOnClickListener(
          v -> {
            RefinerGameViewModel vm = viewModel;
            EditText editText = etAnswerInput;
            if (vm == null || editText == null) {
              return;
            }
            RefinerGameViewModel.ActionResult result =
                vm.onSubmitSentence(editText.getText() == null ? "" : editText.getText().toString(),
                    currentValidation.isAllConstraintsSatisfied());
            if (result == RefinerGameViewModel.ActionResult.INVALID) {
              Toast.makeText(this, R.string.refiner_submit_invalid, Toast.LENGTH_SHORT).show();
            }
          });
    }

    if (btnNext != null) {
      btnNext.setOnClickListener(
          v -> {
            RefinerGameViewModel vm = viewModel;
            if (vm != null) {
              vm.onNextFromFeedback();
            }
          });
    }

    if (btnFinish != null) {
      btnFinish.setOnClickListener(v -> navigateToMainActivity());
    }

    if (etAnswerInput != null) {
      etAnswerInput.addTextChangedListener(
          new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
              refreshValidationUi();
            }

            @Override
            public void afterTextChanged(Editable s) {}
          });
    }
  }

  private void showDifficultyDialog() {
    String[] labels = {
      getString(R.string.refiner_difficulty_easy),
      getString(R.string.refiner_difficulty_normal),
      getString(R.string.refiner_difficulty_hard),
      getString(R.string.refiner_difficulty_expert)
    };
    RefinerDifficulty[] values = {
      RefinerDifficulty.EASY,
      RefinerDifficulty.NORMAL,
      RefinerDifficulty.HARD,
      RefinerDifficulty.EXPERT
    };
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.refiner_difficulty_dialog_title)
        .setCancelable(false)
        .setItems(
            labels,
            (dialog, which) -> {
              RefinerGameViewModel vm = viewModel;
              if (vm == null) {
                return;
              }
              int safeIndex = Math.max(0, Math.min(which, values.length - 1));
              vm.initialize(values[safeIndex]);
            })
        .show();
  }

  private void renderState(@NonNull RefinerGameViewModel.GameUiState state) {
    if (tvProgress != null) {
      tvProgress.setText(
          getString(
              R.string.refiner_progress_format,
              Math.min(state.getCurrentQuestionNumber(), state.getTotalQuestions()),
              state.getTotalQuestions()));
    }
    if (tvScore != null) {
      tvScore.setText(getString(R.string.refiner_score_format, state.getTotalScore()));
    }
    if (tvDifficulty != null) {
      tvDifficulty.setText(
          getString(R.string.refiner_difficulty_format, state.getDifficulty().name()));
    }

    switch (state.getStage()) {
      case LOADING:
        showOnly(layoutLoading);
        if (tvLoadingMessage != null) {
          String message = state.getLoadingMessage();
          tvLoadingMessage.setText(
              message == null || message.trim().isEmpty()
                  ? getString(R.string.refiner_loading_default)
                  : message);
        }
        break;
      case ERROR:
        showOnly(layoutError);
        if (tvErrorMessage != null) {
          String errorMessage = state.getErrorMessage();
          tvErrorMessage.setText(
              errorMessage == null || errorMessage.trim().isEmpty()
                  ? getString(R.string.refiner_error_default)
                  : errorMessage);
        }
        break;
      case ROUND_COMPLETED:
        showOnly(layoutCompleted);
        renderCompleted(state.getRoundResult());
        break;
      case ANSWERING:
      case EVALUATING:
      case FEEDBACK:
      default:
        showOnly(layoutContent);
        renderQuestionState(state);
        break;
    }
  }

  private void renderQuestionState(@NonNull RefinerGameViewModel.GameUiState state) {
    RefinerQuestion question = state.getQuestion();
    if (question == null) {
      return;
    }

    if (!question.signature().equals(renderedQuestionSignature)) {
      renderedQuestionSignature = question.signature();
      if (etAnswerInput != null) {
        etAnswerInput.setText("");
      }
      currentValidation = RefinerConstraintValidator.validate(question.getConstraints(), "");
    }

    if (tvSourceSentence != null) {
      tvSourceSentence.setText(question.getSourceSentence());
    }
    if (tvContext != null) {
      tvContext.setText(getString(R.string.refiner_context_format, question.getStyleContext()));
    }
    if (tvConstraints != null) {
      tvConstraints.setText(buildConstraintsText(question.getConstraints()));
    }

    if (btnHint != null) {
      btnHint.setVisibility(View.VISIBLE);
      btnHint.setEnabled(state.getStage() == RefinerGameViewModel.Stage.ANSWERING && !state.isHintUsed());
    }
    if (tvHint != null) {
      boolean showHint = !state.getHintText().trim().isEmpty();
      tvHint.setVisibility(showHint ? View.VISIBLE : View.GONE);
      if (showHint) {
        tvHint.setText(getString(R.string.refiner_hint_format, state.getHintText()));
      }
    }

    if (etAnswerInput != null) {
      etAnswerInput.setEnabled(state.getStage() == RefinerGameViewModel.Stage.ANSWERING);
    }
    if (btnSubmit != null) {
      btnSubmit.setEnabled(
          state.getStage() == RefinerGameViewModel.Stage.ANSWERING
              && currentValidation.isAllConstraintsSatisfied());
    }

    if (cardFeedback != null) {
      cardFeedback.setVisibility(
          state.getStage() == RefinerGameViewModel.Stage.FEEDBACK ? View.VISIBLE : View.GONE);
    }
    if (state.getStage() == RefinerGameViewModel.Stage.FEEDBACK) {
      renderFeedback(state);
    }

    refreshValidationUi();
  }

  @NonNull
  private String buildConstraintsText(@NonNull RefinerConstraints constraints) {
    List<String> lines = new ArrayList<>();
    if (!constraints.getBannedWords().isEmpty()) {
      lines.add(
          getString(
              R.string.refiner_constraint_banned_format, String.join(", ", constraints.getBannedWords())));
    }
    RefinerWordLimit wordLimit = constraints.getWordLimit();
    if (wordLimit != null) {
      if (wordLimit.getMode() == com.jjundev.oneclickeng.game.refiner.model.RefinerWordLimitMode.EXACT) {
        lines.add(getString(R.string.refiner_constraint_word_limit_exact_format, wordLimit.getValue()));
      } else {
        lines.add(getString(R.string.refiner_constraint_word_limit_max_format, wordLimit.getValue()));
      }
    }
    if (!constraints.getRequiredWord().isEmpty()) {
      lines.add(getString(R.string.refiner_constraint_required_format, constraints.getRequiredWord()));
    }
    if (lines.isEmpty()) {
      return "-";
    }
    return String.join("\n", lines);
  }

  private void refreshValidationUi() {
    RefinerGameViewModel vm = viewModel;
    TextInputEditText input = etAnswerInput;
    RefinerGameViewModel.GameUiState state = vm == null ? null : vm.getUiState().getValue();
    RefinerQuestion question = state == null ? null : state.getQuestion();
    if (input == null || question == null) {
      return;
    }

    String text = input.getText() == null ? "" : input.getText().toString();
    currentValidation = RefinerConstraintValidator.validate(question.getConstraints(), text);
    updateWordCount(question.getConstraints(), currentValidation.getWordCount());
    applyBannedWordHighlight(input, currentValidation.getBannedWordRanges());

    if (btnSubmit != null) {
      boolean answering = state.getStage() == RefinerGameViewModel.Stage.ANSWERING;
      btnSubmit.setEnabled(answering && currentValidation.isAllConstraintsSatisfied());
    }
  }

  private void updateWordCount(@NonNull RefinerConstraints constraints, int wordCount) {
    if (tvWordCount == null) {
      return;
    }
    RefinerWordLimit wordLimit = constraints.getWordLimit();
    if (wordLimit == null) {
      tvWordCount.setText(getString(R.string.refiner_word_count_plain_format, wordCount));
      return;
    }
    if (wordLimit.getMode() == com.jjundev.oneclickeng.game.refiner.model.RefinerWordLimitMode.EXACT) {
      tvWordCount.setText(getString(R.string.refiner_word_count_exact_format, wordCount, wordLimit.getValue()));
    } else {
      tvWordCount.setText(getString(R.string.refiner_word_count_max_format, wordCount, wordLimit.getValue()));
    }
  }

  private void applyBannedWordHighlight(
      @NonNull TextInputEditText editText,
      @NonNull List<RefinerConstraintValidator.TokenRange> ranges) {
    Editable editable = editText.getText();
    if (editable == null) {
      return;
    }
    clearHighlightSpans(editable);
    int color = ContextCompat.getColor(this, R.color.expression_precise_accent);
    for (RefinerConstraintValidator.TokenRange range : ranges) {
      int safeStart = Math.max(0, Math.min(range.getStart(), editable.length()));
      int safeEnd = Math.max(safeStart, Math.min(range.getEnd(), editable.length()));
      if (safeStart >= safeEnd) {
        continue;
      }
      editable.setSpan(new UnderlineSpan(), safeStart, safeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      editable.setSpan(
          new ForegroundColorSpan(color), safeStart, safeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }

  private void clearHighlightSpans(@NonNull Editable editable) {
    UnderlineSpan[] underlines = editable.getSpans(0, editable.length(), UnderlineSpan.class);
    for (UnderlineSpan span : underlines) {
      editable.removeSpan(span);
    }
    ForegroundColorSpan[] colors =
        editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
    for (ForegroundColorSpan span : colors) {
      editable.removeSpan(span);
    }
  }

  private void renderFeedback(@NonNull RefinerGameViewModel.GameUiState state) {
    RefinerEvaluation evaluation = state.getEvaluation();
    if (evaluation == null) {
      return;
    }
    if (tvFeedbackLevel != null) {
      tvFeedbackLevel.setText(
          getString(R.string.refiner_feedback_level_format, evaluation.getLevel().name()));
    }
    if (tvFeedbackSentence != null) {
      tvFeedbackSentence.setText(
          getString(R.string.refiner_feedback_sentence_format, state.getSubmittedSentence()));
    }

    setScoreRow(tvFeedbackLexical, progressLexical, evaluation.getLexicalScore());
    setScoreRow(tvFeedbackSyntax, progressSyntax, evaluation.getSyntaxScore());
    setScoreRow(tvFeedbackNaturalness, progressNaturalness, evaluation.getNaturalnessScore());
    setScoreRow(tvFeedbackCompliance, progressCompliance, evaluation.getComplianceScore());

    if (tvQuestionScore != null) {
      tvQuestionScore.setText(
          getString(
              R.string.refiner_feedback_question_score_format,
              state.getLastQuestionScore(),
              state.getTotalScore()));
    }
    if (tvFeedbackBonus != null) {
      tvFeedbackBonus.setText(
          getString(
              R.string.refiner_feedback_bonus_breakdown_format,
              state.getLastBaseScore(),
              state.getLastHintModifier(),
              state.getLastQuickBonus(),
              state.getLastCreativeBonus(),
              formatElapsedSeconds(state.getElapsedMs())));
    }

    renderExamples(evaluation.getLevelExamples(), evaluation.getLevel());

    if (tvInsight != null) {
      tvInsight.setText(getString(R.string.refiner_feedback_insight_format, evaluation.getInsight()));
    }
    if (btnNext instanceof TextView) {
      ((TextView) btnNext)
          .setText(
              state.getCurrentQuestionNumber() >= state.getTotalQuestions()
                  ? R.string.refiner_next_show_result
                  : R.string.refiner_next_question);
    }
  }

  private void renderExamples(@NonNull List<RefinerLevelExample> examples, @NonNull RefinerLevel myLevel) {
    LinearLayout container = examplesContainer;
    if (container == null) {
      return;
    }
    container.removeAllViews();
    List<RefinerLevelExample> sorted = new ArrayList<>(examples);
    Collections.sort(sorted, Comparator.comparingInt(e -> e.getLevel().ordinal()));

    int highlightColor = ContextCompat.getColor(this, R.color.shorts_progress_active);
    int defaultColor = ContextCompat.getColor(this, R.color.grey_100);

    for (RefinerLevelExample example : sorted) {
      MaterialCardView card = new MaterialCardView(this);
      card.setCardElevation(0f);
      card.setRadius(dpToPx(10));
      card.setCardBackgroundColor(example.getLevel() == myLevel ? highlightColor : defaultColor);

      LinearLayout.LayoutParams params =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      params.bottomMargin = dpToPx(8);
      card.setLayoutParams(params);

      TextView content = new TextView(this);
      content.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
      String levelLabel = example.getLevel().name();
      if (example.getLevel() == myLevel) {
        levelLabel = levelLabel + " · " + getString(R.string.refiner_feedback_my_level_suffix);
      }
      String title =
          getString(
              R.string.refiner_feedback_level_example_title, levelLabel, example.getSentence());
      String comment = getString(R.string.refiner_feedback_level_example_comment, example.getComment());
      content.setText(title + "\n" + comment);
      content.setTextSize(13f);
      content.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

      card.addView(content);
      container.addView(card);
    }
  }

  private void renderCompleted(@Nullable RefinerRoundResult result) {
    if (result == null) {
      return;
    }
    if (tvCompletedTotalScore != null) {
      tvCompletedTotalScore.setText(
          getString(R.string.refiner_completed_total_score_format, result.getTotalScore()));
    }
    if (tvCompletedLexicalAvg != null) {
      tvCompletedLexicalAvg.setText(
          getString(R.string.refiner_completed_lexical_avg_format, result.getAverageLexicalScore()));
    }
    if (tvCompletedSyntaxAvg != null) {
      tvCompletedSyntaxAvg.setText(
          getString(R.string.refiner_completed_syntax_avg_format, result.getAverageSyntaxScore()));
    }
    if (tvCompletedNaturalnessAvg != null) {
      tvCompletedNaturalnessAvg.setText(
          getString(
              R.string.refiner_completed_naturalness_avg_format, result.getAverageNaturalnessScore()));
    }
    if (tvCompletedComplianceAvg != null) {
      tvCompletedComplianceAvg.setText(
          getString(
              R.string.refiner_completed_compliance_avg_format, result.getAverageComplianceScore()));
    }
  }

  private void setScoreRow(
      @Nullable TextView valueView, @Nullable ProgressBar progressBar, int scoreValue) {
    if (valueView != null) {
      valueView.setText(getString(R.string.refiner_score_value_format, scoreValue));
    }
    if (progressBar != null) {
      progressBar.setProgress(scoreValue);
      progressBar.setProgressTintList(
          ColorStateList.valueOf(ContextCompat.getColor(this, R.color.toss_blue)));
    }
  }

  private void showExitConfirmDialog() {
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.refiner_exit_title)
        .setMessage(R.string.refiner_exit_message)
        .setNegativeButton(R.string.refiner_exit_cancel, null)
        .setPositiveButton(R.string.refiner_exit_confirm, (dialog, which) -> navigateToMainActivity())
        .show();
  }

  private void navigateToMainActivity() {
    Intent intent = new Intent(this, MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    startActivity(intent);
    finish();
  }

  private void showOnly(@Nullable View target) {
    setVisible(layoutLoading, target == layoutLoading);
    setVisible(layoutError, target == layoutError);
    setVisible(layoutContent, target == layoutContent);
    setVisible(layoutCompleted, target == layoutCompleted);
  }

  private void setVisible(@Nullable View view, boolean visible) {
    if (view != null) {
      view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
  }

  private int dpToPx(int dpValue) {
    return Math.round(dpValue * getResources().getDisplayMetrics().density);
  }

  @NonNull
  private String formatElapsedSeconds(long elapsedMs) {
    return String.format(Locale.US, "%.1f", elapsedMs / 1000f);
  }
}
