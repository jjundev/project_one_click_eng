package com.jjundev.oneclickeng.game.minefield;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.jjundev.oneclickeng.game.minefield.manager.IMinefieldGenerationManager;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldDifficulty;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldEvaluation;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldQuestion;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldRoundResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;

public class MinefieldGameViewModelTest {

  @Rule public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

  @Test
  public void initialize_movesToAnswering_whenFirstQuestionLoaded() {
    QueueManager manager = new QueueManager();
    manager.enqueueQuestion(question("Q1"));

    MinefieldGameViewModel viewModel = new MinefieldGameViewModel(manager, result -> {});
    viewModel.initialize(MinefieldDifficulty.EASY);

    MinefieldGameViewModel.GameUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(MinefieldGameViewModel.Stage.ANSWERING, state.getStage());
    assertEquals(1, state.getCurrentQuestionNumber());
    assertEquals(4, state.getTotalQuestions());
  }

  @Test
  public void submit_isInvalid_whenNoProvidedWordUsed() {
    QueueManager manager = new QueueManager();
    manager.enqueueQuestion(question("Q1"));

    MinefieldGameViewModel viewModel = new MinefieldGameViewModel(manager, result -> {});
    viewModel.initialize(MinefieldDifficulty.EASY);

    MinefieldGameViewModel.ActionResult result = viewModel.onSubmitSentence("I am late.", 0);
    assertEquals(MinefieldGameViewModel.ActionResult.INVALID, result);
  }

  @Test
  public void scoring_appliesBonusesAndPenalty() {
    QueueManager manager = new QueueManager();
    manager.enqueueQuestion(question("Q1"));
    manager.enqueueEvaluation(
        evaluation(80, 70, 100, 7, 7, true, new ArrayList<>(), "좋습니다.", "개선해보세요."));

    MinefieldGameViewModel viewModel = new MinefieldGameViewModel(manager, result -> {});
    viewModel.initialize(MinefieldDifficulty.EASY);

    viewModel.onSubmitSentence("I got stuck in traffic longer than expected.", 3);

    MinefieldGameViewModel.GameUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(MinefieldGameViewModel.Stage.FEEDBACK, state.getStage());
    assertEquals(350, state.getLastQuestionScore());
    assertEquals(350, state.getTotalScore());

    QueueManager managerWithPenalty = new QueueManager();
    managerWithPenalty.enqueueQuestion(question("Q2"));
    managerWithPenalty.enqueueEvaluation(
        evaluation(
            60,
            60,
            70,
            4,
            7,
            false,
            Arrays.asList("traffic"),
            "시도는 좋아요.",
            "필수 단어가 누락됐어요."));
    MinefieldGameViewModel penaltyVm = new MinefieldGameViewModel(managerWithPenalty, result -> {});
    penaltyVm.initialize(MinefieldDifficulty.EASY);
    penaltyVm.onSubmitSentence("Sorry I was late.", 1);

    MinefieldGameViewModel.GameUiState penaltyState = penaltyVm.getUiState().getValue();
    assertNotNull(penaltyState);
    assertEquals(170, penaltyState.getLastQuestionScore());
    assertEquals(50, penaltyState.getPenaltyMine());
  }

  @Test
  public void completeRound_afterFourQuestions() {
    QueueManager manager = new QueueManager();
    manager.enqueueQuestion(question("Q1"));
    manager.enqueueQuestion(question("Q2"));
    manager.enqueueQuestion(question("Q3"));
    manager.enqueueQuestion(question("Q4"));
    manager.enqueueEvaluation(
        evaluation(80, 70, 100, 7, 7, true, new ArrayList<>(), "s1", "i1"));
    manager.enqueueEvaluation(
        evaluation(80, 70, 100, 7, 7, true, new ArrayList<>(), "s2", "i2"));
    manager.enqueueEvaluation(
        evaluation(80, 70, 100, 7, 7, true, new ArrayList<>(), "s3", "i3"));
    manager.enqueueEvaluation(
        evaluation(80, 70, 100, 7, 7, true, new ArrayList<>(), "s4", "i4"));

    final List<MinefieldRoundResult> savedResults = new ArrayList<>();
    MinefieldGameViewModel viewModel = new MinefieldGameViewModel(manager, savedResults::add);
    viewModel.initialize(MinefieldDifficulty.EASY);

    playQuestion(viewModel);
    playQuestion(viewModel);
    playQuestion(viewModel);
    playQuestion(viewModel);

    viewModel.onNextFromFeedback();

    MinefieldGameViewModel.GameUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(MinefieldGameViewModel.Stage.ROUND_COMPLETED, state.getStage());
    assertNotNull(state.getRoundResult());
    assertEquals(1400, state.getRoundResult().getTotalScore());
    assertEquals(1, savedResults.size());
  }

