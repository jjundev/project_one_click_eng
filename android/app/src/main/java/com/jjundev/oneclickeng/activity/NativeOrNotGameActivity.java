package com.jjundev.oneclickeng.activity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.game.nativeornot.NativeOrNotGameViewModel;
import com.jjundev.oneclickeng.game.nativeornot.NativeOrNotGameViewModelFactory;
import com.jjundev.oneclickeng.game.nativeornot.NativeOrNotStatsStore;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotQuestion;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotRoundResult;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import java.util.List;

public final class NativeOrNotGameActivity extends AppCompatActivity {

  @Nullable private NativeOrNotGameViewModel viewModel;

  @Nullable private View layoutLoading;
  @Nullable private View layoutError;
  @Nullable private View layoutContent;
  @Nullable private View layoutCompleted;
  @Nullable private View bottomSheet;
  @Nullable private BottomSheetBehavior<View> bottomSheetBehavior;

  @Nullable private TextView tvLoadingMessage;
  @Nullable private TextView tvErrorMessage;
  @Nullable private MaterialButton btnRetry;

  @Nullable private TextView tvProgress;
  @Nullable private TextView tvScore;
  @Nullable private TextView tvStreak;
  @Nullable private TextView tvDifficulty;
  @Nullable private TextView tvSituation;
  @Nullable private LinearLayout optionContainer;
  @Nullable private MaterialButton btnConfirmOption;
  @Nullable private MaterialButton btnHint;
  @Nullable private TextView tvHint;

  @Nullable private View cardReason;
  @Nullable private TextView tvReasonPrompt;
  @Nullable private LinearLayout reasonContainer;
  @Nullable private MaterialButton btnConfirmReason;

  @Nullable private TextView tvPhaseResult;
  @Nullable private TextView tvReasonResult;
  @Nullable private TextView tvExplanation;
  @Nullable private TextView tvLearningPoint;
  @Nullable private MaterialButton btnRelated;
  @Nullable private MaterialButton btnNext;

