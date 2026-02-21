package com.jjundev.oneclickeng.game.refiner;

import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.jjundev.oneclickeng.game.refiner.manager.IRefinerGenerationManager;
import com.jjundev.oneclickeng.game.refiner.model.RefinerDifficulty;
import com.jjundev.oneclickeng.game.refiner.model.RefinerEvaluation;
import com.jjundev.oneclickeng.game.refiner.model.RefinerQuestion;
import com.jjundev.oneclickeng.game.refiner.model.RefinerRoundResult;
import java.util.HashSet;
import java.util.Set;

public final class RefinerGameViewModel extends ViewModel {
  public enum Stage {
    LOADING,
    ERROR,
    ANSWERING,
    EVALUATING,
    FEEDBACK,
    ROUND_COMPLETED
  }

  private enum RetryType {
    QUESTION,
    EVALUATION
  }

  public enum ActionResult {
    NONE,
    UPDATED,
    ADVANCED,
    COMPLETED,
    INVALID
  }

  interface StatsWriter {
    void saveRoundResult(@NonNull RefinerRoundResult result);
  }

  private static final int TOTAL_QUESTIONS = 5;
  private static final int BONUS_NO_HINT = 50;
  private static final int PENALTY_HINT = -30;
  private static final int BONUS_QUICK = 20;
  private static final int BONUS_CREATIVE_REQUIRED = 30;
  private static final long QUICK_BONUS_LIMIT_MS = 60_000L;

  @NonNull private final IRefinerGenerationManager generationManager;
  @NonNull private final StatsWriter statsWriter;
  @NonNull private final MutableLiveData<GameUiState> uiState;
  @NonNull private final Set<String> usedSignatures = new HashSet<>();

  @Nullable private RefinerDifficulty selectedDifficulty;
  @Nullable private RefinerQuestion currentQuestion;
  @Nullable private RefinerEvaluation currentEvaluation;
  @Nullable private RefinerRoundResult finalRoundResult;
  @Nullable private String pendingAnswerForRetry;

  private boolean initialized = false;
  private long activeRequestId = 0L;
  private long questionStartElapsedMs = 0L;

  private int answeredCount = 0;
  private int totalScore = 0;
  private int lexicalSum = 0;
  private int syntaxSum = 0;
  private int naturalnessSum = 0;
  private int complianceSum = 0;

  private boolean hintUsedCurrent = false;
  @NonNull private String revealedHintText = "";

  private int lastQuestionScore = 0;
  private int lastBaseScore = 0;
  private int lastHintModifier = 0;
  private int lastQuickBonus = 0;
  private int lastCreativeBonus = 0;
  private long lastElapsedMs = 0L;
  @NonNull private String lastSubmittedSentence = "";

  @NonNull private RetryType retryType = RetryType.QUESTION;

  public RefinerGameViewModel(
      @NonNull IRefinerGenerationManager generationManager, @NonNull RefinerStatsStore statsStore) {
    this(generationManager, statsStore::saveRoundResult);
  }

  RefinerGameViewModel(
      @NonNull IRefinerGenerationManager generationManager, @NonNull StatsWriter statsWriter) {
    this.generationManager = generationManager;
    this.statsWriter = statsWriter;
    this.uiState =
        new MutableLiveData<>(
            GameUiState.loading(
                "문제를 불러오는 중입니다...",
                RefinerDifficulty.EASY,
                1,
                TOTAL_QUESTIONS,
                0));
  }

  @NonNull
  public LiveData<GameUiState> getUiState() {
    return uiState;
  }

  public void initialize(@NonNull RefinerDifficulty difficulty) {
    if (initialized) {
      return;
    }
    initialized = true;
    selectedDifficulty = difficulty;
    publishLoading("문제를 불러오는 중입니다...");
    generationManager.initializeCache(
        new IRefinerGenerationManager.InitCallback() {
          @Override
          public void onReady() {
            loadNextQuestion();
          }

          @Override
          public void onError(@NonNull String error) {
            publishError(error, RetryType.QUESTION);
          }
        });
  }

