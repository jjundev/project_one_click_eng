package com.jjundev.oneclickeng.game.minefield;

import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.jjundev.oneclickeng.game.minefield.manager.IMinefieldGenerationManager;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldDifficulty;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldEvaluation;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldQuestion;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldRoundResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MinefieldGameViewModel extends ViewModel {
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
    void saveRoundResult(@NonNull MinefieldRoundResult result);
  }

  private static final int TOTAL_QUESTIONS = 4;
  private static final int BONUS_ALL_WORDS = 50;
  private static final int BONUS_QUICK = 30;
  private static final int BONUS_ADVANCED_TRANSFORM = 20;
  private static final int PENALTY_MINE_MISS = 50;
  private static final long QUICK_BONUS_LIMIT_MS = 60_000L;

  @NonNull private final IMinefieldGenerationManager generationManager;
  @NonNull private final StatsWriter statsWriter;
  @NonNull private final MutableLiveData<GameUiState> uiState;
  @NonNull private final Set<String> usedSignatures = new HashSet<>();

  @Nullable private MinefieldDifficulty selectedDifficulty;
  @Nullable private MinefieldQuestion currentQuestion;
  @Nullable private MinefieldEvaluation currentEvaluation;
  @Nullable private MinefieldRoundResult finalRoundResult;
  @Nullable private String pendingAnswerForRetry;

  private boolean initialized = false;
  private long activeRequestId = 0L;
  private long currentQuestionStartElapsedMs = 0L;

  private int answeredCount = 0;
  private int totalScore = 0;
  private int grammarSum = 0;
  private int naturalnessSum = 0;
  private int wordUsageSum = 0;

  private int lastQuestionScore = 0;
  private int lastBonusAllWords = 0;
  private int lastBonusQuick = 0;
  private int lastBonusTransform = 0;
  private int lastPenaltyMine = 0;
  private long lastElapsedMs = 0L;

  @NonNull private RetryType retryType = RetryType.QUESTION;

  public MinefieldGameViewModel(
      @NonNull IMinefieldGenerationManager generationManager,
      @NonNull MinefieldStatsStore statsStore) {
    this(generationManager, statsStore::saveRoundResult);
  }

  MinefieldGameViewModel(
      @NonNull IMinefieldGenerationManager generationManager, @NonNull StatsWriter statsWriter) {
    this.generationManager = generationManager;
    this.statsWriter = statsWriter;
    this.uiState =
        new MutableLiveData<>(
            GameUiState.loading(
                "난이도 선택 후 문제를 불러오고 있습니다...",
                MinefieldDifficulty.EASY,
                1,
                TOTAL_QUESTIONS,
                0));
  }

  @NonNull
  public LiveData<GameUiState> getUiState() {
    return uiState;
  }

  public void initialize(@NonNull MinefieldDifficulty difficulty) {
    if (initialized) {
      return;
    }
    initialized = true;
    selectedDifficulty = difficulty;
    publishLoading("문제를 불러오는 중입니다...");
    generationManager.initializeCache(
        new IMinefieldGenerationManager.InitCallback() {
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

  @NonNull
  public ActionResult onSubmitSentence(@Nullable String userSentence, int locallyUsedWordCount) {
    GameUiState state = uiState.getValue();
    MinefieldQuestion question = currentQuestion;
    if (state == null || question == null || state.getStage() != Stage.ANSWERING) {
      return ActionResult.NONE;
    }

    String trimmed = userSentence == null ? "" : userSentence.trim();
    if (trimmed.isEmpty()) {
      return ActionResult.INVALID;
    }
    if (locallyUsedWordCount <= 0) {
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
    MinefieldDifficulty difficulty =
        selectedDifficulty == null ? MinefieldDifficulty.EASY : selectedDifficulty;
    publishLoading("문제를 불러오는 중입니다...");
    pendingAnswerForRetry = null;
    currentEvaluation = null;
    lastQuestionScore = 0;
    lastBonusAllWords = 0;
    lastBonusQuick = 0;
    lastBonusTransform = 0;
    lastPenaltyMine = 0;
    lastElapsedMs = 0L;

    long requestId = ++activeRequestId;
    generationManager.generateQuestionAsync(
        difficulty,
        new HashSet<>(usedSignatures),
        new IMinefieldGenerationManager.QuestionCallback() {
          @Override
          public void onSuccess(@NonNull MinefieldQuestion question) {
            if (requestId != activeRequestId) {
              return;
            }
            MinefieldQuestion normalized = normalizeQuestion(question, difficulty);
            if (!normalized.isValidForV1()) {
              publishError("문항 형식이 올바르지 않습니다.", RetryType.QUESTION);
              return;
            }
            currentQuestion = normalized;
            usedSignatures.add(normalized.signature());
            currentQuestionStartElapsedMs = SystemClock.elapsedRealtime();
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

  @NonNull
  private MinefieldQuestion normalizeQuestion(
      @NonNull MinefieldQuestion question, @NonNull MinefieldDifficulty difficulty) {
    int requiredCount = difficulty.requiredMineWordCount();
    List<Integer> source = question.getRequiredWordIndices();
    List<Integer> normalizedRequired = new ArrayList<>();
    for (Integer index : source) {
      if (index == null || index < 0 || index >= question.getWords().size()) {
        continue;
      }
      if (!normalizedRequired.contains(index)) {
        normalizedRequired.add(index);
      }
      if (normalizedRequired.size() >= requiredCount) {
        break;
      }
    }

    return new MinefieldQuestion(
        question.getSituation(),
        question.getQuestion(),
        question.getWords(),
        normalizedRequired,
        difficulty);
  }

  private void evaluateCurrentAnswer(@NonNull String sentence) {
    MinefieldQuestion question = currentQuestion;
    if (question == null) {
      publishError("문항 정보가 비어 있습니다.", RetryType.QUESTION);
      return;
    }

    publishEvaluating();
    long requestId = ++activeRequestId;
    generationManager.evaluateAnswerAsync(
        question,
        sentence,
        new IMinefieldGenerationManager.EvaluationCallback() {
          @Override
          public void onSuccess(@NonNull MinefieldEvaluation evaluation) {
            if (requestId != activeRequestId) {
              return;
            }
            if (!evaluation.isValidForV1()) {
              publishError("채점 형식이 올바르지 않습니다.", RetryType.EVALUATION);
              return;
            }
            onEvaluationSuccess(evaluation);
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

  private void onEvaluationSuccess(@NonNull MinefieldEvaluation evaluation) {
    currentEvaluation = evaluation;
    answeredCount = Math.min(TOTAL_QUESTIONS, answeredCount + 1);

    grammarSum += evaluation.getGrammarScore();
    naturalnessSum += evaluation.getNaturalnessScore();
    wordUsageSum += evaluation.getWordUsageScore();

    lastBonusAllWords =
        evaluation.getUsedWordCount() >= evaluation.getTotalWordCount() ? BONUS_ALL_WORDS : 0;
    lastElapsedMs =
        Math.max(0L, SystemClock.elapsedRealtime() - Math.max(0L, currentQuestionStartElapsedMs));
    lastBonusQuick = lastElapsedMs <= QUICK_BONUS_LIMIT_MS ? BONUS_QUICK : 0;
    lastBonusTransform = evaluation.isAdvancedTransformUsed() ? BONUS_ADVANCED_TRANSFORM : 0;
    lastPenaltyMine = evaluation.getMissingRequiredWords().isEmpty() ? 0 : PENALTY_MINE_MISS;

    int rawScore =
        evaluation.getGrammarScore()
            + evaluation.getNaturalnessScore()
            + evaluation.getWordUsageScore()
            + lastBonusAllWords
            + lastBonusQuick
            + lastBonusTransform
            - lastPenaltyMine;
    lastQuestionScore = clamp(rawScore, 0, 400);
    totalScore += lastQuestionScore;

    publishFeedback();
  }

  private void completeRound() {
    int denominator = Math.max(1, answeredCount);
    MinefieldRoundResult result =
        new MinefieldRoundResult(
            System.currentTimeMillis(),
            TOTAL_QUESTIONS,
            totalScore,
            Math.round(grammarSum / (float) denominator),
            Math.round(naturalnessSum / (float) denominator),
            Math.round(wordUsageSum / (float) denominator));
    finalRoundResult = result;
    statsWriter.saveRoundResult(result);
    uiState.setValue(
        GameUiState.completed(
            result,
            selectedDifficulty == null ? MinefieldDifficulty.EASY : selectedDifficulty,
            TOTAL_QUESTIONS,
            totalScore));
  }

  private void publishLoading(@NonNull String message) {
    retryType = RetryType.QUESTION;
    uiState.setValue(
        GameUiState.loading(
            message,
            selectedDifficulty == null ? MinefieldDifficulty.EASY : selectedDifficulty,
            Math.min(TOTAL_QUESTIONS, answeredCount + 1),
            TOTAL_QUESTIONS,
            totalScore));
  }

  private void publishError(@NonNull String message, @NonNull RetryType retryType) {
    this.retryType = retryType;
    uiState.setValue(
        GameUiState.error(
            message,
            selectedDifficulty == null ? MinefieldDifficulty.EASY : selectedDifficulty,
            Math.min(TOTAL_QUESTIONS, Math.max(1, answeredCount + 1)),
            TOTAL_QUESTIONS,
            totalScore));
  }

  private void publishAnswering() {
    MinefieldQuestion question = currentQuestion;
    if (question == null) {
      publishError("문항 정보가 비어 있습니다.", RetryType.QUESTION);
      return;
    }
    uiState.setValue(
        GameUiState.answering(
            selectedDifficulty == null ? MinefieldDifficulty.EASY : selectedDifficulty,
            question,
            Math.min(TOTAL_QUESTIONS, answeredCount + 1),
            TOTAL_QUESTIONS,
            totalScore));
  }

  private void publishEvaluating() {
    MinefieldQuestion question = currentQuestion;
    if (question == null) {
      publishError("문항 정보가 비어 있습니다.", RetryType.QUESTION);
      return;
    }
    retryType = RetryType.EVALUATION;
    uiState.setValue(
        GameUiState.evaluating(
            selectedDifficulty == null ? MinefieldDifficulty.EASY : selectedDifficulty,
            question,
            Math.min(TOTAL_QUESTIONS, answeredCount + 1),
            TOTAL_QUESTIONS,
            totalScore));
  }

  private void publishFeedback() {
    MinefieldQuestion question = currentQuestion;
    MinefieldEvaluation evaluation = currentEvaluation;
    if (question == null || evaluation == null) {
      publishError("피드백 정보가 비어 있습니다.", RetryType.QUESTION);
      return;
    }
    uiState.setValue(
        GameUiState.feedback(
            selectedDifficulty == null ? MinefieldDifficulty.EASY : selectedDifficulty,
            question,
            evaluation,
            Math.min(TOTAL_QUESTIONS, Math.max(1, answeredCount)),
            TOTAL_QUESTIONS,
            totalScore,
            lastQuestionScore,
            lastBonusAllWords,
            lastBonusQuick,
            lastBonusTransform,
            lastPenaltyMine,
            lastElapsedMs));
  }

  private static int clamp(int value, int min, int max) {
    if (value < min) {
      return min;
    }
    return Math.min(value, max);
  }

  public static final class GameUiState {
    @NonNull private final Stage stage;
    @NonNull private final MinefieldDifficulty difficulty;
    @Nullable private final String loadingMessage;
    @Nullable private final String errorMessage;
    @Nullable private final MinefieldQuestion question;
    @Nullable private final MinefieldEvaluation evaluation;
    @Nullable private final MinefieldRoundResult roundResult;
    private final int currentQuestionNumber;
    private final int totalQuestions;
    private final int totalScore;
    private final int lastQuestionScore;
    private final int bonusAllWords;
    private final int bonusQuick;
    private final int bonusTransform;
    private final int penaltyMine;
    private final long elapsedMs;

    private GameUiState(
        @NonNull Stage stage,
        @NonNull MinefieldDifficulty difficulty,
        @Nullable String loadingMessage,
        @Nullable String errorMessage,
        @Nullable MinefieldQuestion question,
        @Nullable MinefieldEvaluation evaluation,
        @Nullable MinefieldRoundResult roundResult,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore,
        int lastQuestionScore,
        int bonusAllWords,
        int bonusQuick,
        int bonusTransform,
        int penaltyMine,
        long elapsedMs) {
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
      this.bonusAllWords = Math.max(0, bonusAllWords);
      this.bonusQuick = Math.max(0, bonusQuick);
      this.bonusTransform = Math.max(0, bonusTransform);
      this.penaltyMine = Math.max(0, penaltyMine);
      this.elapsedMs = Math.max(0L, elapsedMs);
    }

    @NonNull
    static GameUiState loading(
        @NonNull String loadingMessage,
        @NonNull MinefieldDifficulty difficulty,
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
          0L);
    }

    @NonNull
    static GameUiState error(
        @NonNull String errorMessage,
        @NonNull MinefieldDifficulty difficulty,
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
          0L);
    }

    @NonNull
    static GameUiState answering(
        @NonNull MinefieldDifficulty difficulty,
        @NonNull MinefieldQuestion question,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore) {
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
          0L);
    }

    @NonNull
    static GameUiState evaluating(
        @NonNull MinefieldDifficulty difficulty,
        @NonNull MinefieldQuestion question,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore) {
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
          0L);
    }

    @NonNull
    static GameUiState feedback(
        @NonNull MinefieldDifficulty difficulty,
        @NonNull MinefieldQuestion question,
        @NonNull MinefieldEvaluation evaluation,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore,
        int lastQuestionScore,
        int bonusAllWords,
        int bonusQuick,
        int bonusTransform,
        int penaltyMine,
        long elapsedMs) {
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
          bonusAllWords,
          bonusQuick,
          bonusTransform,
          penaltyMine,
          elapsedMs);
    }

    @NonNull
    static GameUiState completed(
        @NonNull MinefieldRoundResult roundResult,
        @NonNull MinefieldDifficulty difficulty,
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
          0L);
    }

    @NonNull
    public Stage getStage() {
      return stage;
    }

    @NonNull
    public MinefieldDifficulty getDifficulty() {
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
    public MinefieldQuestion getQuestion() {
      return question;
    }

    @Nullable
    public MinefieldEvaluation getEvaluation() {
      return evaluation;
    }

    @Nullable
    public MinefieldRoundResult getRoundResult() {
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

    public int getBonusAllWords() {
      return bonusAllWords;
    }

    public int getBonusQuick() {
      return bonusQuick;
    }

    public int getBonusTransform() {
      return bonusTransform;
    }

    public int getPenaltyMine() {
      return penaltyMine;
    }

    public long getElapsedMs() {
      return elapsedMs;
    }
  }
}
