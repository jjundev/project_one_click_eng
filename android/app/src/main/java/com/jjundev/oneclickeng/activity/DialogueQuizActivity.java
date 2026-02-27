package com.jjundev.oneclickeng.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.learning.quiz.DialogueQuizViewModel;
import com.jjundev.oneclickeng.learning.quiz.DialogueQuizViewModelFactory;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import java.util.List;

public class DialogueQuizActivity extends AppCompatActivity {
  public static final String EXTRA_SUMMARY_JSON = "extra_summary_json";
  public static final String EXTRA_FEATURE_BUNDLE_JSON = "extra_feature_bundle_json";
  public static final String EXTRA_REQUESTED_QUESTION_COUNT = "extra_requested_question_count";
  public static final String EXTRA_STREAM_SESSION_ID = "extra_stream_session_id";

  private static final String TAG = "JOB_J-20260217-002";
  private static final long SHOW_CHOICES_DELAY_MS = 500L;
  private static final long AUTO_CHECK_DELAY_MS = 200L;
  private static final long SHOW_NEXT_BUTTON_DELAY_MS = 500L;

  private enum BottomSheetUiStage {
    HIDDEN,
    CHOICES_ONLY,
    NEXT_BUTTON_ONLY
  }

  @Nullable private DialogueQuizViewModel viewModel;
  @Nullable private View loadingView;
  @Nullable private View errorView;
  @Nullable private View contentView;
  @Nullable private View completedView;
  @Nullable private View bottomSheetView;
  @Nullable private BottomSheetBehavior<View> bottomSheetBehavior;
  @Nullable private ImageButton btnLeft;
  @Nullable private TextView tvTitle;
  @Nullable private TextView tvErrorMessage;
  @Nullable private MaterialButton btnRetry;
  @Nullable private TextView tvProgress;
  @Nullable private ProgressBar progressBar;
  @Nullable private TextView tvQuestion;
  @Nullable private MaterialCardView cardQuestionMaterial;
  @Nullable private TextView tvQuestionMaterial;
  @Nullable private LinearLayout choiceContainer;
  @Nullable private TextInputLayout inputAnswerLayout;
  @Nullable private MaterialCardView resultCard;
  @Nullable private TextView tvResultTitle;
  @Nullable private TextView tvCorrectAnswer;
  @Nullable private TextView tvExplanation;
  @Nullable private MaterialButton btnPrimary;
  @Nullable private TextView tvNextQuestionLoadingNotice;
  @Nullable private TextView tvCompletedSummary;
  @Nullable private MaterialButton btnFinish;

  @NonNull private final Handler uiHandler = new Handler(Looper.getMainLooper());
  @Nullable private Runnable showChoicesRunnable;
  @Nullable private Runnable autoCheckRunnable;
  @Nullable private Runnable showNextButtonRunnable;

