package com.jjundev.oneclickeng.activity;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.game.minefield.MinefieldGameViewModel;
import com.jjundev.oneclickeng.game.minefield.MinefieldGameViewModelFactory;
import com.jjundev.oneclickeng.game.minefield.MinefieldStatsStore;
import com.jjundev.oneclickeng.game.minefield.MinefieldWordUsageMatcher;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldDifficulty;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldEvaluation;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldQuestion;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldRoundResult;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MinefieldGameActivity extends AppCompatActivity {
  @Nullable private MinefieldGameViewModel viewModel;
  @NonNull private final List<Chip> wordChips = new ArrayList<>();
  @NonNull private final Set<Integer> locallyUsedIndices = new LinkedHashSet<>();

  @Nullable private View layoutLoading;
  @Nullable private View layoutError;
  @Nullable private View layoutContent;
  @Nullable private View layoutCompleted;

  @Nullable private TextView tvLoadingMessage;
  @Nullable private TextView tvErrorMessage;
  @Nullable private TextView tvProgress;
  @Nullable private TextView tvScore;
  @Nullable private TextView tvDifficulty;
  @Nullable private TextView tvSituation;
  @Nullable private TextView tvQuestion;
  @Nullable private TextView tvMineRequired;
  @Nullable private TextView tvUnusedWordCount;
  @Nullable private FlexboxLayout chipContainer;
  @Nullable private EditText etAnswerInput;
  @Nullable private View btnSubmit;
  @Nullable private View btnRetry;

  @Nullable private View cardFeedback;
  @Nullable private TextView tvFeedbackGrammar;
  @Nullable private TextView tvFeedbackNaturalness;
  @Nullable private TextView tvFeedbackWordUsage;
  @Nullable private ProgressBar progressGrammar;
  @Nullable private ProgressBar progressNaturalness;
  @Nullable private ProgressBar progressWordUsage;
  @Nullable private TextView tvFeedbackQuestionScore;
  @Nullable private TextView tvFeedbackBonus;
  @Nullable private TextView tvFeedbackStrengths;
  @Nullable private TextView tvFeedbackImprovement;
  @Nullable private TextView tvFeedbackImprovedSentence;
  @Nullable private TextView tvFeedbackExamples;
  @Nullable private View btnNext;

  @Nullable private TextView tvCompletedTotalScore;
  @Nullable private TextView tvCompletedGrammarAvg;
  @Nullable private TextView tvCompletedNaturalnessAvg;
  @Nullable private TextView tvCompletedWordUsageAvg;
  @Nullable private View btnFinish;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_minefield_game);

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
    layoutLoading = findViewById(R.id.layout_minefield_loading);
    layoutError = findViewById(R.id.layout_minefield_error);
    layoutContent = findViewById(R.id.layout_minefield_content);
    layoutCompleted = findViewById(R.id.layout_minefield_completed);

    tvLoadingMessage = findViewById(R.id.tv_minefield_loading_message);
    tvErrorMessage = findViewById(R.id.tv_minefield_error_message);
    tvProgress = findViewById(R.id.tv_minefield_progress);
    tvScore = findViewById(R.id.tv_minefield_score);
    tvDifficulty = findViewById(R.id.tv_minefield_difficulty);
    tvSituation = findViewById(R.id.tv_minefield_situation);
    tvQuestion = findViewById(R.id.tv_minefield_question);
    tvMineRequired = findViewById(R.id.tv_minefield_required_words);
    tvUnusedWordCount = findViewById(R.id.tv_minefield_unused_count);
    chipContainer = findViewById(R.id.layout_minefield_chip_container);
    etAnswerInput = findViewById(R.id.et_minefield_answer);
    btnSubmit = findViewById(R.id.btn_minefield_submit);
    btnRetry = findViewById(R.id.btn_minefield_retry);

    cardFeedback = findViewById(R.id.card_minefield_feedback);
    tvFeedbackGrammar = findViewById(R.id.tv_minefield_feedback_grammar);
    tvFeedbackNaturalness = findViewById(R.id.tv_minefield_feedback_naturalness);
    tvFeedbackWordUsage = findViewById(R.id.tv_minefield_feedback_word_usage);
    progressGrammar = findViewById(R.id.progress_minefield_grammar);
    progressNaturalness = findViewById(R.id.progress_minefield_naturalness);
    progressWordUsage = findViewById(R.id.progress_minefield_word_usage);
    tvFeedbackQuestionScore = findViewById(R.id.tv_minefield_question_score);
    tvFeedbackBonus = findViewById(R.id.tv_minefield_feedback_bonus);
    tvFeedbackStrengths = findViewById(R.id.tv_minefield_feedback_strengths);
    tvFeedbackImprovement = findViewById(R.id.tv_minefield_feedback_improvement);
    tvFeedbackImprovedSentence = findViewById(R.id.tv_minefield_feedback_improved_sentence);
    tvFeedbackExamples = findViewById(R.id.tv_minefield_feedback_examples);
    btnNext = findViewById(R.id.btn_minefield_next);

    tvCompletedTotalScore = findViewById(R.id.tv_minefield_completed_total_score);
    tvCompletedGrammarAvg = findViewById(R.id.tv_minefield_completed_grammar_avg);
    tvCompletedNaturalnessAvg = findViewById(R.id.tv_minefield_completed_naturalness_avg);
    tvCompletedWordUsageAvg = findViewById(R.id.tv_minefield_completed_word_usage_avg);
    btnFinish = findViewById(R.id.btn_minefield_finish);
  }

  private void initViewModel() {
    AppSettings settings = new AppSettingsStore(getApplicationContext()).getSettings();
    MinefieldGameViewModelFactory factory =
        new MinefieldGameViewModelFactory(
            LearningDependencyProvider.provideMinefieldGenerationManager(
                getApplicationContext(),
                settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
                settings.getLlmModelMinefield()),
            new MinefieldStatsStore(getApplicationContext()));
    viewModel = new ViewModelProvider(this, factory).get(MinefieldGameViewModel.class);
    viewModel.getUiState().observe(this, this::renderState);
  }

  private void bindListeners() {
    if (btnRetry != null) {
      btnRetry.setOnClickListener(
          v -> {
            if (viewModel != null) {
              viewModel.retry();
            }
          });
    }

    if (btnSubmit != null) {
      btnSubmit.setOnClickListener(
          v -> {
            MinefieldGameViewModel vm = viewModel;
            if (vm == null || etAnswerInput == null) {
              return;
            }
            MinefieldGameViewModel.ActionResult result =
                vm.onSubmitSentence(etAnswerInput.getText().toString(), locallyUsedIndices.size());
            if (result == MinefieldGameViewModel.ActionResult.INVALID) {
              Toast.makeText(this, R.string.minefield_submit_invalid, Toast.LENGTH_SHORT).show();
            }
          });
    }

    if (btnNext != null) {
      btnNext.setOnClickListener(
          v -> {
            MinefieldGameViewModel vm = viewModel;
            if (vm == null) {
              return;
            }
            vm.onNextFromFeedback();
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
              refreshLocalWordUsage();
            }

            @Override
            public void afterTextChanged(Editable s) {}
          });
    }
  }

  private void showDifficultyDialog() {
    String[] labels = {
      getString(R.string.minefield_difficulty_easy),
      getString(R.string.minefield_difficulty_normal),
      getString(R.string.minefield_difficulty_hard),
      getString(R.string.minefield_difficulty_expert)
    };
    MinefieldDifficulty[] values = {
      MinefieldDifficulty.EASY,
      MinefieldDifficulty.NORMAL,
      MinefieldDifficulty.HARD,
      MinefieldDifficulty.EXPERT
    };
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.minefield_difficulty_dialog_title)
        .setCancelable(false)
        .setItems(
            labels,
            (dialog, which) -> {
              MinefieldGameViewModel vm = viewModel;
              if (vm == null) {
                return;
              }
              int safeIndex = Math.max(0, Math.min(which, values.length - 1));
              vm.initialize(values[safeIndex]);
            })
        .show();
  }

  private void renderState(@NonNull MinefieldGameViewModel.GameUiState state) {
    if (tvProgress != null) {
      tvProgress.setText(
          getString(
              R.string.minefield_progress_format,
              Math.min(state.getCurrentQuestionNumber(), state.getTotalQuestions()),
              state.getTotalQuestions()));
    }
    if (tvScore != null) {
      tvScore.setText(getString(R.string.minefield_score_format, state.getTotalScore()));
    }
    if (tvDifficulty != null) {
      tvDifficulty.setText(
          getString(R.string.minefield_difficulty_format, state.getDifficulty().name()));
    }

    switch (state.getStage()) {
      case LOADING:
        showOnly(layoutLoading);
        if (tvLoadingMessage != null) {
          String message = state.getLoadingMessage();
          tvLoadingMessage.setText(
              message == null || message.trim().isEmpty()
                  ? getString(R.string.minefield_loading_default)
                  : message);
        }
        break;
      case ERROR:
        showOnly(layoutError);
        if (tvErrorMessage != null) {
          String errorMessage = state.getErrorMessage();
          tvErrorMessage.setText(
              errorMessage == null || errorMessage.trim().isEmpty()
                  ? getString(R.string.minefield_error_default)
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

  private void renderQuestionState(@NonNull MinefieldGameViewModel.GameUiState state) {
    MinefieldQuestion question = state.getQuestion();
    if (question == null) {
      return;
    }
    if (tvSituation != null) {
      tvSituation.setText(question.getSituation());
    }
    if (tvQuestion != null) {
      tvQuestion.setText(question.getQuestion());
    }
    renderRequiredMineWords(question);
    renderWordChips(question);
    refreshLocalWordUsage();

    if (etAnswerInput != null) {
      boolean editable = state.getStage() == MinefieldGameViewModel.Stage.ANSWERING;
      etAnswerInput.setEnabled(editable);
    }
    if (btnSubmit != null) {
      btnSubmit.setEnabled(state.getStage() == MinefieldGameViewModel.Stage.ANSWERING);
    }

    if (cardFeedback != null) {
      cardFeedback.setVisibility(
          state.getStage() == MinefieldGameViewModel.Stage.FEEDBACK ? View.VISIBLE : View.GONE);
    }

    if (state.getStage() == MinefieldGameViewModel.Stage.FEEDBACK) {
      renderFeedback(state);
    }
  }

  private void renderRequiredMineWords(@NonNull MinefieldQuestion question) {
    if (tvMineRequired == null) {
      return;
    }
    List<String> words = question.getWords();
    List<Integer> requiredIndices = question.getRequiredWordIndices();
    List<String> requiredWords = new ArrayList<>();
    for (Integer index : requiredIndices) {
      if (index == null || index < 0 || index >= words.size()) {
        continue;
      }
      requiredWords.add(words.get(index));
    }
    if (requiredWords.isEmpty()) {
      tvMineRequired.setText(getString(R.string.minefield_required_words_empty));
      return;
    }
    tvMineRequired.setText(
        getString(R.string.minefield_required_words_format, String.join(", ", requiredWords)));
  }

  private void renderWordChips(@NonNull MinefieldQuestion question) {
    FlexboxLayout container = chipContainer;
    if (container == null) {
      return;
    }
    container.removeAllViews();
    wordChips.clear();

    for (int i = 0; i < question.getWords().size(); i++) {
      String word = question.getWords().get(i);
      Chip chip = new Chip(this);
      chip.setText(word);
      chip.setCheckable(false);
      chip.setClickable(true);
      chip.setChipStrokeWidth(2f);
      chip.setTextSize(14f);
      chip.setEnsureMinTouchTargetSize(false);
      chip.setChipBackgroundColorResource(R.color.white);
      chip.setChipStrokeColorResource(R.color.grey_300);
      chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
      if (question.getRequiredWordIndices().contains(i)) {
        chip.setText("💣 " + word);
      }
      int index = i;
      chip.setOnClickListener(v -> appendWordToInput(question.getWords().get(index)));

      LinearLayout.LayoutParams params =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      params.setMarginEnd(dpToPx(8));
      params.bottomMargin = dpToPx(8);
      chip.setLayoutParams(params);
      container.addView(chip);
      wordChips.add(chip);
    }
  }

  private void appendWordToInput(@NonNull String word) {
    EditText input = etAnswerInput;
    if (input == null || !input.isEnabled()) {
      return;
    }
    String existing = input.getText() == null ? "" : input.getText().toString();
    StringBuilder builder = new StringBuilder(existing);
    if (!existing.isEmpty() && !existing.endsWith(" ")) {
      builder.append(' ');
    }
    builder.append(word).append(' ');
    input.setText(builder.toString());
    input.setSelection(input.getText() == null ? 0 : input.getText().length());
  }

  private void refreshLocalWordUsage() {
    MinefieldGameViewModel vm = viewModel;
    EditText input = etAnswerInput;
    MinefieldGameViewModel.GameUiState state = vm == null ? null : vm.getUiState().getValue();
    MinefieldQuestion question = state == null ? null : state.getQuestion();
    if (input == null || question == null) {
      return;
    }

    locallyUsedIndices.clear();
    locallyUsedIndices.addAll(
        MinefieldWordUsageMatcher.findUsedWordIndices(
            question.getWords(), input.getText() == null ? "" : input.getText().toString()));

    int usedText = ContextCompat.getColor(this, R.color.toss_blue);
    int idleText = ContextCompat.getColor(this, R.color.text_primary);

    for (int i = 0; i < wordChips.size(); i++) {
      Chip chip = wordChips.get(i);
      boolean used = locallyUsedIndices.contains(i);
      chip.setChipBackgroundColorResource(used ? R.color.shorts_progress_active : R.color.white);
      chip.setChipStrokeColorResource(used ? R.color.toss_blue : R.color.grey_300);
      chip.setTextColor(used ? usedText : idleText);
      if (question.getRequiredWordIndices().contains(i) && !used) {
        chip.setChipStrokeColorResource(R.color.expression_precise_accent);
      }
    }

    if (tvUnusedWordCount != null) {
      int unusedCount = Math.max(0, question.getWords().size() - locallyUsedIndices.size());
      tvUnusedWordCount.setText(
          getString(R.string.minefield_unused_words_format, unusedCount, question.getWords().size()));
    }
  }

  private void renderFeedback(@NonNull MinefieldGameViewModel.GameUiState state) {
    MinefieldEvaluation evaluation = state.getEvaluation();
    if (evaluation == null) {
      return;
    }
    setScoreRow(tvFeedbackGrammar, progressGrammar, evaluation.getGrammarScore());
    setScoreRow(tvFeedbackNaturalness, progressNaturalness, evaluation.getNaturalnessScore());
    setScoreRow(tvFeedbackWordUsage, progressWordUsage, evaluation.getWordUsageScore());

    if (tvFeedbackQuestionScore != null) {
      tvFeedbackQuestionScore.setText(
          getString(
              R.string.minefield_question_score_format,
              state.getLastQuestionScore(),
              state.getTotalScore()));
    }

    if (tvFeedbackBonus != null) {
      String bonusText =
          getString(
              R.string.minefield_bonus_breakdown_format,
              state.getBonusAllWords(),
              state.getBonusQuick(),
              state.getBonusTransform(),
              state.getPenaltyMine(),
              formatElapsedSeconds(state.getElapsedMs()));
      tvFeedbackBonus.setText(bonusText);
    }

    if (tvFeedbackStrengths != null) {
      tvFeedbackStrengths.setText(
          getString(R.string.minefield_feedback_strengths_format, evaluation.getStrengthsComment()));
    }
    if (tvFeedbackImprovement != null) {
      tvFeedbackImprovement.setText(
          getString(R.string.minefield_feedback_improvement_format, evaluation.getImprovementComment()));
    }
    if (tvFeedbackImprovedSentence != null) {
      tvFeedbackImprovedSentence.setText(
          getString(
              R.string.minefield_feedback_improved_sentence_format, evaluation.getImprovedSentence()));
      tvFeedbackImprovedSentence.setTypeface(Typeface.MONOSPACE);
    }
    if (tvFeedbackExamples != null) {
      tvFeedbackExamples.setText(
          getString(
              R.string.minefield_feedback_examples_format,
              evaluation.getExampleBasic(),
              evaluation.getExampleIntermediate(),
              evaluation.getExampleAdvanced()));
      tvFeedbackExamples.setTypeface(Typeface.MONOSPACE);
    }

    if (btnNext instanceof TextView) {
      ((TextView) btnNext)
          .setText(
              state.getCurrentQuestionNumber() >= state.getTotalQuestions()
                  ? R.string.minefield_next_show_result
                  : R.string.minefield_next_question);
    }
  }

  private void setScoreRow(
      @Nullable TextView valueView, @Nullable ProgressBar progressBar, int scoreValue) {
    if (valueView != null) {
      valueView.setText(getString(R.string.minefield_score_value_format, scoreValue));
    }
    if (progressBar != null) {
      progressBar.setProgress(scoreValue);
    }
  }

  private void renderCompleted(@Nullable MinefieldRoundResult result) {
    if (result == null) {
      return;
    }
    if (tvCompletedTotalScore != null) {
      tvCompletedTotalScore.setText(
          getString(R.string.minefield_completed_total_score_format, result.getTotalScore()));
    }
    if (tvCompletedGrammarAvg != null) {
      tvCompletedGrammarAvg.setText(
          getString(R.string.minefield_completed_grammar_avg_format, result.getAverageGrammarScore()));
    }
    if (tvCompletedNaturalnessAvg != null) {
      tvCompletedNaturalnessAvg.setText(
          getString(
              R.string.minefield_completed_naturalness_avg_format,
              result.getAverageNaturalnessScore()));
    }
    if (tvCompletedWordUsageAvg != null) {
      tvCompletedWordUsageAvg.setText(
          getString(
              R.string.minefield_completed_word_usage_avg_format, result.getAverageWordUsageScore()));
    }
  }

  private void showExitConfirmDialog() {
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.minefield_exit_title)
        .setMessage(R.string.minefield_exit_message)
        .setNegativeButton(R.string.minefield_exit_cancel, null)
        .setPositiveButton(R.string.minefield_exit_confirm, (dialog, which) -> navigateToMainActivity())
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
