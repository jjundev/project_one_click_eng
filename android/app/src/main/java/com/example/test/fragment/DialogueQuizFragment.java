package com.example.test.fragment;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.test.BuildConfig;
import com.example.test.R;
import com.example.test.fragment.dialoguelearning.di.LearningDependencyProvider;
import com.example.test.settings.AppSettings;
import com.example.test.settings.AppSettingsStore;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import java.util.List;

public class DialogueQuizFragment extends Fragment {
  public static final String ARG_SUMMARY_JSON = "arg_summary_json";
  public static final String ARG_FEATURE_BUNDLE_JSON = "arg_feature_bundle_json";
  public static final String ARG_REQUESTED_QUESTION_COUNT = "arg_requested_question_count";
  public static final String ARG_FINISH_BEHAVIOR = "arg_finish_behavior";

  public static final int FINISH_ACTIVITY = 0;
  public static final int POP_BACK_STACK = 1;
  private static final String TAG = "JOB_J-20260217-002";
  private static final long SHOW_CHOICES_DELAY_MS = 500L;
  private static final long AUTO_CHECK_DELAY_MS = 200L;
  private static final long SHOW_NEXT_BUTTON_DELAY_MS = 500L;

  private enum BottomSheetUiStage {
    HIDDEN,
    CHOICES_ONLY,
    NEXT_BUTTON_ONLY
  }

  @Nullable
  private DialogueQuizViewModel viewModel;
  @Nullable
  private View loadingView;
  @Nullable
  private View errorView;
  @Nullable
  private View contentView;
  @Nullable
  private View completedView;
  @Nullable
  private View bottomSheetView;
  @Nullable
  private BottomSheetBehavior<View> bottomSheetBehavior;
  @Nullable
  private TextView tvErrorMessage;
  @Nullable
  private MaterialButton btnRetry;
  @Nullable
  private TextView tvProgress;
  @Nullable
  private ProgressBar progressBar;
  @Nullable
  private TextView tvQuestion;
  @Nullable
  private LinearLayout choiceContainer;
  @Nullable
  private TextInputLayout inputAnswerLayout;
  @Nullable
  private MaterialCardView resultCard;
  @Nullable
  private TextView tvResultTitle;
  @Nullable
  private TextView tvCorrectAnswer;
  @Nullable
  private TextView tvExplanation;
  @Nullable
  private MaterialButton btnPrimary;
  @Nullable
  private TextView tvCompletedSummary;
  @Nullable
  private MaterialButton btnFinish;

  @NonNull
  private final Handler uiHandler = new Handler(Looper.getMainLooper());
  @Nullable
  private Runnable showChoicesRunnable;
  @Nullable
  private Runnable autoCheckRunnable;
  @Nullable
  private Runnable showNextButtonRunnable;

  private BottomSheetUiStage bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
  private int renderedBottomSheetQuestionIndex = -1;
  private int lastLoggedQuestionIndex = -1;
  private boolean autoCheckPending = false;
  private boolean showNextButtonScheduled = false;

  public static DialogueQuizFragment newInstance(String summaryJson) {
    return newInstance(summaryJson, null);
  }

  public static DialogueQuizFragment newInstance(
      @Nullable String summaryJson, @Nullable String featureBundleJson) {
    DialogueQuizFragment fragment = new DialogueQuizFragment();
    Bundle args = new Bundle();
    args.putString(ARG_SUMMARY_JSON, summaryJson);
    args.putString(ARG_FEATURE_BUNDLE_JSON, featureBundleJson);
    fragment.setArguments(args);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_dialogue_quiz, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    bindViews(view);
    initViewModel();
    bindListeners();

    Bundle args = getArguments();
    String summaryJson = args == null ? null : args.getString(ARG_SUMMARY_JSON);
    int requestedQuestionCount = args == null ? 5 : args.getInt(ARG_REQUESTED_QUESTION_COUNT, 5);
    if (viewModel != null) {
      viewModel.initialize(summaryJson, requestedQuestionCount);
    }
    logDebug("quiz fragment entered");
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    cancelPendingBottomSheetTasks();
    loadingView = null;
    errorView = null;
    contentView = null;
    completedView = null;
    bottomSheetView = null;
    bottomSheetBehavior = null;
    tvErrorMessage = null;
    btnRetry = null;
    tvProgress = null;
    progressBar = null;
    tvQuestion = null;
    choiceContainer = null;
    inputAnswerLayout = null;
    resultCard = null;
    tvResultTitle = null;
    tvCorrectAnswer = null;
    tvExplanation = null;
    btnPrimary = null;
    tvCompletedSummary = null;
    btnFinish = null;
    bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
    renderedBottomSheetQuestionIndex = -1;
    autoCheckPending = false;
    showNextButtonScheduled = false;
  }