  private BottomSheetUiStage bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
  private int renderedBottomSheetQuestionIndex = -1;
  private int lastLoggedQuestionIndex = -1;
  private boolean autoCheckPending = false;
  private boolean showNextButtonScheduled = false;
  private int requestedQuestionCount = 5;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_dialogue_quiz);

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                showExitConfirmDialog();
              }
            });

    View rootView = findViewById(android.R.id.content);
    ViewCompat.setOnApplyWindowInsetsListener(
        rootView,
        (v, windowInsets) -> {
          Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
          Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(
              systemBarInsets.left,
              systemBarInsets.top,
              systemBarInsets.right,
              Math.max(imeInsets.bottom, systemBarInsets.bottom));
          return WindowInsetsCompat.CONSUMED;
        });

    bindViews();
    bindListeners();

    Intent intent = getIntent();
    String summaryJson = intent.getStringExtra(EXTRA_SUMMARY_JSON);
    String streamSessionId = intent.getStringExtra(EXTRA_STREAM_SESSION_ID);
    requestedQuestionCount = Math.max(1, intent.getIntExtra(EXTRA_REQUESTED_QUESTION_COUNT, 5));
    configureToolbar();

    initViewModel();
    if (viewModel != null) {
      viewModel.initialize(summaryJson, requestedQuestionCount, streamSessionId);
    }
    logDebug("quiz activity entered");
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    cancelPendingBottomSheetTasks();
  }

  private void bindViews() {
    loadingView = findViewById(R.id.layout_quiz_loading);
    errorView = findViewById(R.id.layout_quiz_error);
    contentView = findViewById(R.id.layout_quiz_content);
    completedView = findViewById(R.id.layout_quiz_completed);
    bottomSheetView = findViewById(R.id.bottom_sheet_quiz);
    btnLeft = findViewById(R.id.btn_left);
    tvTitle = findViewById(R.id.tv_title);
    tvErrorMessage = findViewById(R.id.tv_quiz_error_message);
    btnRetry = findViewById(R.id.btn_quiz_retry);
    tvProgress = findViewById(R.id.tv_progress);
    progressBar = findViewById(R.id.progress_bar);
    tvQuestion = findViewById(R.id.tv_quiz_question);
    cardQuestionMaterial = findViewById(R.id.card_quiz_material);
    tvQuestionMaterial = findViewById(R.id.tv_quiz_material);
    choiceContainer = findViewById(R.id.layout_quiz_choice_container);
    inputAnswerLayout = findViewById(R.id.layout_quiz_answer_input);
    resultCard = findViewById(R.id.card_quiz_result);
    tvResultTitle = findViewById(R.id.tv_quiz_result_title);
    tvCorrectAnswer = findViewById(R.id.tv_quiz_correct_answer);
    tvExplanation = findViewById(R.id.tv_quiz_explanation);
    btnPrimary = findViewById(R.id.btn_quiz_primary);
    tvNextQuestionLoadingNotice = findViewById(R.id.tv_quiz_next_loading_notice);
    tvCompletedSummary = findViewById(R.id.tv_quiz_completed_summary);
    btnFinish = findViewById(R.id.btn_quiz_finish);

    if (bottomSheetView != null) {
      bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetView);
      bottomSheetBehavior.setHideable(true);
      bottomSheetBehavior.setSkipCollapsed(true);
      bottomSheetBehavior.setDraggable(false);
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }
  }

  private void initViewModel() {
    AppSettings settings = new AppSettingsStore(getApplicationContext()).getSettings();
    DialogueQuizViewModelFactory factory =
        new DialogueQuizViewModelFactory(
            LearningDependencyProvider.provideQuizGenerationManager(
                getApplicationContext(),
                settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
                settings.getLlmModelSummary()),
            LearningDependencyProvider.provideQuizStreamingSessionStore());
    viewModel = new ViewModelProvider(this, factory).get(DialogueQuizViewModel.class);
    viewModel.getUiState().observe(this, this::renderUiState);
  }

  private void bindListeners() {
    if (btnLeft != null) {
      btnLeft.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    if (btnRetry != null) {
      btnRetry.setOnClickListener(
          v -> {
            if (viewModel != null) {
              viewModel.retryLoad();
            }
          });
    }

    if (btnPrimary != null) {
      btnPrimary.setOnClickListener(
          v -> {
            if (bottomSheetUiStage != BottomSheetUiStage.NEXT_BUTTON_ONLY || viewModel == null) {
              return;
            }
            DialogueQuizViewModel.PrimaryActionResult result = viewModel.onPrimaryAction();
            if (result == DialogueQuizViewModel.PrimaryActionResult.WAITING_NEXT_QUESTION) {
              logDebug("quiz next requested: waiting for streamed question");
              showNextQuestionLoadingNotice();
              return;
            }

            hideNextQuestionLoadingNotice();
            if (result == DialogueQuizViewModel.PrimaryActionResult.MOVED_TO_NEXT) {
              // Do not cancel pending tasks here.
              // The next question render may already have scheduled showChoicesRunnable.
              logDebug("quiz next requested: moved to next question");
              hideBottomSheet();
              bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
              autoCheckPending = false;
              return;
            }
            if (result == DialogueQuizViewModel.PrimaryActionResult.COMPLETED) {
              logDebug("quiz next requested: completed");
              cancelPendingBottomSheetTasks();
              hideBottomSheet();
              bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
              autoCheckPending = false;
            }
          });
    }

    if (btnFinish != null) {
      btnFinish.setOnClickListener(
          v -> {
            navigateToMainActivity();
          });
    }
  }

  private void renderUiState(@NonNull DialogueQuizViewModel.QuizUiState state) {
    switch (state.getStatus()) {
      case LOADING:
        updateToolbarProgress(0, requestedQuestionCount);
        showOnly(loadingView);
        break;
      case ERROR:
        showOnly(errorView);
        renderError(state);
        break;
      case COMPLETED:
        showOnly(completedView);
        renderCompleted(state);
        break;
      case READY:
      default:
        showOnly(contentView);
        renderReady(state);
        break;
    }
  }

  private void renderError(@NonNull DialogueQuizViewModel.QuizUiState state) {
    if (tvErrorMessage == null) {
      return;
    }
    String message = state.getErrorMessage();
    if (message == null || message.trim().isEmpty()) {
      message = getString(R.string.quiz_error_default);
    }
    tvErrorMessage.setText(message);
  }

  private void renderCompleted(@NonNull DialogueQuizViewModel.QuizUiState state) {
    updateToolbarProgress(state.getTotalQuestions(), state.getTotalQuestions());
    if (tvCompletedSummary != null) {
      tvCompletedSummary.setText(
          getString(
              R.string.quiz_completed_summary_format,
              state.getCorrectAnswerCount(),
              state.getTotalQuestions()));
    }
    logDebug(
        "quiz completed render: correct="
            + state.getCorrectAnswerCount()
            + "/"
            + state.getTotalQuestions());
  }

  private void renderReady(@NonNull DialogueQuizViewModel.QuizUiState state) {
    DialogueQuizViewModel.QuizQuestionState questionState = state.getQuestionState();
    if (questionState == null || !questionState.isMultipleChoice()) {
      showOnly(errorView);
      if (tvErrorMessage != null) {
        tvErrorMessage.setText(getString(R.string.quiz_error_default));
      }
      return;
    }

    updateToolbarProgress(state.getCurrentQuestionNumber(), state.getTotalQuestions());
    if (tvQuestion != null) {
      tvQuestion.setText(questionState.getQuestionMain());
    }

    String questionMaterial = questionState.getQuestionMaterial();
    boolean hasQuestionMaterial = questionMaterial != null;
    if (cardQuestionMaterial != null) {
      cardQuestionMaterial.setVisibility(hasQuestionMaterial ? View.VISIBLE : View.GONE);
    }
    if (tvQuestionMaterial != null) {
      tvQuestionMaterial.setText(hasQuestionMaterial ? questionMaterial : "");
    }

    boolean questionChanged = renderedBottomSheetQuestionIndex != state.getCurrentQuestionIndex();
    if (questionChanged) {
      renderedBottomSheetQuestionIndex = state.getCurrentQuestionIndex();
      resetBottomSheetForNewQuestion();
    }

    renderResultCard(questionState);
    if (questionState.isChecked()) {
      autoCheckPending = false;
      if (bottomSheetUiStage != BottomSheetUiStage.NEXT_BUTTON_ONLY && !showNextButtonScheduled) {
        scheduleShowNextButton(state.getCurrentQuestionIndex(), state.isLastQuestion());
      }
    } else {
      if (questionChanged) {
        scheduleShowChoices(state.getCurrentQuestionIndex(), questionState);
      } else if (bottomSheetUiStage == BottomSheetUiStage.CHOICES_ONLY) {
        showBottomSheetChoices(questionState);
      }
    }

    if (lastLoggedQuestionIndex != state.getCurrentQuestionIndex()) {
      lastLoggedQuestionIndex = state.getCurrentQuestionIndex();
      logDebug(
          "quiz question render: index="
              + state.getCurrentQuestionNumber()
              + "/"
              + state.getTotalQuestions());
    }
  }

  private void resetBottomSheetForNewQuestion() {
    cancelPendingBottomSheetTasks();
    autoCheckPending = false;
    showNextButtonScheduled = false;
    bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
    if (choiceContainer != null) {
      choiceContainer.setVisibility(View.GONE);
    }
    if (inputAnswerLayout != null) {
      inputAnswerLayout.setVisibility(View.GONE);
    }
    if (btnPrimary != null) {
      btnPrimary.setVisibility(View.GONE);
    }
    hideNextQuestionLoadingNotice();
    hideBottomSheet();
  }

  private void renderChoiceButtons(@NonNull DialogueQuizViewModel.QuizQuestionState state) {
    LinearLayout container = choiceContainer;
    if (container == null) {
      return;
    }

    container.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(this);
    List<String> choices = state.getChoices();
    if (choices == null) {
      return;
    }

    String selectedChoice = state.getSelectedChoice();
    for (String choice : choices) {
      View item = inflater.inflate(R.layout.item_quiz_choice, container, false);
      MaterialButton button = item.findViewById(R.id.btn_quiz_choice);
      button.setText(choice);
      applyChoiceButtonStyle(button, state, choice, selectedChoice);
      if (!state.isChecked()
          && !autoCheckPending
          && bottomSheetUiStage == BottomSheetUiStage.CHOICES_ONLY) {
        button.setOnClickListener(v -> onChoiceSelected(choice));
      }
      container.addView(item);
    }
  }

  private void onChoiceSelected(@NonNull String choice) {
    if (viewModel == null
        || autoCheckPending
        || bottomSheetUiStage != BottomSheetUiStage.CHOICES_ONLY) {
      return;
    }
    autoCheckPending = true;
    viewModel.onChoiceSelected(choice);
    scheduleAutoCheck(renderedBottomSheetQuestionIndex);
  }

  private void applyChoiceButtonStyle(
      @NonNull MaterialButton button,
      @NonNull DialogueQuizViewModel.QuizQuestionState state,
      @NonNull String choice,
      @Nullable String selectedChoice) {
    Context context = button.getContext();
    int defaultBg = ContextCompat.getColor(context, R.color.color_background_2);
    int defaultStroke = ContextCompat.getColor(context, R.color.grey_300);
    int defaultText = ContextCompat.getColor(context, R.color.color_primary_text);
    int selectedBg = ContextCompat.getColor(context, R.color.color_background_3);
    int selectedStroke = ContextCompat.getColor(context, R.color.grey_300);
    int selectedText = ContextCompat.getColor(context, R.color.color_primary_text);

    boolean isSelected = normalize(choice).equals(normalize(selectedChoice));
    boolean isChecked = state.isChecked();
    boolean isCorrectChoice = normalize(choice).equals(normalize(state.getAnswer()));
    boolean isWrongSelected = isChecked && isSelected && !isCorrectChoice;

    int bgColor = isSelected ? selectedBg : defaultBg;
    int strokeColor = isSelected ? selectedStroke : defaultStroke;
    int textColor = isSelected ? selectedText : defaultText;

    if (isChecked && isCorrectChoice) {
      bgColor = ContextCompat.getColor(context, R.color.expression_natural_after_bg);
      strokeColor = ContextCompat.getColor(context, R.color.expression_natural_accent);
      textColor = ContextCompat.getColor(context, R.color.expression_natural_accent);
    } else if (isWrongSelected) {
      bgColor = ContextCompat.getColor(context, R.color.expression_precise_after_bg);
      strokeColor = ContextCompat.getColor(context, R.color.expression_precise_accent);
      textColor = ContextCompat.getColor(context, R.color.expression_precise_accent);
    }

    boolean enabled =
        !isChecked && !autoCheckPending && bottomSheetUiStage == BottomSheetUiStage.CHOICES_ONLY;
    button.setEnabled(enabled);
    button.setStrokeWidth(dpToPx(1));
    button.setStrokeColor(ColorStateList.valueOf(strokeColor));
    button.setBackgroundTintList(ColorStateList.valueOf(bgColor));
    button.setTextColor(textColor);
  }

  private void renderResultCard(@NonNull DialogueQuizViewModel.QuizQuestionState state) {
    if (resultCard != null) {
      resultCard.setVisibility(state.isChecked() ? View.VISIBLE : View.GONE);
    }
    if (!state.isChecked()) {
      return;
    }

    if (tvResultTitle != null) {
      int color =
          ContextCompat.getColor(
              this,
              state.isCorrect()
                  ? R.color.expression_natural_accent
                  : R.color.expression_precise_accent);
      tvResultTitle.setText(
          state.isCorrect() ? R.string.quiz_result_correct : R.string.quiz_result_incorrect);
      tvResultTitle.setTextColor(color);
    }
    if (tvCorrectAnswer != null) {
      tvCorrectAnswer.setText(getString(R.string.quiz_correct_answer_format, state.getAnswer()));
    }
    if (tvExplanation != null) {
      String explanation = state.getExplanation();
      if (explanation == null || explanation.trim().isEmpty()) {
        tvExplanation.setVisibility(View.GONE);
      } else {
        tvExplanation.setVisibility(View.VISIBLE);
        tvExplanation.setText(getString(R.string.quiz_explanation_format, explanation));
      }
    }
  }

  private void scheduleShowChoices(
      int questionIndex, @NonNull DialogueQuizViewModel.QuizQuestionState questionState) {
    if (showChoicesRunnable != null) {
      uiHandler.removeCallbacks(showChoicesRunnable);
      showChoicesRunnable = null;
    }
    Runnable runnable =
        () -> {
          showChoicesRunnable = null;
          if (isDestroyed() || renderedBottomSheetQuestionIndex != questionIndex) {
            return;
          }
          showBottomSheetChoices(questionState);
        };
    showChoicesRunnable = runnable;
    uiHandler.postDelayed(runnable, SHOW_CHOICES_DELAY_MS);
  }

  private void scheduleAutoCheck(int questionIndex) {
    if (autoCheckRunnable != null) {
      uiHandler.removeCallbacks(autoCheckRunnable);
      autoCheckRunnable = null;
    }
    Runnable runnable =
        () -> {
          autoCheckRunnable = null;
          if (isDestroyed() || renderedBottomSheetQuestionIndex != questionIndex) {
            autoCheckPending = false;
            return;
          }
          hideBottomSheet();
          bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
          if (choiceContainer != null) {
            choiceContainer.setVisibility(View.GONE);
          }
          if (viewModel != null) {
            viewModel.onPrimaryAction();
          } else {
            autoCheckPending = false;
          }
        };
    autoCheckRunnable = runnable;
    uiHandler.postDelayed(runnable, AUTO_CHECK_DELAY_MS);
  }

  private void scheduleShowNextButton(int questionIndex, boolean lastQuestion) {
    if (showNextButtonRunnable != null) {
      uiHandler.removeCallbacks(showNextButtonRunnable);
      showNextButtonRunnable = null;
    }
    showNextButtonScheduled = true;
    Runnable runnable =
        () -> {
          showNextButtonRunnable = null;
          showNextButtonScheduled = false;
          if (isDestroyed() || renderedBottomSheetQuestionIndex != questionIndex) {
            return;
          }
          showBottomSheetNextButton(lastQuestion);
        };
    showNextButtonRunnable = runnable;
    uiHandler.postDelayed(runnable, SHOW_NEXT_BUTTON_DELAY_MS);
  }

  private void showBottomSheetChoices(
      @NonNull DialogueQuizViewModel.QuizQuestionState questionState) {
    bottomSheetUiStage = BottomSheetUiStage.CHOICES_ONLY;
    if (choiceContainer != null) {
      choiceContainer.setVisibility(View.VISIBLE);
    }
    if (inputAnswerLayout != null) {
      inputAnswerLayout.setVisibility(View.GONE);
    }
    if (btnPrimary != null) {
      btnPrimary.setVisibility(View.GONE);
    }
    hideNextQuestionLoadingNotice();
    renderChoiceButtons(questionState);
    expandBottomSheet();
  }

  private void showBottomSheetNextButton(boolean lastQuestion) {
    bottomSheetUiStage = BottomSheetUiStage.NEXT_BUTTON_ONLY;
    autoCheckPending = false;
    if (choiceContainer != null) {
      choiceContainer.setVisibility(View.GONE);
    }
    if (inputAnswerLayout != null) {
      inputAnswerLayout.setVisibility(View.GONE);
    }
    if (btnPrimary != null) {
        // TODO: 틀린 경우에는 분홍색 버튼, 맞은 경우에는 초록색 버튼
      btnPrimary.setBackgroundColor(ContextCompat.getColor(this, R.color.card_purple_start));
      btnPrimary.setTextColor(ContextCompat.getColor(this, R.color.color_text_on_fixed_bg));
      btnPrimary.setVisibility(View.VISIBLE);
      btnPrimary.setEnabled(true);
      btnPrimary.setText(lastQuestion ? R.string.quiz_primary_finish : R.string.quiz_primary_next);
    }
    hideNextQuestionLoadingNotice();
    expandBottomSheet();
  }

  private void expandBottomSheet() {
    if (bottomSheetView == null) {
      return;
    }
    bottomSheetView.setVisibility(View.VISIBLE);
    if (bottomSheetBehavior != null) {
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }
  }

  private void hideBottomSheet() {
    if (bottomSheetBehavior != null) {
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
      return;
    }
    if (bottomSheetView != null) {
      bottomSheetView.setVisibility(View.GONE);
    }
  }

  private void cancelPendingBottomSheetTasks() {
    if (showChoicesRunnable != null) {
      uiHandler.removeCallbacks(showChoicesRunnable);
      showChoicesRunnable = null;
    }
    if (autoCheckRunnable != null) {
      uiHandler.removeCallbacks(autoCheckRunnable);
      autoCheckRunnable = null;
    }
    if (showNextButtonRunnable != null) {
      uiHandler.removeCallbacks(showNextButtonRunnable);
      showNextButtonRunnable = null;
    }
    showNextButtonScheduled = false;
  }

  private void showNextQuestionLoadingNotice() {
    if (tvNextQuestionLoadingNotice != null) {
      tvNextQuestionLoadingNotice.setVisibility(View.VISIBLE);
      tvNextQuestionLoadingNotice.setText(R.string.quiz_next_question_loading_notice);
    }
  }

  private void hideNextQuestionLoadingNotice() {
    if (tvNextQuestionLoadingNotice != null) {
      tvNextQuestionLoadingNotice.setVisibility(View.GONE);
    }
  }

  private void showOnly(@Nullable View target) {
    if (target != contentView) {
      cancelPendingBottomSheetTasks();
      bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
      renderedBottomSheetQuestionIndex = -1;
      autoCheckPending = false;
      hideNextQuestionLoadingNotice();
      hideBottomSheet();
    }
    setVisible(loadingView, loadingView == target);
    setVisible(errorView, errorView == target);
    setVisible(contentView, contentView == target);
    setVisible(completedView, completedView == target);
    setVisible(bottomSheetView, contentView == target);
  }

  private void setVisible(@Nullable View view, boolean visible) {
    if (view != null) {
      view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
  }

  private void configureToolbar() {
    if (tvTitle != null) {
      tvTitle.setText(R.string.quiz_toolbar_title);
    }
    updateToolbarProgress(0, requestedQuestionCount);
  }

  private void updateToolbarProgress(int current, int total) {
    int safeTotal = Math.max(1, total);
    int safeCurrent = Math.max(0, Math.min(current, safeTotal));
    if (tvProgress != null) {
      tvProgress.setText(getString(R.string.quiz_progress_format, safeCurrent, safeTotal));
    }
    if (progressBar != null) {
      progressBar.setMax(safeTotal);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        progressBar.setProgress(safeCurrent, true);
      } else {
        progressBar.setProgress(safeCurrent);
      }
    }
  }

  private int dpToPx(int dp) {
    float density = getResources().getDisplayMetrics().density;
    return (int) (dp * density);
  }

  @NonNull
  private static String normalize(@Nullable String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private void showExitConfirmDialog() {
    android.app.Dialog dialog = new android.app.Dialog(this);
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
    dialog.setContentView(R.layout.dialog_exit_confirm);

    if (dialog.getWindow() != null) {
      dialog
          .getWindow()
          .setBackgroundDrawable(
              new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
      android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
      int dialogWidth = (int) (metrics.widthPixels * 0.9);
      dialog.getWindow().setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    TextView tvHeader = dialog.findViewById(R.id.tv_header);
    TextView tvMessage = dialog.findViewById(R.id.tv_message);
    if (tvHeader != null) {
      tvHeader.setText("퀴즈 종료");
    }
    if (tvMessage != null) {
      tvMessage.setText("현재 진행 중인 퀴즈 내역이 저장되지 않아요.\n정말 종료할까요?");
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

  private void logDebug(@NonNull String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }
}
