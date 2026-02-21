package com.jjundev.oneclickeng.game.nativeornot;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.jjundev.oneclickeng.game.nativeornot.manager.INativeOrNotGenerationManager;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotDifficulty;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotQuestion;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotRoundResult;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotTag;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class NativeOrNotGameViewModel extends ViewModel {

  public enum Stage {
    LOADING_QUESTION,
    PHASE1_SELECTING,
    PHASE2_REASON,
    PHASE3_EXPLANATION,
    LOADING_BONUS,
    ROUND_COMPLETED,
    ERROR
  }

  private enum LoadType {
    REGULAR,
    BONUS
  }

  public enum ActionResult {
    NONE,
    UPDATED,
    ADVANCED,
    COMPLETED,
    INVALID
  }

  private static final int TOTAL_REGULAR_QUESTIONS = 5;
  private static final int BASE_SCORE = 100;
  private static final int REASON_BONUS_SCORE = 50;
  private static final int NO_HINT_BONUS_SCORE = 30;
  private static final int HINT_PENALTY_SCORE = -20;
  private static final int BONUS_QUESTION_SCORE = 100;

  interface StatsWriter {
    void saveRoundResult(@NonNull NativeOrNotRoundResult result);
  }

  @NonNull private final INativeOrNotGenerationManager generationManager;
  @NonNull private final StatsWriter statsWriter;
  @NonNull private final MutableLiveData<GameUiState> uiState;

  @NonNull private final Set<String> usedSignatures = new HashSet<>();
  @NonNull private final Map<NativeOrNotTag, Integer> wrongTagCounts =
      new EnumMap<>(NativeOrNotTag.class);

  @Nullable private NativeOrNotQuestion currentQuestion;
  @Nullable private NativeOrNotQuestion regularQuestionForBonus;
  @Nullable private NativeOrNotRoundResult finalRoundResult;

  private long activeRequestId = 0L;
  private boolean initialized = false;
  private boolean bonusUsed = false;
  private boolean currentQuestionIsBonus = false;

  private int answeredRegularCount = 0;
  private int correctRegularCount = 0;
  private int streak = 0;
  private int highestStreak = 0;
  private int totalScore = 0;

  private int selectedOptionIndex = -1;
  private int selectedReasonIndex = -1;
  private boolean hintUsedCurrent = false;
  @Nullable private String revealedHintText;
  private boolean phase1Correct = false;
  private boolean reasonCorrect = false;

  @NonNull private LoadType retryLoadType = LoadType.REGULAR;

  public NativeOrNotGameViewModel(
      @NonNull INativeOrNotGenerationManager generationManager,
      @NonNull NativeOrNotStatsStore statsStore) {
    this(generationManager, statsStore::saveRoundResult);
  }

  NativeOrNotGameViewModel(
      @NonNull INativeOrNotGenerationManager generationManager, @NonNull StatsWriter statsWriter) {
    this.generationManager = generationManager;
    this.statsWriter = statsWriter;
    this.uiState = new MutableLiveData<>(GameUiState.loading(Stage.LOADING_QUESTION));
  }

  @NonNull
  public LiveData<GameUiState> getUiState() {
    return uiState;
  }

  public void initialize() {
    if (initialized) {
      return;
    }
    initialized = true;
    publishLoading(Stage.LOADING_QUESTION);
    generationManager.initializeCache(
        new INativeOrNotGenerationManager.InitCallback() {
          @Override
          public void onReady() {
            loadRegularQuestion();
          }

          @Override
          public void onError(@NonNull String error) {
            publishError(error, LoadType.REGULAR);
          }
        });
  }

  public void retryLoad() {
    if (retryLoadType == LoadType.BONUS && regularQuestionForBonus != null) {
      loadRelatedBonusQuestion(regularQuestionForBonus);
      return;
    }
    loadRegularQuestion();
  }

  public void onOptionSelected(int optionIndex) {
    if (currentQuestion == null || uiState.getValue() == null) {
      return;
    }
    Stage stage = uiState.getValue().getStage();
    if (stage != Stage.PHASE1_SELECTING) {
      return;
    }
    if (optionIndex < 0 || optionIndex >= currentQuestion.getOptions().size()) {
      return;
    }
    selectedOptionIndex = optionIndex;
    publishQuestionState(Stage.PHASE1_SELECTING);
  }

  @NonNull
  public ActionResult onConfirmOption() {
    NativeOrNotQuestion question = currentQuestion;
    if (question == null || selectedOptionIndex < 0) {
      return ActionResult.INVALID;
    }
    GameUiState state = uiState.getValue();
    if (state == null || state.getStage() != Stage.PHASE1_SELECTING) {
      return ActionResult.NONE;
    }

    phase1Correct = selectedOptionIndex == question.getCorrectIndex();

    if (currentQuestionIsBonus) {
      if (phase1Correct) {
        totalScore += BONUS_QUESTION_SCORE;
      }
      reasonCorrect = false;
      publishQuestionState(Stage.PHASE3_EXPLANATION);
      return ActionResult.UPDATED;
    }

    answeredRegularCount++;
    if (hintUsedCurrent) {
      // Penalty was already applied when hint button was tapped.
    } else {
      totalScore += NO_HINT_BONUS_SCORE;
    }

    if (phase1Correct) {
      streak++;
      highestStreak = Math.max(highestStreak, streak);
      correctRegularCount++;
      int scoredBase = Math.round(BASE_SCORE * resolveComboMultiplier(streak));
      totalScore += scoredBase;
    } else {
      streak = 0;
      incrementWrongTag(question.getTag());
    }

    publishQuestionState(Stage.PHASE2_REASON);
    return ActionResult.UPDATED;
  }

  public void onReasonSelected(int index) {
    NativeOrNotQuestion question = currentQuestion;
    GameUiState state = uiState.getValue();
    if (question == null || state == null || state.getStage() != Stage.PHASE2_REASON) {
      return;
    }
    if (index < 0 || index >= question.getReasonChoices().size()) {
      return;
    }
    selectedReasonIndex = index;
    publishQuestionState(Stage.PHASE2_REASON);
  }

  @NonNull
  public ActionResult onConfirmReason() {
    NativeOrNotQuestion question = currentQuestion;
    GameUiState state = uiState.getValue();
    if (question == null || state == null || state.getStage() != Stage.PHASE2_REASON) {
      return ActionResult.NONE;
    }
    if (selectedReasonIndex < 0) {
      return ActionResult.INVALID;
    }

    reasonCorrect = selectedReasonIndex == question.getReasonAnswerIndex();
    if (reasonCorrect) {
      totalScore += REASON_BONUS_SCORE;
    }
    publishQuestionState(Stage.PHASE3_EXPLANATION);
    return ActionResult.UPDATED;
  }

  public void onHintRequested() {
    GameUiState state = uiState.getValue();
    if (state == null || state.getStage() != Stage.PHASE1_SELECTING || currentQuestion == null) {
      return;
    }
    if (currentQuestionIsBonus || hintUsedCurrent) {
      return;
    }

    hintUsedCurrent = true;
    revealedHintText = currentQuestion.getHint();
    totalScore += HINT_PENALTY_SCORE;
    publishQuestionState(Stage.PHASE1_SELECTING);
  }

  public boolean canRequestRelatedQuestion() {
    GameUiState state = uiState.getValue();
    return state != null
        && state.getStage() == Stage.PHASE3_EXPLANATION
        && !currentQuestionIsBonus
        && !bonusUsed
        && currentQuestion != null;
  }

  @NonNull
  public ActionResult onRequestRelatedQuestion() {
    if (!canRequestRelatedQuestion()) {
      return ActionResult.NONE;
    }
    NativeOrNotQuestion baseQuestion = currentQuestion;
    if (baseQuestion == null) {
      return ActionResult.NONE;
    }
    bonusUsed = true;
    regularQuestionForBonus = baseQuestion;
    loadRelatedBonusQuestion(baseQuestion);
    return ActionResult.ADVANCED;
  }

  @NonNull
  public ActionResult onNextFromExplanation() {
    GameUiState state = uiState.getValue();
    if (state == null || state.getStage() != Stage.PHASE3_EXPLANATION) {
      return ActionResult.NONE;
    }

    if (currentQuestionIsBonus) {
      currentQuestionIsBonus = false;
      regularQuestionForBonus = null;
      if (answeredRegularCount >= TOTAL_REGULAR_QUESTIONS) {
        completeRound();
        return ActionResult.COMPLETED;
      }
      loadRegularQuestion();
      return ActionResult.ADVANCED;
    }

    if (answeredRegularCount >= TOTAL_REGULAR_QUESTIONS) {
      completeRound();
      return ActionResult.COMPLETED;
    }

    loadRegularQuestion();
    return ActionResult.ADVANCED;
  }

  private void loadRegularQuestion() {
    currentQuestionIsBonus = false;
    publishLoading(Stage.LOADING_QUESTION);
    resetPerQuestionState();

    long requestId = ++activeRequestId;
    NativeOrNotDifficulty difficulty = NativeOrNotDifficulty.forStreakV1(streak);
    generationManager.generateQuestionAsync(
        difficulty,
        new HashSet<>(usedSignatures),
        new INativeOrNotGenerationManager.QuestionCallback() {
          @Override
          public void onSuccess(@NonNull NativeOrNotQuestion question) {
            if (requestId != activeRequestId) {
              return;
            }
            onQuestionLoaded(question, false);
          }

          @Override
          public void onFailure(@NonNull String error) {
            if (requestId != activeRequestId) {
              return;
            }
            publishError(error, LoadType.REGULAR);
          }
        });
  }

  private void loadRelatedBonusQuestion(@NonNull NativeOrNotQuestion baseQuestion) {
    publishLoading(Stage.LOADING_BONUS);
    resetPerQuestionState();

    long requestId = ++activeRequestId;
    generationManager.generateRelatedQuestionAsync(
        baseQuestion,
        new HashSet<>(usedSignatures),
        new INativeOrNotGenerationManager.QuestionCallback() {
          @Override
          public void onSuccess(@NonNull NativeOrNotQuestion question) {
            if (requestId != activeRequestId) {
              return;
            }
            onQuestionLoaded(question, true);
          }

          @Override
          public void onFailure(@NonNull String error) {
            if (requestId != activeRequestId) {
              return;
            }
            publishError(error, LoadType.BONUS);
          }
        });
  }

  private void onQuestionLoaded(@NonNull NativeOrNotQuestion question, boolean asBonus) {
    currentQuestion = question;
    currentQuestionIsBonus = asBonus;
    usedSignatures.add(question.signature());
    publishQuestionState(Stage.PHASE1_SELECTING);
  }

  private void completeRound() {
    NativeOrNotRoundResult result =
        new NativeOrNotRoundResult(
            System.currentTimeMillis(),
            TOTAL_REGULAR_QUESTIONS,
            correctRegularCount,
            highestStreak,
            totalScore,
            resolveWeakTag());
    finalRoundResult = result;
    statsWriter.saveRoundResult(result);
    uiState.setValue(
        GameUiState.completed(
            result,
            TOTAL_REGULAR_QUESTIONS,
            correctRegularCount,
            totalScore,
            highestStreak,
            streak,
            bonusUsed));
  }

  private void resetPerQuestionState() {
    selectedOptionIndex = -1;
    selectedReasonIndex = -1;
    hintUsedCurrent = false;
    revealedHintText = null;
    phase1Correct = false;
    reasonCorrect = false;
  }

  private void publishQuestionState(@NonNull Stage stage) {
    NativeOrNotQuestion question = currentQuestion;
    if (question == null) {
      publishError("문항이 비어 있습니다.", LoadType.REGULAR);
      return;
    }
    int currentNumber = resolveDisplayQuestionNumber(stage);
    boolean canUseRelatedBonus =
        stage == Stage.PHASE3_EXPLANATION && !currentQuestionIsBonus && !bonusUsed;
    uiState.setValue(
        GameUiState.question(
            stage,
            question,
            selectedOptionIndex,
            selectedReasonIndex,
            currentNumber,
            TOTAL_REGULAR_QUESTIONS,
            totalScore,
            streak,
            highestStreak,
            hintUsedCurrent,
            revealedHintText,
            phase1Correct,
            reasonCorrect,
            currentQuestionIsBonus,
            bonusUsed,
            canUseRelatedBonus,
            correctRegularCount));
  }

  private int resolveDisplayQuestionNumber(@NonNull Stage stage) {
    if (currentQuestionIsBonus) {
      return Math.max(1, Math.min(answeredRegularCount, TOTAL_REGULAR_QUESTIONS));
    }
    if (stage == Stage.PHASE1_SELECTING) {
      return Math.max(1, Math.min(answeredRegularCount + 1, TOTAL_REGULAR_QUESTIONS));
    }
    return Math.max(1, Math.min(answeredRegularCount, TOTAL_REGULAR_QUESTIONS));
  }

  private void publishLoading(@NonNull Stage stage) {
    uiState.setValue(
        GameUiState.loading(
            stage,
            answeredRegularCount + 1,
            TOTAL_REGULAR_QUESTIONS,
            totalScore,
            streak,
            highestStreak,
            bonusUsed,
            correctRegularCount));
  }

  private void publishError(@NonNull String message, @NonNull LoadType loadType) {
    retryLoadType = loadType;
    uiState.setValue(
        GameUiState.error(
            message,
            answeredRegularCount + 1,
            TOTAL_REGULAR_QUESTIONS,
            totalScore,
            streak,
            highestStreak,
            bonusUsed,
            correctRegularCount));
  }

  private void incrementWrongTag(@NonNull NativeOrNotTag tag) {
    Integer count = wrongTagCounts.get(tag);
    wrongTagCounts.put(tag, count == null ? 1 : count + 1);
  }

  @Nullable
  private NativeOrNotTag resolveWeakTag() {
    int maxCount = 0;
    NativeOrNotTag maxTag = null;
    for (Map.Entry<NativeOrNotTag, Integer> entry : wrongTagCounts.entrySet()) {
      int count = entry.getValue() == null ? 0 : entry.getValue();
      if (count > maxCount) {
        maxCount = count;
        maxTag = entry.getKey();
      }
    }
    return maxTag;
  }

  private float resolveComboMultiplier(int streakValue) {
    if (streakValue >= 5) {
      return 2.0f;
    }
    if (streakValue >= 3) {
      return 1.5f;
    }
    return 1.0f;
  }

  public static final class GameUiState {
    @NonNull private final Stage stage;
    @Nullable private final String errorMessage;
    @Nullable private final NativeOrNotQuestion question;
    private final int selectedOptionIndex;
    private final int selectedReasonIndex;
    private final int currentQuestionNumber;
    private final int totalQuestions;
    private final int totalScore;
    private final int streak;
    private final int highestStreak;
    private final boolean hintUsed;
    @Nullable private final String hintText;
    private final boolean phase1Correct;
    private final boolean reasonCorrect;
    private final boolean bonusQuestion;
    private final boolean bonusUsed;
    private final boolean canUseRelatedBonus;
    private final int correctRegularCount;
    @Nullable private final NativeOrNotRoundResult roundResult;

    private GameUiState(
        @NonNull Stage stage,
        @Nullable String errorMessage,
        @Nullable NativeOrNotQuestion question,
        int selectedOptionIndex,
        int selectedReasonIndex,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore,
        int streak,
        int highestStreak,
        boolean hintUsed,
        @Nullable String hintText,
        boolean phase1Correct,
        boolean reasonCorrect,
        boolean bonusQuestion,
        boolean bonusUsed,
        boolean canUseRelatedBonus,
        int correctRegularCount,
        @Nullable NativeOrNotRoundResult roundResult) {
      this.stage = stage;
      this.errorMessage = errorMessage;
      this.question = question;
      this.selectedOptionIndex = selectedOptionIndex;
      this.selectedReasonIndex = selectedReasonIndex;
      this.currentQuestionNumber = Math.max(1, currentQuestionNumber);
      this.totalQuestions = Math.max(1, totalQuestions);
      this.totalScore = totalScore;
      this.streak = Math.max(0, streak);
      this.highestStreak = Math.max(0, highestStreak);
      this.hintUsed = hintUsed;
      this.hintText = hintText;
      this.phase1Correct = phase1Correct;
      this.reasonCorrect = reasonCorrect;
      this.bonusQuestion = bonusQuestion;
      this.bonusUsed = bonusUsed;
      this.canUseRelatedBonus = canUseRelatedBonus;
      this.correctRegularCount = Math.max(0, correctRegularCount);
      this.roundResult = roundResult;
    }

    @NonNull
    public static GameUiState loading(@NonNull Stage stage) {
      return new GameUiState(stage, null, null, -1, -1, 1, TOTAL_REGULAR_QUESTIONS, 0, 0, 0, false, null, false, false, false, false, false, 0, null);
    }

    @NonNull
    public static GameUiState loading(
        @NonNull Stage stage,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore,
        int streak,
        int highestStreak,
        boolean bonusUsed,
        int correctRegularCount) {
      return new GameUiState(
          stage,
          null,
          null,
          -1,
          -1,
          currentQuestionNumber,
          totalQuestions,
          totalScore,
          streak,
          highestStreak,
          false,
          null,
          false,
          false,
          false,
          bonusUsed,
          false,
          correctRegularCount,
          null);
    }

    @NonNull
    public static GameUiState error(
        @NonNull String errorMessage,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore,
        int streak,
        int highestStreak,
        boolean bonusUsed,
        int correctRegularCount) {
      return new GameUiState(
          Stage.ERROR,
          errorMessage,
          null,
          -1,
          -1,
          currentQuestionNumber,
          totalQuestions,
          totalScore,
          streak,
          highestStreak,
          false,
          null,
          false,
          false,
          false,
          bonusUsed,
          false,
          correctRegularCount,
          null);
    }

    @NonNull
    public static GameUiState question(
        @NonNull Stage stage,
        @NonNull NativeOrNotQuestion question,
        int selectedOptionIndex,
        int selectedReasonIndex,
        int currentQuestionNumber,
        int totalQuestions,
        int totalScore,
        int streak,
        int highestStreak,
        boolean hintUsed,
        @Nullable String hintText,
        boolean phase1Correct,
        boolean reasonCorrect,
        boolean bonusQuestion,
        boolean bonusUsed,
        boolean canUseRelatedBonus,
        int correctRegularCount) {
      return new GameUiState(
          stage,
          null,
          question,
          selectedOptionIndex,
          selectedReasonIndex,
          currentQuestionNumber,
          totalQuestions,
          totalScore,
          streak,
          highestStreak,
          hintUsed,
          hintText,
          phase1Correct,
          reasonCorrect,
          bonusQuestion,
          bonusUsed,
          canUseRelatedBonus,
          correctRegularCount,
          null);
    }

    @NonNull
    public static GameUiState completed(
        @NonNull NativeOrNotRoundResult result,
        int totalQuestions,
        int correctRegularCount,
        int totalScore,
        int highestStreak,
        int streak,
        boolean bonusUsed) {
      return new GameUiState(
          Stage.ROUND_COMPLETED,
          null,
          null,
          -1,
          -1,
          totalQuestions,
          totalQuestions,
          totalScore,
          streak,
          highestStreak,
          false,
          null,
          false,
          false,
          false,
          bonusUsed,
          false,
          correctRegularCount,
          result);
    }

    @NonNull
    public Stage getStage() {
      return stage;
    }

    @Nullable
    public String getErrorMessage() {
      return errorMessage;
    }

    @Nullable
    public NativeOrNotQuestion getQuestion() {
      return question;
    }

    public int getSelectedOptionIndex() {
      return selectedOptionIndex;
    }

    public int getSelectedReasonIndex() {
      return selectedReasonIndex;
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

    public int getStreak() {
      return streak;
    }

    public int getHighestStreak() {
      return highestStreak;
    }

    public boolean isHintUsed() {
      return hintUsed;
    }

    @Nullable
    public String getHintText() {
      return hintText;
    }

    public boolean isPhase1Correct() {
      return phase1Correct;
    }

    public boolean isReasonCorrect() {
      return reasonCorrect;
    }

    public boolean isBonusQuestion() {
      return bonusQuestion;
    }

    public boolean isBonusUsed() {
      return bonusUsed;
    }

    public boolean canUseRelatedBonus() {
      return canUseRelatedBonus;
    }

    public int getCorrectRegularCount() {
      return correctRegularCount;
    }

    @Nullable
    public NativeOrNotRoundResult getRoundResult() {
      return roundResult;
    }

    public boolean isOptionConfirmEnabled() {
      return selectedOptionIndex >= 0;
    }

    public boolean isReasonConfirmEnabled() {
      return selectedReasonIndex >= 0;
    }
  }
}