  @Nullable private TextView tvCompletedAccuracy;
  @Nullable private TextView tvCompletedHighestStreak;
  @Nullable private TextView tvCompletedWeakTag;
  @Nullable private TextView tvCompletedScore;
  @Nullable private MaterialButton btnFinish;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_native_or_not_game);

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
  }

  private void bindViews() {
    layoutLoading = findViewById(R.id.layout_native_or_not_loading);
    layoutError = findViewById(R.id.layout_native_or_not_error);
    layoutContent = findViewById(R.id.layout_native_or_not_content);
    layoutCompleted = findViewById(R.id.layout_native_or_not_completed);
    bottomSheet = findViewById(R.id.bottom_sheet_native_or_not);

    tvLoadingMessage = findViewById(R.id.tv_native_or_not_loading_message);
    tvErrorMessage = findViewById(R.id.tv_native_or_not_error_message);
    btnRetry = findViewById(R.id.btn_native_or_not_retry);

    tvProgress = findViewById(R.id.tv_native_or_not_progress);
    tvScore = findViewById(R.id.tv_native_or_not_score);
    tvStreak = findViewById(R.id.tv_native_or_not_streak);
    tvDifficulty = findViewById(R.id.tv_native_or_not_difficulty);
    tvSituation = findViewById(R.id.tv_native_or_not_situation);
    optionContainer = findViewById(R.id.layout_native_or_not_option_container);
    btnConfirmOption = findViewById(R.id.btn_native_or_not_confirm_option);
    btnHint = findViewById(R.id.btn_native_or_not_hint);
    tvHint = findViewById(R.id.tv_native_or_not_hint);

    cardReason = findViewById(R.id.card_native_or_not_reason);
    tvReasonPrompt = findViewById(R.id.tv_native_or_not_reason_prompt);
    reasonContainer = findViewById(R.id.layout_native_or_not_reason_container);
    btnConfirmReason = findViewById(R.id.btn_native_or_not_confirm_reason);

    tvPhaseResult = findViewById(R.id.tv_native_or_not_phase_result);
    tvReasonResult = findViewById(R.id.tv_native_or_not_reason_result);
    tvExplanation = findViewById(R.id.tv_native_or_not_explanation);
    tvLearningPoint = findViewById(R.id.tv_native_or_not_learning_point);
    btnRelated = findViewById(R.id.btn_native_or_not_related);
    btnNext = findViewById(R.id.btn_native_or_not_next);

    tvCompletedAccuracy = findViewById(R.id.tv_native_or_not_completed_accuracy);
    tvCompletedHighestStreak = findViewById(R.id.tv_native_or_not_completed_streak);
    tvCompletedWeakTag = findViewById(R.id.tv_native_or_not_completed_weak_tag);
    tvCompletedScore = findViewById(R.id.tv_native_or_not_completed_score);
    btnFinish = findViewById(R.id.btn_native_or_not_finish);

    if (bottomSheet != null) {
      bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
      bottomSheetBehavior.setHideable(true);
      bottomSheetBehavior.setSkipCollapsed(true);
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }
  }

  private void initViewModel() {
    AppSettings settings = new AppSettingsStore(getApplicationContext()).getSettings();
    NativeOrNotGameViewModelFactory factory =
        new NativeOrNotGameViewModelFactory(
            LearningDependencyProvider.provideNativeOrNotGenerationManager(
                getApplicationContext(),
                settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
                settings.getLlmModelSummary()),
            new NativeOrNotStatsStore(getApplicationContext()));

    viewModel = new ViewModelProvider(this, factory).get(NativeOrNotGameViewModel.class);
    viewModel.getUiState().observe(this, this::renderState);
    viewModel.initialize();
  }

  private void bindListeners() {
    if (btnRetry != null) {
      btnRetry.setOnClickListener(
          v -> {
            if (viewModel != null) {
              viewModel.retryLoad();
            }
          });
    }

    if (btnConfirmOption != null) {
      btnConfirmOption.setOnClickListener(
          v -> {
            if (viewModel == null) {
              return;
            }
            NativeOrNotGameViewModel.ActionResult result = viewModel.onConfirmOption();
            if (result == NativeOrNotGameViewModel.ActionResult.INVALID) {
              Toast.makeText(this, R.string.native_or_not_select_option_first, Toast.LENGTH_SHORT)
                  .show();
            }
          });
    }

    if (btnHint != null) {
      btnHint.setOnClickListener(
          v -> {
            if (viewModel != null) {
              viewModel.onHintRequested();
            }
          });
    }

    if (btnConfirmReason != null) {
      btnConfirmReason.setOnClickListener(
          v -> {
            if (viewModel == null) {
              return;
            }
            NativeOrNotGameViewModel.ActionResult result = viewModel.onConfirmReason();
            if (result == NativeOrNotGameViewModel.ActionResult.INVALID) {
              Toast.makeText(this, R.string.native_or_not_select_reason_first, Toast.LENGTH_SHORT)
                  .show();
            }
          });
    }

    if (btnRelated != null) {
      btnRelated.setOnClickListener(
          v -> {
            if (viewModel == null) {
              return;
            }
            if (!viewModel.canRequestRelatedQuestion()) {
              return;
            }
            showBonusQuestionDialog();
          });
    }

    if (btnNext != null) {
      btnNext.setOnClickListener(
          v -> {
            if (viewModel != null) {
              viewModel.onNextFromExplanation();
            }
          });
    }

    if (btnFinish != null) {
      btnFinish.setOnClickListener(v -> navigateToMainActivity());
    }
  }

  private void renderState(@NonNull NativeOrNotGameViewModel.GameUiState state) {
    switch (state.getStage()) {
      case LOADING_BONUS:
        showOnly(layoutLoading);
        if (tvLoadingMessage != null) {
          tvLoadingMessage.setText(R.string.native_or_not_loading_bonus);
        }
        hideBottomSheet();
        break;
      case LOADING_QUESTION:
        showOnly(layoutLoading);
        if (tvLoadingMessage != null) {
          tvLoadingMessage.setText(R.string.native_or_not_loading_question);
        }
        hideBottomSheet();
        break;
      case ERROR:
        showOnly(layoutError);
        if (tvErrorMessage != null) {
          String message = state.getErrorMessage();
          tvErrorMessage.setText(
              message == null || message.trim().isEmpty()
                  ? getString(R.string.native_or_not_error_default)
                  : message);
        }
        hideBottomSheet();
        break;
      case ROUND_COMPLETED:
        showOnly(layoutCompleted);
        renderCompleted(state.getRoundResult());
        hideBottomSheet();
        break;
      case PHASE1_SELECTING:
      case PHASE2_REASON:
      case PHASE3_EXPLANATION:
      default:
        showOnly(layoutContent);
        renderQuestionState(state);
        break;
    }
  }

  private void renderQuestionState(@NonNull NativeOrNotGameViewModel.GameUiState state) {
    NativeOrNotQuestion question = state.getQuestion();
    if (question == null) {
      return;
    }

    if (tvProgress != null) {
      if (state.isBonusQuestion()) {
        tvProgress.setText(getString(R.string.native_or_not_progress_bonus));
      } else {
        tvProgress.setText(
            getString(
                R.string.native_or_not_progress_format,
                Math.min(state.getCurrentQuestionNumber(), state.getTotalQuestions()),
                state.getTotalQuestions()));
      }
    }

    if (tvScore != null) {
      tvScore.setText(getString(R.string.native_or_not_score_format, state.getTotalScore()));
    }
    if (tvStreak != null) {
      tvStreak.setText(getString(R.string.native_or_not_streak_format, state.getStreak()));
    }
    if (tvDifficulty != null) {
      tvDifficulty.setText(
          getString(R.string.native_or_not_difficulty_format, question.getDifficulty().name()));
    }
    if (tvSituation != null) {
      tvSituation.setText(question.getSituation());
    }

    renderOptions(state, question);

    boolean isPhase1 = state.getStage() == NativeOrNotGameViewModel.Stage.PHASE1_SELECTING;
    boolean isPhase2 = state.getStage() == NativeOrNotGameViewModel.Stage.PHASE2_REASON;
    boolean isPhase3 = state.getStage() == NativeOrNotGameViewModel.Stage.PHASE3_EXPLANATION;

    setVisible(btnConfirmOption, isPhase1);
    if (btnConfirmOption != null) {
      btnConfirmOption.setEnabled(state.isOptionConfirmEnabled());
      btnConfirmOption.setText(
          state.isBonusQuestion()
              ? R.string.native_or_not_bonus_confirm_option
              : R.string.native_or_not_confirm_option);
    }

    boolean hintVisible = isPhase1 && !state.isBonusQuestion();
    setVisible(btnHint, hintVisible);
    if (btnHint != null) {
      btnHint.setEnabled(!state.isHintUsed());
    }
    if (tvHint != null) {
      String hintText = state.getHintText();
      boolean showHint = hintText != null && !hintText.trim().isEmpty();
      tvHint.setVisibility(showHint ? View.VISIBLE : View.GONE);
      if (showHint) {
        tvHint.setText(getString(R.string.native_or_not_hint_format, hintText));
      }
    }

    setVisible(cardReason, isPhase2 && !state.isBonusQuestion());
    setVisible(btnConfirmReason, isPhase2 && !state.isBonusQuestion());
    if (btnConfirmReason != null) {
      btnConfirmReason.setEnabled(state.isReasonConfirmEnabled());
    }

    if (!state.isBonusQuestion()) {
      if (tvReasonPrompt != null) {
        tvReasonPrompt.setText(
            getString(
                R.string.native_or_not_reason_prompt_format,
                question.getAwkwardSentence()));
      }
      renderReasonChoices(state, question.getReasonChoices());
    } else {
      clearReasonChoices();
    }

    if (isPhase3) {
      renderExplanation(state, question);
      showBottomSheet();
    } else {
      hideBottomSheet();
    }
  }

  private void renderOptions(
      @NonNull NativeOrNotGameViewModel.GameUiState state, @NonNull NativeOrNotQuestion question) {
    LinearLayout container = optionContainer;
    if (container == null) {
      return;
    }
    container.removeAllViews();

    LayoutInflater inflater = LayoutInflater.from(this);
    List<String> options = question.getOptions();
    for (int i = 0; i < options.size(); i++) {
      View item = inflater.inflate(R.layout.item_native_or_not_option, container, false);
      MaterialButton button = item.findViewById(R.id.btn_native_or_not_option);
      int optionIndex = i;
      button.setText(getString(R.string.native_or_not_option_format, i + 1, options.get(i)));
      button.setOnClickListener(
          v -> {
            if (viewModel != null
                && state.getStage() == NativeOrNotGameViewModel.Stage.PHASE1_SELECTING) {
              viewModel.onOptionSelected(optionIndex);
            }
          });
      applyOptionStyle(button, state, question, optionIndex);
      container.addView(item);
    }
  }

  private void applyOptionStyle(
      @NonNull MaterialButton button,
      @NonNull NativeOrNotGameViewModel.GameUiState state,
      @NonNull NativeOrNotQuestion question,
      int optionIndex) {
    int defaultBg = ContextCompat.getColor(this, R.color.white);
    int defaultStroke = ContextCompat.getColor(this, R.color.grey_300);
    int defaultText = ContextCompat.getColor(this, R.color.text_primary);

    int selectedBg = ContextCompat.getColor(this, R.color.shorts_progress_active);
    int selectedStroke = ContextCompat.getColor(this, R.color.toss_blue);
    int selectedText = ContextCompat.getColor(this, R.color.toss_blue);

    int correctBg = ContextCompat.getColor(this, R.color.expression_natural_after_bg);
    int correctStroke = ContextCompat.getColor(this, R.color.expression_natural_accent);
    int correctText = ContextCompat.getColor(this, R.color.expression_natural_accent);

    int wrongBg = ContextCompat.getColor(this, R.color.expression_precise_after_bg);
    int wrongStroke = ContextCompat.getColor(this, R.color.expression_precise_accent);
    int wrongText = ContextCompat.getColor(this, R.color.expression_precise_accent);

    boolean selected = state.getSelectedOptionIndex() == optionIndex;
    boolean revealResult = state.getStage() == NativeOrNotGameViewModel.Stage.PHASE3_EXPLANATION;

    int background = selected ? selectedBg : defaultBg;
    int stroke = selected ? selectedStroke : defaultStroke;
    int text = selected ? selectedText : defaultText;

    if (revealResult) {
      if (optionIndex == question.getCorrectIndex()) {
        background = correctBg;
        stroke = correctStroke;
        text = correctText;
      } else if (selected && optionIndex != question.getCorrectIndex()) {
        background = wrongBg;
        stroke = wrongStroke;
        text = wrongText;
      }
    }

    button.setEnabled(state.getStage() == NativeOrNotGameViewModel.Stage.PHASE1_SELECTING);
    button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(background));
    button.setStrokeColor(android.content.res.ColorStateList.valueOf(stroke));
    button.setStrokeWidth(1);
    button.setTextColor(text);
  }

  private void renderReasonChoices(
      @NonNull NativeOrNotGameViewModel.GameUiState state, @NonNull List<String> reasonChoices) {
    LinearLayout container = reasonContainer;
    if (container == null) {
      return;
    }
    container.removeAllViews();

    LayoutInflater inflater = LayoutInflater.from(this);
    for (int i = 0; i < reasonChoices.size(); i++) {
      View item = inflater.inflate(R.layout.item_native_or_not_reason_choice, container, false);
      MaterialButton button = item.findViewById(R.id.btn_native_or_not_reason_choice);
      int index = i;
      button.setText(getString(R.string.native_or_not_reason_option_format, i + 1, reasonChoices.get(i)));
      button.setOnClickListener(
          v -> {
            if (viewModel != null
                && state.getStage() == NativeOrNotGameViewModel.Stage.PHASE2_REASON) {
              viewModel.onReasonSelected(index);
            }
          });
      boolean selected = state.getSelectedReasonIndex() == index;
      int bg = selected ? R.color.shorts_progress_active : R.color.white;
      int stroke = selected ? R.color.toss_blue : R.color.grey_300;
      int text = selected ? R.color.toss_blue : R.color.text_primary;
      button.setEnabled(state.getStage() == NativeOrNotGameViewModel.Stage.PHASE2_REASON);
      button.setBackgroundTintList(
          android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, bg)));
      button.setStrokeColor(
          android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, stroke)));
      button.setStrokeWidth(1);
      button.setTextColor(ContextCompat.getColor(this, text));
      container.addView(item);
    }
  }

  private void clearReasonChoices() {
    if (reasonContainer != null) {
      reasonContainer.removeAllViews();
    }
  }

  private void renderExplanation(
      @NonNull NativeOrNotGameViewModel.GameUiState state, @NonNull NativeOrNotQuestion question) {
    if (tvPhaseResult != null) {
      tvPhaseResult.setText(
          state.isPhase1Correct()
              ? R.string.native_or_not_phase1_correct
              : R.string.native_or_not_phase1_wrong);
      tvPhaseResult.setTextColor(
          ContextCompat.getColor(
              this,
              state.isPhase1Correct()
                  ? R.color.expression_natural_accent
                  : R.color.expression_precise_accent));
    }
    if (tvReasonResult != null) {
      if (state.isBonusQuestion()) {
        tvReasonResult.setVisibility(View.GONE);
      } else {
        tvReasonResult.setVisibility(View.VISIBLE);
        tvReasonResult.setText(
            state.isReasonCorrect()
                ? R.string.native_or_not_reason_correct
                : R.string.native_or_not_reason_wrong);
      }
    }
    if (tvExplanation != null) {
      tvExplanation.setText(question.getExplanation());
    }
    if (tvLearningPoint != null) {
      tvLearningPoint.setText(getString(R.string.native_or_not_learning_point_format, question.getLearningPoint()));
    }

    if (btnRelated != null) {
      boolean visible = state.canUseRelatedBonus() && !state.isBonusQuestion();
      btnRelated.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
    if (btnNext != null) {
      btnNext.setText(
          state.getCurrentQuestionNumber() > state.getTotalQuestions()
              ? R.string.native_or_not_next
              : state.getCurrentQuestionNumber() >= state.getTotalQuestions()
                  ? R.string.native_or_not_finish_round
                  : R.string.native_or_not_next);
    }
  }

  private void renderCompleted(@Nullable NativeOrNotRoundResult result) {
    if (result == null) {
      return;
    }
    if (tvCompletedAccuracy != null) {
      tvCompletedAccuracy.setText(
          getString(
              R.string.native_or_not_completed_accuracy_format,
              result.getAccuracyPercent(),
              result.getCorrectAnswers(),
              result.getTotalQuestions()));
    }
    if (tvCompletedHighestStreak != null) {
      tvCompletedHighestStreak.setText(
          getString(R.string.native_or_not_completed_streak_format, result.getHighestStreak()));
    }
    if (tvCompletedWeakTag != null) {
      tvCompletedWeakTag.setText(
          getString(R.string.native_or_not_completed_weak_tag_format, result.getWeakTagDisplay()));
    }
    if (tvCompletedScore != null) {
      tvCompletedScore.setText(
          getString(R.string.native_or_not_completed_score_format, result.getTotalScore()));
    }
  }

  private void showBonusQuestionDialog() {
    Dialog dialog = new Dialog(this);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    dialog.setContentView(R.layout.dialog_native_or_not_bonus);

    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    View btnCancel = dialog.findViewById(R.id.btn_cancel);
    View btnConfirm = dialog.findViewById(R.id.btn_confirm);
    if (btnCancel != null) {
      btnCancel.setOnClickListener(v -> dialog.dismiss());
    }
    if (btnConfirm != null) {
      btnConfirm.setOnClickListener(
          v -> {
            dialog.dismiss();
            if (viewModel != null) {
              viewModel.onRequestRelatedQuestion();
            }
          });
    }
    dialog.show();
  }

  private void showExitConfirmDialog() {
    Dialog dialog = new Dialog(this);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    dialog.setContentView(R.layout.dialog_exit_confirm);

    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    TextView tvHeader = dialog.findViewById(R.id.tv_header);
    TextView tvMessage = dialog.findViewById(R.id.tv_message);
    if (tvHeader != null) {
      tvHeader.setText(R.string.native_or_not_exit_title);
    }
    if (tvMessage != null) {
      tvMessage.setText(R.string.native_or_not_exit_message);
    }

    View btnCancel = dialog.findViewById(R.id.btn_cancel);
    View btnConfirm = dialog.findViewById(R.id.btn_confirm);

    if (btnCancel != null) {
      btnCancel.setOnClickListener(v -> dialog.dismiss());
    }
    if (btnConfirm != null) {
      btnConfirm.setOnClickListener(
          v -> {
            dialog.dismiss();
            navigateToMainActivity();
          });
    }

    dialog.show();
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

  private void showBottomSheet() {
    if (bottomSheet == null) {
      return;
    }
    bottomSheet.setVisibility(View.VISIBLE);
    if (bottomSheetBehavior != null) {
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }
  }

  private void hideBottomSheet() {
    if (bottomSheetBehavior != null) {
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
      return;
    }
    if (bottomSheet != null) {
      bottomSheet.setVisibility(View.GONE);
    }
  }
}
