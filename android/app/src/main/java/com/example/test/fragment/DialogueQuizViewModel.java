package com.example.test.fragment;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.test.BuildConfig;
import com.example.test.fragment.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.example.test.fragment.dialoguelearning.model.QuizData;
import com.example.test.fragment.dialoguelearning.model.SummaryData;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DialogueQuizViewModel extends ViewModel {
  private static final String TAG = "JOB_J-20260217-002";
  private static final String DEFAULT_QUIZ_ERROR =
      "Quiz questions are unavailable right now. Please try again.";

  private final IQuizGenerationManager quizGenerationManager;
  private final Gson gson = new Gson();
  private final MutableLiveData<QuizUiState> uiState = new MutableLiveData<>(QuizUiState.loading());

  @Nullable private SummaryData summaryData;
  @NonNull private List<QuizData.QuizQuestion> questions = new ArrayList<>();
  @Nullable private QuizQuestionState currentQuestionState;
  private int currentQuestionIndex = 0;
  private int correctAnswerCount = 0;
  private boolean hasInitialized = false;
  private int maxQuestions = 5;

  public DialogueQuizViewModel(@NonNull IQuizGenerationManager quizGenerationManager) {
    this.quizGenerationManager = quizGenerationManager;
  }

  public LiveData<QuizUiState> getUiState() {
    return uiState;
  }

  public void initialize(@Nullable String summaryJson) {
    initialize(summaryJson, 5);
  }

  public void initialize(@Nullable String summaryJson, int requestedQuestionCount) {
    if (hasInitialized) {
      return;
    }
    hasInitialized = true;
    maxQuestions = Math.max(1, Math.min(10, requestedQuestionCount));
    summaryData = resolveSummaryData(summaryJson);

    uiState.setValue(QuizUiState.loading());
    quizGenerationManager.initializeCache(
        new IQuizGenerationManager.InitCallback() {
          @Override
          public void onReady() {
            loadQuizQuestions();
          }

          @Override
          public void onError(@NonNull String error) {
            logDebug("Quiz cache init error: " + error);
            loadQuizQuestions();
          }
        });
  }

  public void retryLoad() {
    loadQuizQuestions();
  }

  public void onChoiceSelected(@Nullable String choice) {
    if (currentQuestionState == null || currentQuestionState.isChecked()) {
      return;
    }
    currentQuestionState = currentQuestionState.withChoice(choice);
    publishCurrentQuestionState();
  }

  public void onAnswerInputChanged(@Nullable String answer) {
    if (currentQuestionState == null || currentQuestionState.isChecked()) {
      return;
    }
    currentQuestionState = currentQuestionState.withTypedAnswer(answer);
    publishCurrentQuestionState();
  }

  public void onPrimaryAction() {
    if (currentQuestionState == null) {
      return;
    }

    if (!currentQuestionState.isChecked()) {
      checkCurrentAnswer();
      return;
    }

    moveToNextQuestionOrComplete();
  }

  private void loadQuizQuestions() {
    SummaryData seed = summaryData;
    if (seed == null) {
      uiState.setValue(QuizUiState.error(DEFAULT_QUIZ_ERROR));
      return;
    }

    // Loading state is already set in initialize, but we can set it again if
    // retried.
    uiState.postValue(QuizUiState.loading());
    logDebug("quiz load start");
    quizGenerationManager.generateQuizFromSummaryAsync(
        seed,
        maxQuestions,
        new IQuizGenerationManager.QuizCallback() {
          @Override
          public void onSuccess(@NonNull List<QuizData.QuizQuestion> rawQuestions) {
            questions = sanitizeQuestions(rawQuestions);
            if (questions.isEmpty()) {
              uiState.setValue(QuizUiState.error(DEFAULT_QUIZ_ERROR));
              logDebug("quiz load failed: empty_questions");
              return;
            }

            currentQuestionIndex = 0;
            correctAnswerCount = 0;
            currentQuestionState = QuizQuestionState.from(questions.get(0));
            publishCurrentQuestionState();
            logDebug("quiz load success: count=" + questions.size());
          }

          @Override
          public void onFailure(@NonNull String error) {
            String safeError = trimToNull(error);
            uiState.setValue(QuizUiState.error(firstNonBlank(safeError, DEFAULT_QUIZ_ERROR)));
            logDebug("quiz load failed: " + firstNonBlank(safeError, "unknown"));
          }
        });
  }

  private void checkCurrentAnswer() {
    QuizQuestionState current = currentQuestionState;
    if (current == null) {
      return;
    }

    String submitted = current.getSubmittedAnswer();
    if (isBlank(submitted)) {
      return;
    }

    boolean isCorrect = normalize(submitted).equals(normalize(current.getAnswer()));
    if (isCorrect) {
      correctAnswerCount++;
    }

    currentQuestionState = current.withCheckResult(isCorrect);
    publishCurrentQuestionState();
    logDebug(
        "quiz check: index="
            + (currentQuestionIndex + 1)
            + "/"
            + questions.size()
            + ", correct="
            + isCorrect);
  }

  private void moveToNextQuestionOrComplete() {
    if (questions.isEmpty()) {
      uiState.setValue(QuizUiState.error(DEFAULT_QUIZ_ERROR));
      return;
    }

    if (currentQuestionIndex >= questions.size() - 1) {
      uiState.setValue(QuizUiState.completed(questions.size(), correctAnswerCount));
      logDebug("quiz completed: correct=" + correctAnswerCount + "/" + questions.size());
      return;
    }

    currentQuestionIndex++;
    currentQuestionState = QuizQuestionState.from(questions.get(currentQuestionIndex));
    publishCurrentQuestionState();
    logDebug("quiz next question: index=" + (currentQuestionIndex + 1) + "/" + questions.size());
  }

  private void publishCurrentQuestionState() {
    QuizQuestionState state = currentQuestionState;
    if (state == null || questions.isEmpty()) {
      uiState.setValue(QuizUiState.error(DEFAULT_QUIZ_ERROR));
      return;
    }

    boolean isLastQuestion = currentQuestionIndex >= questions.size() - 1;
    uiState.setValue(
        QuizUiState.ready(
            state, currentQuestionIndex, questions.size(), correctAnswerCount, isLastQuestion));
  }

  @NonNull
  private List<QuizData.QuizQuestion> sanitizeQuestions(
      @Nullable List<QuizData.QuizQuestion> sourceQuestions) {
    List<QuizData.QuizQuestion> result = new ArrayList<>();
    if (sourceQuestions == null || sourceQuestions.isEmpty()) {
      return result;
    }

    Set<String> seenQuestions = new HashSet<>();
    for (QuizData.QuizQuestion item : sourceQuestions) {
      if (item == null) {
        continue;
      }

      String question = trimToNull(item.getQuestion());
      String answer = trimToNull(item.getAnswer());
      if (question == null || answer == null) {
        continue;
      }

      String dedupeKey = normalize(question);
      if (!seenQuestions.add(dedupeKey)) {
        continue;
      }

      List<String> choices = sanitizeChoices(item.getChoices(), answer);
      String explanation = trimToNull(item.getExplanation());
      result.add(new QuizData.QuizQuestion(question, answer, choices, explanation));
      if (result.size() >= maxQuestions) {
        break;
      }
    }
    return result;
  }

  @Nullable
  private List<String> sanitizeChoices(
      @Nullable List<String> sourceChoices, @NonNull String answer) {
    if (sourceChoices == null || sourceChoices.isEmpty()) {
      return null;
    }

    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String rawChoice : sourceChoices) {
      String choice = trimToNull(rawChoice);
      if (choice == null) {
        continue;
      }
      String normalized = normalize(choice);
      if (!seen.add(normalized)) {
        continue;
      }
      result.add(choice);
    }

    if (!seen.contains(normalize(answer))) {
      result.add(answer);
    }

    if (result.size() < 2) {
      return null;
    }
    return result;
  }

  @Nullable
  private SummaryData resolveSummaryData(@Nullable String summaryJson) {
    String safeJson = trimToNull(summaryJson);
    if (safeJson == null) {
      return new SummaryData();
    }

    try {
      SummaryData parsed = gson.fromJson(safeJson, SummaryData.class);
      return parsed == null ? new SummaryData() : parsed;
    } catch (Exception ignored) {
      return new SummaryData();
    }
  }

  @Nullable
  private static String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static boolean isBlank(@Nullable String value) {
    return trimToNull(value) == null;
  }

  @NonNull
  private static String normalize(@Nullable String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  @NonNull
  private static String firstNonBlank(@Nullable String primary, @NonNull String fallback) {
    return isBlank(primary) ? fallback : primary.trim();
  }

  private void logDebug(@NonNull String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }

  public static final class QuizUiState {
    public enum Status {
      LOADING,
      ERROR,
      READY,
      COMPLETED
    }

    @NonNull private final Status status;
    @Nullable private final String errorMessage;
    @Nullable private final QuizQuestionState questionState;
    private final int currentQuestionIndex;
    private final int totalQuestions;
    private final int correctAnswerCount;
    private final boolean lastQuestion;

    private QuizUiState(
        @NonNull Status status,
        @Nullable String errorMessage,
        @Nullable QuizQuestionState questionState,
        int currentQuestionIndex,
        int totalQuestions,
        int correctAnswerCount,
        boolean lastQuestion) {
      this.status = status;
      this.errorMessage = errorMessage;
      this.questionState = questionState;
      this.currentQuestionIndex = currentQuestionIndex;
      this.totalQuestions = totalQuestions;
      this.correctAnswerCount = correctAnswerCount;
      this.lastQuestion = lastQuestion;
    }

    @NonNull
    public static QuizUiState loading() {
      return new QuizUiState(Status.LOADING, null, null, 0, 0, 0, false);
    }

    @NonNull
    public static QuizUiState error(@NonNull String message) {
      return new QuizUiState(Status.ERROR, message, null, 0, 0, 0, false);
    }

    @NonNull
    public static QuizUiState ready(
        @NonNull QuizQuestionState questionState,
        int currentQuestionIndex,
        int totalQuestions,
        int correctAnswerCount,
        boolean lastQuestion) {
      return new QuizUiState(
          Status.READY,
          null,
          questionState,
          currentQuestionIndex,
          totalQuestions,
          correctAnswerCount,
          lastQuestion);
    }

    @NonNull
    public static QuizUiState completed(int totalQuestions, int correctAnswerCount) {
      return new QuizUiState(
          Status.COMPLETED,
          null,
          null,
          totalQuestions == 0 ? 0 : totalQuestions - 1,
          totalQuestions,
          correctAnswerCount,
          true);
    }

    @NonNull
    public Status getStatus() {
      return status;
    }

    @Nullable
    public String getErrorMessage() {
      return errorMessage;
    }

    @Nullable
    public QuizQuestionState getQuestionState() {
      return questionState;
    }

    public int getCurrentQuestionIndex() {
      return currentQuestionIndex;
    }

    public int getCurrentQuestionNumber() {
      return currentQuestionIndex + 1;
    }

    public int getTotalQuestions() {
      return totalQuestions;
    }

    public int getCorrectAnswerCount() {
      return correctAnswerCount;
    }

    public boolean isLastQuestion() {
      return lastQuestion;
    }
  }

  public static final class QuizQuestionState {
    public enum PrimaryAction {
      CHECK,
      NEXT,
      FINISH
    }

    @NonNull private final String question;
    @NonNull private final String answer;
    @Nullable private final List<String> choices;
    @Nullable private final String explanation;
    @Nullable private final String selectedChoice;
    @Nullable private final String typedAnswer;
    private final boolean checked;
    private final boolean correct;

    private QuizQuestionState(
        @NonNull String question,
        @NonNull String answer,
        @Nullable List<String> choices,
        @Nullable String explanation,
        @Nullable String selectedChoice,
        @Nullable String typedAnswer,
        boolean checked,
        boolean correct) {
      this.question = question;
      this.answer = answer;
      this.choices = choices;
      this.explanation = explanation;
      this.selectedChoice = selectedChoice;
      this.typedAnswer = typedAnswer;
      this.checked = checked;
      this.correct = correct;
    }

    @NonNull
    public static QuizQuestionState from(@NonNull QuizData.QuizQuestion source) {
      return new QuizQuestionState(
          firstNonBlank(trimToNull(source.getQuestion()), ""),
          firstNonBlank(trimToNull(source.getAnswer()), ""),
          source.getChoices(),
          trimToNull(source.getExplanation()),
          null,
          null,
          false,
          false);
    }

    @NonNull
    public QuizQuestionState withChoice(@Nullable String choice) {
      return new QuizQuestionState(
          question, answer, choices, explanation, trimToNull(choice), null, checked, correct);
    }

    @NonNull
    public QuizQuestionState withTypedAnswer(@Nullable String answerInput) {
      return new QuizQuestionState(
          question, answer, choices, explanation, null, trimToNull(answerInput), checked, correct);
    }

    @NonNull
    public QuizQuestionState withCheckResult(boolean isCorrect) {
      return new QuizQuestionState(
          question, answer, choices, explanation, selectedChoice, typedAnswer, true, isCorrect);
    }

    @NonNull
    public String getQuestion() {
      return question;
    }

    @NonNull
    public String getAnswer() {
      return answer;
    }

    @Nullable
    public List<String> getChoices() {
      return choices;
    }

    @Nullable
    public String getExplanation() {
      return explanation;
    }

    @Nullable
    public String getSelectedChoice() {
      return selectedChoice;
    }

    @Nullable
    public String getTypedAnswer() {
      return typedAnswer;
    }

    public boolean isChecked() {
      return checked;
    }

    public boolean isCorrect() {
      return correct;
    }

    public boolean isMultipleChoice() {
      return choices != null && !choices.isEmpty();
    }

    @Nullable
    public String getSubmittedAnswer() {
      return isMultipleChoice() ? selectedChoice : typedAnswer;
    }

    @NonNull
    public PrimaryAction resolvePrimaryAction(boolean lastQuestion) {
      if (!checked) {
        return PrimaryAction.CHECK;
      }
      return lastQuestion ? PrimaryAction.FINISH : PrimaryAction.NEXT;
    }

    public boolean isPrimaryActionEnabled() {
      return checked || !isBlank(getSubmittedAnswer());
    }
  }
}