  public void retry() {
    if (retryType == RetryType.EVALUATION
        && currentQuestion != null
        && pendingAnswerForRetry != null
        && !pendingAnswerForRetry.trim().isEmpty()) {
      evaluateCurrentAnswer(pendingAnswerForRetry.trim());
      return;
    }
    loadNextQuestion();
  }

  public void onHintRequested() {
    GameUiState state = uiState.getValue();
    if (state == null || state.getStage() != Stage.ANSWERING || currentQuestion == null) {
      return;
    }
    if (hintUsedCurrent) {
      return;
    }
    hintUsedCurrent = true;
    revealedHintText = currentQuestion.getHint();
    publishAnswering();
  }

  @NonNull
  public ActionResult onSubmitSentence(
      @Nullable String userSentence, boolean constraintsSatisfied) {
    GameUiState state = uiState.getValue();
    if (state == null || state.getStage() != Stage.ANSWERING || currentQuestion == null) {
      return ActionResult.NONE;
    }
    String trimmed = userSentence == null ? "" : userSentence.trim();
    if (trimmed.isEmpty() || !constraintsSatisfied) {
      return ActionResult.INVALID;
    }
    pendingAnswerForRetry = trimmed;
    evaluateCurrentAnswer(trimmed);
    return ActionResult.ADVANCED;
  }

  @NonNull
  public ActionResult onNextFromFeedback() {
    GameUiState state = uiState.getValue();
    if (state == null || state.getStage() != Stage.FEEDBACK) {
      return ActionResult.NONE;
    }
    if (answeredCount >= TOTAL_QUESTIONS) {
      completeRound();
      return ActionResult.COMPLETED;
    }
    loadNextQuestion();
    return ActionResult.ADVANCED;
  }

  private void loadNextQuestion() {
    RefinerDifficulty difficulty =
        selectedDifficulty == null ? RefinerDifficulty.EASY : selectedDifficulty;
    publishLoading("문제를 불러오는 중입니다...");
    pendingAnswerForRetry = null;
    currentEvaluation = null;
    lastQuestionScore = 0;
    lastBaseScore = 0;
    lastHintModifier = 0;
    lastQuickBonus = 0;
    lastCreativeBonus = 0;
    lastElapsedMs = 0L;
    lastSubmittedSentence = "";
    hintUsedCurrent = false;
    revealedHintText = "";

    long requestId = ++activeRequestId;
    generationManager.generateQuestionAsync(
        difficulty,
        new HashSet<>(usedSignatures),
        new IRefinerGenerationManager.QuestionCallback() {
          @Override
          public void onSuccess(@NonNull RefinerQuestion question) {
            if (requestId != activeRequestId) {
              return;
            }
            if (!question.isValidForV1()) {
              publishError("문항 형식이 올바르지 않습니다.", RetryType.QUESTION);
              return;
            }
            currentQuestion = question;
            usedSignatures.add(question.signature());
            questionStartElapsedMs = SystemClock.elapsedRealtime();
            publishAnswering();
          }

          @Override
          public void onFailure(@NonNull String error) {
            if (requestId != activeRequestId) {
              return;
            }
            publishError(error, RetryType.QUESTION);
          }
        });
  }

  private void evaluateCurrentAnswer(@NonNull String userSentence) {
    RefinerQuestion question = currentQuestion;
    if (question == null) {
      publishError("문항 정보가 비어 있습니다.", RetryType.QUESTION);
      return;
    }
    publishEvaluating();
    long requestId = ++activeRequestId;
    generationManager.evaluateAnswerAsync(
        question,
        userSentence,
        new IRefinerGenerationManager.EvaluationCallback() {
          @Override
          public void onSuccess(@NonNull RefinerEvaluation evaluation) {
            if (requestId != activeRequestId) {
              return;
            }
            if (!evaluation.isValidForV1()) {
              publishError("채점 형식이 올바르지 않습니다.", RetryType.EVALUATION);
              return;
            }
            onEvaluationSuccess(userSentence, evaluation);
          }

          @Override
          public void onFailure(@NonNull String error) {
            if (requestId != activeRequestId) {
              return;
            }
            publishError(error, RetryType.EVALUATION);
          }
        });
  }