  @Test
  public void retry_ignoresStaleQuestionCallbacks() {
    DelayedManager manager = new DelayedManager();
    MinefieldGameViewModel viewModel = new MinefieldGameViewModel(manager, result -> {});
    viewModel.initialize(MinefieldDifficulty.NORMAL);
    viewModel.retry();

    manager.questionCallbacks.get(0).onSuccess(question("OLD"));
    MinefieldGameViewModel.GameUiState staleState = viewModel.getUiState().getValue();
    assertNotNull(staleState);
    assertEquals(MinefieldGameViewModel.Stage.LOADING, staleState.getStage());

    manager.questionCallbacks.get(1).onSuccess(question("NEW"));
    MinefieldGameViewModel.GameUiState finalState = viewModel.getUiState().getValue();
    assertNotNull(finalState);
    assertEquals(MinefieldGameViewModel.Stage.ANSWERING, finalState.getStage());
    assertTrue(finalState.getQuestion().getSituation().contains("NEW"));
  }

  private static void playQuestion(@NonNull MinefieldGameViewModel viewModel) {
    viewModel.onSubmitSentence("I got stuck in traffic longer than expected.", 2);
    viewModel.onNextFromFeedback();
  }

  @NonNull
  private static MinefieldQuestion question(@NonNull String suffix) {
    return new MinefieldQuestion(
        "상황 " + suffix,
        "Why were you late this morning?",
        Arrays.asList("traffic", "stuck", "unexpected", "call", "sorry", "longer", "than"),
        Arrays.asList(0),
        MinefieldDifficulty.EASY);
  }

  @NonNull
  private static MinefieldEvaluation evaluation(
      int grammar,
      int naturalness,
      int wordUsage,
      int usedWordCount,
      int totalWordCount,
      boolean advancedTransform,
      @NonNull List<String> missingRequiredWords,
      @NonNull String strengths,
      @NonNull String improvement) {
    return new MinefieldEvaluation(
        grammar,
        naturalness,
        wordUsage,
        usedWordCount,
        totalWordCount,
        Arrays.asList("traffic", "stuck"),
        Arrays.asList("unexpected"),
        missingRequiredWords,
        advancedTransform,
        strengths,
        improvement,
        "I got stuck in traffic longer than expected.",
        "Basic example",
        "Intermediate example",
        "Advanced example");
  }

  private static class QueueManager implements IMinefieldGenerationManager {
    private final Queue<MinefieldQuestion> questions = new ArrayDeque<>();
    private final Queue<MinefieldEvaluation> evaluations = new ArrayDeque<>();

    void enqueueQuestion(@NonNull MinefieldQuestion question) {
      questions.add(question);
    }

    void enqueueEvaluation(@NonNull MinefieldEvaluation evaluation) {
      evaluations.add(evaluation);
    }

    @Override
    public void initializeCache(@NonNull InitCallback callback) {
      callback.onReady();
    }

    @Override
    public void generateQuestionAsync(
        @NonNull MinefieldDifficulty difficulty,
        @NonNull Set<String> excludedSignatures,
        @NonNull QuestionCallback callback) {
      MinefieldQuestion question = questions.poll();
      if (question == null) {
        callback.onFailure("no_question");
        return;
      }
      callback.onSuccess(question);
    }

    @Override
    public void evaluateAnswerAsync(
        @NonNull MinefieldQuestion question,
        @NonNull String userSentence,
        @NonNull EvaluationCallback callback) {
      MinefieldEvaluation evaluation = evaluations.poll();
      if (evaluation == null) {
        callback.onFailure("no_evaluation");
        return;
      }
      callback.onSuccess(evaluation);
    }
  }

  private static class DelayedManager implements IMinefieldGenerationManager {
    private final List<QuestionCallback> questionCallbacks = new ArrayList<>();

    @Override
    public void initializeCache(@NonNull InitCallback callback) {
      callback.onReady();
    }

    @Override
    public void generateQuestionAsync(
        @NonNull MinefieldDifficulty difficulty,
        @NonNull Set<String> excludedSignatures,
        @NonNull QuestionCallback callback) {
      questionCallbacks.add(callback);
    }

    @Override
    public void evaluateAnswerAsync(
        @NonNull MinefieldQuestion question,
        @NonNull String userSentence,
        @NonNull EvaluationCallback callback) {
      callback.onFailure("unused");
    }
  }
}
