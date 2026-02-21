package com.jjundev.oneclickeng.learning.quiz;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.gson.Gson;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.QuizData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DialogueQuizViewModel extends ViewModel {
  private static final String TAG = "JOB_J-20260217-002";
  private static final String DEFAULT_QUIZ_ERROR =
      "Quiz questions are unavailable right now. Please try again.";

  public enum PrimaryActionResult {
    NONE,
    CHECKED,
    MOVED_TO_NEXT,
    WAITING_NEXT_QUESTION,
    COMPLETED
  }

  private final IQuizGenerationManager quizGenerationManager;
  private final Gson gson = new Gson();
  private final MutableLiveData<QuizUiState> uiState = new MutableLiveData<>(QuizUiState.loading());

  @Nullable private SummaryData summaryData;
  @NonNull private List<QuizData.QuizQuestion> questions = new ArrayList<>();
  @NonNull private final Set<String> seenQuestionKeys = new HashSet<>();
  @Nullable private QuizQuestionState currentQuestionState;
  private int currentQuestionIndex = 0;
  private int correctAnswerCount = 0;
  private boolean hasInitialized = false;
  private int requestedQuestionCount = 5;
  private boolean streamCompleted = false;
  private long activeLoadRequestId = 0L;

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
    this.requestedQuestionCount = Math.max(1, Math.min(10, requestedQuestionCount));
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

  @NonNull
  public PrimaryActionResult onPrimaryAction() {
    if (currentQuestionState == null) {
      return PrimaryActionResult.NONE;
    }

    if (!currentQuestionState.isChecked()) {
      return checkCurrentAnswer() ? PrimaryActionResult.CHECKED : PrimaryActionResult.NONE;
    }

    return moveToNextQuestionOrComplete();
  }

  private void loadQuizQuestions() {
    SummaryData seed = summaryData;
    if (seed == null) {
      uiState.setValue(QuizUiState.error(DEFAULT_QUIZ_ERROR));
      return;
    }

    long requestId = ++activeLoadRequestId;
    streamCompleted = false;
    currentQuestionIndex = 0;
    correctAnswerCount = 0;
    currentQuestionState = null;
    questions = new ArrayList<>();
    seenQuestionKeys.clear();
    uiState.postValue(QuizUiState.loading());
    logDebug("quiz load start");
    quizGenerationManager.generateQuizFromSummaryStreamingAsync(
        seed,
        requestedQuestionCount,
        new IQuizGenerationManager.QuizStreamingCallback() {
          @Override
          public void onQuestion(@NonNull QuizData.QuizQuestion rawQuestion) {
            if (requestId != activeLoadRequestId) {
              return;
            }
            QuizData.QuizQuestion question = sanitizeQuestion(rawQuestion);
            if (question == null) {
              return;
            }
            questions.add(question);

            if (questions.size() == 1) {
              currentQuestionIndex = 0;
              currentQuestionState = QuizQuestionState.from(question);
              publishCurrentQuestionState();
              logDebug("quiz first question ready");
            }
          }

          @Override
          public void onComplete(@Nullable String warningMessage) {
            if (requestId != activeLoadRequestId) {
              return;
            }
            streamCompleted = true;
            if (questions.isEmpty()) {
              uiState.setValue(QuizUiState.error(DEFAULT_QUIZ_ERROR));
              logDebug("quiz load failed: empty_questions");
              return;
            }
            publishCurrentQuestionState();
            if (!isBlank(warningMessage)) {
              logDebug("quiz stream completed with warning: " + warningMessage);
            } else {
              logDebug("quiz stream completed: count=" + questions.size());
            }
          }

          @Override
          public void onFailure(@NonNull String error) {
            if (requestId != activeLoadRequestId) {
              return;
            }
            String safeError = trimToNull(error);
            uiState.setValue(QuizUiState.error(firstNonBlank(safeError, DEFAULT_QUIZ_ERROR)));
            logDebug("quiz load failed: " + firstNonBlank(safeError, "unknown"));
          }
        });
  }

  private boolean checkCurrentAnswer() {
    QuizQuestionState current = currentQuestionState;
    if (current == null) {
      return false;
    }

    String submitted = current.getSubmittedAnswer();
    if (isBlank(submitted)) {
      return false;
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
    return true;
  }

  @NonNull
  private PrimaryActionResult moveToNextQuestionOrComplete() {
    if (questions.isEmpty()) {
      uiState.setValue(QuizUiState.error(DEFAULT_QUIZ_ERROR));
      return PrimaryActionResult.NONE;
    }

    if (currentQuestionIndex < questions.size() - 1) {
      currentQuestionIndex++;
      currentQuestionState = QuizQuestionState.from(questions.get(currentQuestionIndex));
      publishCurrentQuestionState();
      logDebug("quiz next question: index=" + (currentQuestionIndex + 1) + "/" + questions.size());
      return PrimaryActionResult.MOVED_TO_NEXT;
    }

    if (streamCompleted) {
      uiState.setValue(QuizUiState.completed(questions.size(), correctAnswerCount));
      logDebug("quiz completed: correct=" + correctAnswerCount + "/" + questions.size());
      return PrimaryActionResult.COMPLETED;
    }

    // Keep current question until the next one arrives from stream.
    publishCurrentQuestionState();
    logDebug("quiz waiting for next streamed question");
    return PrimaryActionResult.WAITING_NEXT_QUESTION;
  }

  private void publishCurrentQuestionState() {
    QuizQuestionState state = currentQuestionState;
    if (state == null || questions.isEmpty()) {
      uiState.setValue(QuizUiState.error(DEFAULT_QUIZ_ERROR));
      return;
    }

    int totalQuestions = resolveVisibleTotalQuestions();
    boolean isLastQuestion = streamCompleted && currentQuestionIndex >= questions.size() - 1;
    uiState.setValue(
        QuizUiState.ready(
            state, currentQuestionIndex, totalQuestions, correctAnswerCount, isLastQuestion));
  }

  private int resolveVisibleTotalQuestions() {
    if (streamCompleted) {
      return Math.max(1, questions.size());
    }
    return Math.max(1, requestedQuestionCount);
  }

  @Nullable
  private QuizData.QuizQuestion sanitizeQuestion(@Nullable QuizData.QuizQuestion item) {
    if (item == null || questions.size() >= requestedQuestionCount) {
      return null;
    }

    String question = trimToNull(item.getQuestion());
    String answer = trimToNull(item.getAnswer());
    if (question == null || answer == null) {
      return null;
    }

    String dedupeKey = normalize(question);
    if (!seenQuestionKeys.add(dedupeKey)) {
      return null;
    }

    List<String> choices = sanitizeChoices(item.getChoices(), answer);
    if (choices == null || choices.size() <= 1) {
      return null;
    }
    String explanation = trimToNull(item.getExplanation());
    return new QuizData.QuizQuestion(question, answer, choices, explanation);
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

    if (result.size() <= 1) {
      return null;
    }

    if (!seen.contains(normalize(answer))) {
      result.add(answer);
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