  private void onEvaluationSuccess(
      @NonNull String userSentence, @NonNull RefinerEvaluation evaluation) {
    currentEvaluation = evaluation;
    answeredCount = Math.min(TOTAL_QUESTIONS, answeredCount + 1);
    lastSubmittedSentence = userSentence;

    lastBaseScore = evaluation.getLevel().baseScore();
    lastHintModifier = hintUsedCurrent ? PENALTY_HINT : BONUS_NO_HINT;
    lastElapsedMs = Math.max(0L, SystemClock.elapsedRealtime() - Math.max(0L, questionStartElapsedMs));
    lastQuickBonus = lastElapsedMs <= QUICK_BONUS_LIMIT_MS ? BONUS_QUICK : 0;
    boolean hasRequiredWord =
        currentQuestion != null && currentQuestion.getConstraints().hasRequiredWord();
    lastCreativeBonus =
        hasRequiredWord && evaluation.isCreativeRequiredWordUse() ? BONUS_CREATIVE_REQUIRED : 0;

    int total = lastBaseScore + lastHintModifier + lastQuickBonus + lastCreativeBonus;
    lastQuestionScore = Math.max(0, total);
    totalScore += lastQuestionScore;

    lexicalSum += evaluation.getLexicalScore();
    syntaxSum += evaluation.getSyntaxScore();
    naturalnessSum += evaluation.getNaturalnessScore();
    complianceSum += evaluation.getComplianceScore();

    publishFeedback();
  }

  private void completeRound() {
    int denominator = Math.max(1, answeredCount);
    RefinerRoundResult result =
        new RefinerRoundResult(
            System.currentTimeMillis(),
            TOTAL_QUESTIONS,
            totalScore,
            Math.round(lexicalSum / (float) denominator),
            Math.round(syntaxSum / (float) denominator),
            Math.round(naturalnessSum / (float) denominator),
            Math.round(complianceSum / (float) denominator));
    finalRoundResult = result;
    statsWriter.saveRoundResult(result);
    uiState.setValue(
        GameUiState.completed(
            result,
            selectedDifficulty == null ? RefinerDifficulty.EASY : selectedDifficulty,
            TOTAL_QUESTIONS,
            totalScore));
  }

  private void publishLoading(@NonNull String message) {
    retryType = RetryType.QUESTION;
    uiState.setValue(
        GameUiState.loading(
            message,
            selectedDifficulty == null ? RefinerDifficulty.EASY : selectedDifficulty,
            Math.min(TOTAL_QUESTIONS, Math.max(1, answeredCount + 1)),
            TOTAL_QUESTIONS,
            totalScore));
  }

  private void publishError(@NonNull String message, @NonNull RetryType retryType) {
    this.retryType = retryType;
    uiState.setValue(
        GameUiState.error(
            message,
            selectedDifficulty == null ? RefinerDifficulty.EASY : selectedDifficulty,
            Math.min(TOTAL_QUESTIONS, Math.max(1, answeredCount + 1)),
            TOTAL_QUESTIONS,
            totalScore));
  }

  private void publishAnswering() {
    RefinerQuestion question = currentQuestion;
    if (question == null) {
      publishError("문항 정보가 비어 있습니다.", RetryType.QUESTION);
      return;
    }
    uiState.setValue(
        GameUiState.answering(
            selectedDifficulty == null ? RefinerDifficulty.EASY : selectedDifficulty,
            question,
            Math.min(TOTAL_QUESTIONS, answeredCount + 1),
            TOTAL_QUESTIONS,
            totalScore,
            hintUsedCurrent,
            revealedHintText));
  }

  private void publishEvaluating() {
    RefinerQuestion question = currentQuestion;
    if (question == null) {
      publishError("문항 정보가 비어 있습니다.", RetryType.QUESTION);
      return;
    }
    retryType = RetryType.EVALUATION;
    uiState.setValue(
        GameUiState.evaluating(
            selectedDifficulty == null ? RefinerDifficulty.EASY : selectedDifficulty,
            question,
            Math.min(TOTAL_QUESTIONS, answeredCount + 1),
            TOTAL_QUESTIONS,
            totalScore,
            hintUsedCurrent,
            revealedHintText));
  }

