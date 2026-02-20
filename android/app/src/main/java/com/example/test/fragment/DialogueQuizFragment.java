package com.example.test.fragment;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
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
  private TextInputEditText etAnswer;
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
  @Nullable
  private TextWatcher answerWatcher;
  private boolean suppressAnswerWatcher = false;
  private int lastLoggedQuestionIndex = -1;

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
    if (etAnswer != null && answerWatcher != null) {
      etAnswer.removeTextChangedListener(answerWatcher);
    }
    answerWatcher = null;
    loadingView = null;
    errorView = null;
    contentView = null;
    completedView = null;
    tvErrorMessage = null;
    btnRetry = null;
    tvProgress = null;
    progressBar = null;
    tvQuestion = null;
    choiceContainer = null;
    inputAnswerLayout = null;
    etAnswer = null;
    resultCard = null;
    tvResultTitle = null;
    tvCorrectAnswer = null;
    tvExplanation = null;
    btnPrimary = null;
    tvCompletedSummary = null;
    btnFinish = null;
    suppressAnswerWatcher = false;
  }

  private void bindViews(@NonNull View root) {
    loadingView = root.findViewById(R.id.layout_quiz_loading);
    errorView = root.findViewById(R.id.layout_quiz_error);
    contentView = root.findViewById(R.id.layout_quiz_content);
    completedView = root.findViewById(R.id.layout_quiz_completed);
    tvErrorMessage = root.findViewById(R.id.tv_quiz_error_message);
    btnRetry = root.findViewById(R.id.btn_quiz_retry);
    tvProgress = root.findViewById(R.id.tv_quiz_progress);
    progressBar = root.findViewById(R.id.progress_quiz);
    tvQuestion = root.findViewById(R.id.tv_quiz_question);
    choiceContainer = root.findViewById(R.id.layout_quiz_choice_container);
    inputAnswerLayout = root.findViewById(R.id.layout_quiz_answer_input);
    etAnswer = root.findViewById(R.id.et_quiz_answer);
    resultCard = root.findViewById(R.id.card_quiz_result);
    tvResultTitle = root.findViewById(R.id.tv_quiz_result_title);
    tvCorrectAnswer = root.findViewById(R.id.tv_quiz_correct_answer);
    tvExplanation = root.findViewById(R.id.tv_quiz_explanation);
    btnPrimary = root.findViewById(R.id.btn_quiz_primary);
    tvCompletedSummary = root.findViewById(R.id.tv_quiz_completed_summary);
    btnFinish = root.findViewById(R.id.btn_quiz_finish);
  }

  private void initViewModel() {
    AppSettings settings = new AppSettingsStore(requireContext().getApplicationContext()).getSettings();
    DialogueQuizViewModelFactory factory = new DialogueQuizViewModelFactory(
        LearningDependencyProvider.provideQuizGenerationManager(
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
            if (viewModel != null) {
              viewModel.onPrimaryAction();
            }
          });
    }

    if (btnFinish != null) {
      btnFinish.setOnClickListener(
          v -> {
            Bundle bundle = getArguments();
            int finishBehavior = bundle != null ? bundle.getInt(ARG_FINISH_BEHAVIOR, FINISH_ACTIVITY) : FINISH_ACTIVITY;
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

    if (etAnswer != null) {
      answerWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
          // No-op.
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
          if (suppressAnswerWatcher || viewModel == null) {
            return;
          }
          viewModel.onAnswerInputChanged(s == null ? null : s.toString());
        }

        @Override
        public void afterTextChanged(Editable s) {
          // No-op.
        }
      };
      etAnswer.addTextChangedListener(answerWatcher);
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
    if (questionState == null) {
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

    renderAnswerInput(questionState);
    renderResultCard(questionState);
    renderPrimaryAction(questionState, state.isLastQuestion());

    if (lastLoggedQuestionIndex != state.getCurrentQuestionIndex()) {
      lastLoggedQuestionIndex = state.getCurrentQuestionIndex();
      logDebug(
          "quiz question render: index="
              + state.getCurrentQuestionNumber()
              + "/"
              + state.getTotalQuestions());
    }
  }

  private void renderAnswerInput(@NonNull DialogueQuizViewModel.QuizQuestionState state) {
    boolean multipleChoice = state.isMultipleChoice();
    if (choiceContainer != null) {
      choiceContainer.setVisibility(multipleChoice ? View.VISIBLE : View.GONE);
    }
    if (inputAnswerLayout != null) {
      inputAnswerLayout.setVisibility(multipleChoice ? View.GONE : View.VISIBLE);
    }

    if (multipleChoice) {
      renderChoiceButtons(state);
      clearAnswerText();
      return;
    }

    if (etAnswer != null) {
      etAnswer.setEnabled(!state.isChecked());
      String desired = state.getTypedAnswer() == null ? "" : state.getTypedAnswer();
      String current = etAnswer.getText() == null ? "" : etAnswer.getText().toString();
      if (!current.equals(desired)) {
        suppressAnswerWatcher = true;
        etAnswer.setText(desired);
        etAnswer.setSelection(desired.length());
        suppressAnswerWatcher = false;
      }
    }
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
      if (!state.isChecked()) {
        button.setOnClickListener(
            v -> {
              if (viewModel != null) {
                viewModel.onChoiceSelected(choice);
              }
            });
      }
      container.addView(item);
    }
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

    button.setEnabled(!isChecked);
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
      int color = ContextCompat.getColor(
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

  private void renderPrimaryAction(
      @NonNull DialogueQuizViewModel.QuizQuestionState questionState, boolean lastQuestion) {
    if (btnPrimary == null) {
      return;
    }
    DialogueQuizViewModel.QuizQuestionState.PrimaryAction action = questionState.resolvePrimaryAction(lastQuestion);
    int labelRes;
    switch (action) {
      case NEXT:
        labelRes = R.string.quiz_primary_next;
        break;
      case FINISH:
        labelRes = R.string.quiz_primary_finish;
        break;
      case CHECK:
      default:
        labelRes = R.string.quiz_primary_check;
        break;
    }
    btnPrimary.setText(labelRes);
    btnPrimary.setEnabled(questionState.isPrimaryActionEnabled());
  }

  private void showOnly(@Nullable View target) {
    setVisible(loadingView, loadingView == target);
    setVisible(errorView, errorView == target);
    setVisible(contentView, contentView == target);
    setVisible(completedView, completedView == target);
  }

  private void setVisible(@Nullable View view, boolean visible) {
    if (view != null) {
      view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
  }

  private void clearAnswerText() {
    if (etAnswer == null) {
      return;
    }
    String current = etAnswer.getText() == null ? "" : etAnswer.getText().toString();
    if (current.isEmpty()) {
      return;
    }
    suppressAnswerWatcher = true;
    etAnswer.setText("");
    suppressAnswerWatcher = false;
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