  private void bindViews(@NonNull View root) {
    loadingView = root.findViewById(R.id.layout_quiz_loading);
    errorView = root.findViewById(R.id.layout_quiz_error);
    contentView = root.findViewById(R.id.layout_quiz_content);
    completedView = root.findViewById(R.id.layout_quiz_completed);
    bottomSheetView = root.findViewById(R.id.bottom_sheet_quiz);
    tvErrorMessage = root.findViewById(R.id.tv_quiz_error_message);
    btnRetry = root.findViewById(R.id.btn_quiz_retry);
    tvProgress = root.findViewById(R.id.tv_quiz_progress);
    progressBar = root.findViewById(R.id.progress_quiz);
    tvQuestion = root.findViewById(R.id.tv_quiz_question);
    choiceContainer = root.findViewById(R.id.layout_quiz_choice_container);
    inputAnswerLayout = root.findViewById(R.id.layout_quiz_answer_input);
    resultCard = root.findViewById(R.id.card_quiz_result);
    tvResultTitle = root.findViewById(R.id.tv_quiz_result_title);
    tvCorrectAnswer = root.findViewById(R.id.tv_quiz_correct_answer);
    tvExplanation = root.findViewById(R.id.tv_quiz_explanation);
    btnPrimary = root.findViewById(R.id.btn_quiz_primary);
    tvCompletedSummary = root.findViewById(R.id.tv_quiz_completed_summary);
    btnFinish = root.findViewById(R.id.btn_quiz_finish);

    if (bottomSheetView != null) {
      bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetView);
      bottomSheetBehavior.setHideable(true);
      bottomSheetBehavior.setSkipCollapsed(true);
      bottomSheetBehavior.setDraggable(false);
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }
  }

  private void initViewModel() {
    AppSettings settings = new AppSettingsStore(requireContext().getApplicationContext()).getSettings();
    DialogueQuizViewModelFactory factory =
        new DialogueQuizViewModelFactory(
            LearningDependencyProvider.provideQuizGenerationManager(
                requireContext().getApplicationContext(),
                settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
                settings.getLlmModelSummary()));
    viewModel = new ViewModelProvider(this, factory).get(DialogueQuizViewModel.class);
    viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderUiState);
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

    if (btnPrimary != null) {
      btnPrimary.setOnClickListener(
          v -> {
            if (bottomSheetUiStage != BottomSheetUiStage.NEXT_BUTTON_ONLY || viewModel == null) {
              return;
            }
            cancelPendingBottomSheetTasks();
            hideBottomSheet();
            bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
            autoCheckPending = false;
            viewModel.onPrimaryAction();
          });
    }

    if (btnFinish != null) {
      btnFinish.setOnClickListener(
          v -> {
            Bundle bundle = getArguments();
            int finishBehavior =
                bundle != null
                    ? bundle.getInt(ARG_FINISH_BEHAVIOR, FINISH_ACTIVITY)
                    : FINISH_ACTIVITY;
            if (finishBehavior == POP_BACK_STACK) {
              if (getActivity() != null) {
                getActivity().getOnBackPressedDispatcher().onBackPressed();
              }
            } else {
              if (getActivity() != null) {
                getActivity().finish();
              }
            }
          });
    }
  }

  private void renderUiState(@NonNull DialogueQuizViewModel.QuizUiState state) {
    switch (state.getStatus()) {
      case LOADING:
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

    if (tvProgress != null) {
      tvProgress.setText(
          getString(
              R.string.quiz_progress_format,
              state.getCurrentQuestionNumber(),
              state.getTotalQuestions()));
    }
    if (progressBar != null) {
      progressBar.setMax(Math.max(1, state.getTotalQuestions()));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        progressBar.setProgress(state.getCurrentQuestionNumber(), true);
      } else {
        progressBar.setProgress(state.getCurrentQuestionNumber());
      }
    }
    if (tvQuestion != null) {
      tvQuestion.setText(questionState.getQuestion());
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
    hideBottomSheet();
  }

  private void renderChoiceButtons(@NonNull DialogueQuizViewModel.QuizQuestionState state) {
    LinearLayout container = choiceContainer;
    Context context = getContext();
    if (container == null || context == null) {
      return;
    }

    container.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(context);
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
    int defaultBg = ContextCompat.getColor(context, R.color.white);
    int defaultStroke = ContextCompat.getColor(context, R.color.grey_300);
    int defaultText = ContextCompat.getColor(context, R.color.text_primary);
    int selectedBg = ContextCompat.getColor(context, R.color.shorts_progress_active);
    int selectedStroke = ContextCompat.getColor(context, R.color.toss_blue);
    int selectedText = ContextCompat.getColor(context, R.color.toss_blue);

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
              requireContext(),
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
          if (!isViewReady() || renderedBottomSheetQuestionIndex != questionIndex) {
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
          if (!isViewReady() || renderedBottomSheetQuestionIndex != questionIndex) {
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
          if (!isViewReady() || renderedBottomSheetQuestionIndex != questionIndex) {
            return;
          }
          showBottomSheetNextButton(lastQuestion);
        };
    showNextButtonRunnable = runnable;
    uiHandler.postDelayed(runnable, SHOW_NEXT_BUTTON_DELAY_MS);
  }

  private void showBottomSheetChoices(@NonNull DialogueQuizViewModel.QuizQuestionState questionState) {
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
      btnPrimary.setVisibility(View.VISIBLE);
      btnPrimary.setEnabled(true);
      btnPrimary.setText(lastQuestion ? R.string.quiz_primary_finish : R.string.quiz_primary_next);
    }
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

  private boolean isViewReady() {
    return isAdded() && getView() != null;
  }

  private void showOnly(@Nullable View target) {
    if (target != contentView) {
      cancelPendingBottomSheetTasks();
      bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
      renderedBottomSheetQuestionIndex = -1;
      autoCheckPending = false;
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

  private int dpToPx(int dp) {
    float density = getResources().getDisplayMetrics().density;
    return (int) (dp * density);
  }

  @NonNull
  private static String normalize(@Nullable String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private void logDebug(@NonNull String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }
}