  private void publishFeedback() {
    RefinerQuestion question = currentQuestion;
    RefinerEvaluation evaluation = currentEvaluation;
    if (question == null || evaluation == null) {
      publishError("피드백 정보가 비어 있습니다.", RetryType.QUESTION);
      return;
    }
    uiState.setValue(
        GameUiState.feedback(
            selectedDifficulty == null ? RefinerDifficulty.EASY : selectedDifficulty,
            question,
            evaluation,
            Math.min(TOTAL_QUESTIONS, Math.max(1, answeredCount)),
            TOTAL_QUESTIONS,
            totalScore,
            lastQuestionScore,
            lastBaseScore,
            lastHintModifier,
            lastQuickBonus,
            lastCreativeBonus,
            lastElapsedMs,
            lastSubmittedSentence));
  }

  public static final class GameUiState {
    @NonNull private final Stage stage;
    @NonNull private final RefinerDifficulty difficulty;
    @Nullable private final String loadingMessage;
    @Nullable private final String errorMessage;
    @Nullable private final RefinerQuestion question;
    @Nullable private final RefinerEvaluation evaluation;
    @Nullable private final RefinerRoundResult roundResult;
    private final int currentQuestionNumber;
    private final int totalQuestions;
    private final int totalScore;
    private final int lastQuestionScore;
    private final int lastBaseScore;
    private final int lastHintModifier;
    private final int lastQuickBonus;
    private final int lastCreativeBonus;
    private final long elapsedMs;
    @NonNull private final String submittedSentence;
    private final boolean hintUsed;
    @NonNull private final String hintText;

    private GameUiState(
        @NonNull Stage stage,
        @NonNull RefinerDifficulty difficulty,
        @Nullable String loadingMessage,
        @Nullable String errorMessage,
        @Nullable RefinerQuestion question,
        @Nullable RefinerEvaluation evaluation,
        @Nullable RefinerRoundResult roundResult,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore,
        int lastQuestionScore,
        int lastBaseScore,
        int lastHintModifier,
        int lastQuickBonus,
        int lastCreativeBonus,
        long elapsedMs,
        @Nullable String submittedSentence,
        boolean hintUsed,
        @Nullable String hintText) {
      this.stage = stage;
      this.difficulty = difficulty;
      this.loadingMessage = loadingMessage;
      this.errorMessage = errorMessage;
      this.question = question;
      this.evaluation = evaluation;
      this.roundResult = roundResult;
      this.currentQuestionNumber = Math.max(1, currentQuestionNumber);
      this.totalQuestions = Math.max(1, totalQuestions);
      this.totalScore = Math.max(0, totalScore);
      this.lastQuestionScore = Math.max(0, lastQuestionScore);
      this.lastBaseScore = Math.max(0, lastBaseScore);
      this.lastHintModifier = lastHintModifier;
      this.lastQuickBonus = Math.max(0, lastQuickBonus);
      this.lastCreativeBonus = Math.max(0, lastCreativeBonus);
      this.elapsedMs = Math.max(0L, elapsedMs);
      this.submittedSentence = submittedSentence == null ? "" : submittedSentence;
      this.hintUsed = hintUsed;
      this.hintText = hintText == null ? "" : hintText;
    }

    @NonNull
    static GameUiState loading(
        @NonNull String loadingMessage,
        @NonNull RefinerDifficulty difficulty,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore) {
      return new GameUiState(
          Stage.LOADING,
          difficulty,
          loadingMessage,
          null,
          null,
          null,
          null,
          currentQuestionNumber,
          totalQuestions,
          totalScore,
          0,
          0,
          0,
          0,
          0,
          0L,
          "",
          false,
          "");
    }

    @NonNull
    static GameUiState error(
        @NonNull String errorMessage,
        @NonNull RefinerDifficulty difficulty,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore) {
      return new GameUiState(
          Stage.ERROR,
          difficulty,
          null,
          errorMessage,
          null,
          null,
          null,
          currentQuestionNumber,
          totalQuestions,
          totalScore,
          0,
          0,
          0,
          0,
          0,
          0L,
          "",
          false,
          "");
    }

    @NonNull
    static GameUiState answering(
        @NonNull RefinerDifficulty difficulty,
        @NonNull RefinerQuestion question,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore,
        boolean hintUsed,
        @Nullable String hintText) {
      return new GameUiState(
          Stage.ANSWERING,
          difficulty,
          null,
          null,
          question,
          null,
          null,
          currentQuestionNumber,
          totalQuestions,
          totalScore,
          0,
          0,
          0,
          0,
          0,
          0L,
          "",
          hintUsed,
          hintText);
    }

    @NonNull
    static GameUiState evaluating(
        @NonNull RefinerDifficulty difficulty,
        @NonNull RefinerQuestion question,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore,
        boolean hintUsed,
        @Nullable String hintText) {
      return new GameUiState(
          Stage.EVALUATING,
          difficulty,
          null,
          null,
          question,
          null,
          null,
          currentQuestionNumber,
          totalQuestions,
          totalScore,
          0,
          0,
          0,
          0,
          0,
          0L,
          "",
          hintUsed,
          hintText);
    }

    @NonNull
    static GameUiState feedback(
        @NonNull RefinerDifficulty difficulty,
        @NonNull RefinerQuestion question,
        @NonNull RefinerEvaluation evaluation,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore,
        int lastQuestionScore,
        int lastBaseScore,
        int lastHintModifier,
        int lastQuickBonus,
        int lastCreativeBonus,
        long elapsedMs,
        @Nullable String submittedSentence) {
      return new GameUiState(
          Stage.FEEDBACK,
          difficulty,
          null,
          null,
          question,
          evaluation,
          null,
          currentQuestionNumber,
          totalQuestions,
          totalScore,
          lastQuestionScore,
          lastBaseScore,
          lastHintModifier,
          lastQuickBonus,
          lastCreativeBonus,
          elapsedMs,
          submittedSentence,
          false,
          "");
    }

    @NonNull
    static GameUiState completed(
        @NonNull RefinerRoundResult roundResult,
        @NonNull RefinerDifficulty difficulty,
        int totalQuestions,
        int totalScore) {
      return new GameUiState(
          Stage.ROUND_COMPLETED,
          difficulty,
          null,
          null,
          null,
          null,
          roundResult,
          totalQuestions,
          totalQuestions,
          totalScore,
          0,
          0,
          0,
          0,
          0,
          0L,
          "",
          false,
          "");
    }

    @NonNull
    public Stage getStage() {
      return stage;
    }

    @NonNull
    public RefinerDifficulty getDifficulty() {
      return difficulty;
    }

    @Nullable
    public String getLoadingMessage() {
      return loadingMessage;
    }

    @Nullable
    public String getErrorMessage() {
      return errorMessage;
    }

    @Nullable
    public RefinerQuestion getQuestion() {
      return question;
    }

    @Nullable
    public RefinerEvaluation getEvaluation() {
      return evaluation;
    }

    @Nullable
    public RefinerRoundResult getRoundResult() {
      return roundResult;
    }

    public int getCurrentQuestionNumber() {
      return currentQuestionNumber;
    }

    public int getTotalQuestions() {
      return totalQuestions;
    }

    public int getTotalScore() {
      return totalScore;
    }

    public int getLastQuestionScore() {
      return lastQuestionScore;
    }

    public int getLastBaseScore() {
      return lastBaseScore;
    }

    public int getLastHintModifier() {
      return lastHintModifier;
    }

    public int getLastQuickBonus() {
      return lastQuickBonus;
    }

    public int getLastCreativeBonus() {
      return lastCreativeBonus;
    }

    public long getElapsedMs() {
      return elapsedMs;
    }

    @NonNull
    public String getSubmittedSentence() {
      return submittedSentence;
    }

    public boolean isHintUsed() {
      return hintUsed;
    }

    @NonNull
    public String getHintText() {
      return hintText;
    }
  }
}
