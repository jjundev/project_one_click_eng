package com.jjundev.oneclickeng.game.refiner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.jjundev.oneclickeng.game.refiner.manager.IRefinerGenerationManager;
import com.jjundev.oneclickeng.game.refiner.model.RefinerConstraints;
import com.jjundev.oneclickeng.game.refiner.model.RefinerDifficulty;
import com.jjundev.oneclickeng.game.refiner.model.RefinerEvaluation;
import com.jjundev.oneclickeng.game.refiner.model.RefinerLevel;
import com.jjundev.oneclickeng.game.refiner.model.RefinerLevelExample;
import com.jjundev.oneclickeng.game.refiner.model.RefinerQuestion;
import com.jjundev.oneclickeng.game.refiner.model.RefinerRoundResult;
import com.jjundev.oneclickeng.game.refiner.model.RefinerWordLimit;
import com.jjundev.oneclickeng.game.refiner.model.RefinerWordLimitMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;

public class RefinerGameViewModelTest {

  @Rule public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

  @Test
  public void initialize_movesToAnswering_whenFirstQuestionLoaded() {
    QueueManager manager = new QueueManager();
    manager.enqueueQuestion(question("Q1", true));

    RefinerGameViewModel viewModel = new RefinerGameViewModel(manager, result -> {});
    viewModel.initialize(RefinerDifficulty.EASY);

    RefinerGameViewModel.GameUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(RefinerGameViewModel.Stage.ANSWERING, state.getStage());
    assertEquals(1, state.getCurrentQuestionNumber());
    assertEquals(5, state.getTotalQuestions());
  }

  @Test
  public void submit_isInvalid_whenConstraintNotSatisfied() {
    QueueManager manager = new QueueManager();
    manager.enqueueQuestion(question("Q1", true));

    RefinerGameViewModel viewModel = new RefinerGameViewModel(manager, result -> {});
    viewModel.initialize(RefinerDifficulty.NORMAL);

    RefinerGameViewModel.ActionResult result =
        viewModel.onSubmitSentence("Could I request more time?", false);
    assertEquals(RefinerGameViewModel.ActionResult.INVALID, result);
  }

  @Test
  public void scoring_reflectsHintQuickAndCreativeBonus() {
    QueueManager managerNoHint = new QueueManager();
    managerNoHint.enqueueQuestion(question("Q1", true));
    managerNoHint.enqueueEvaluation(evaluation(RefinerLevel.B2, true));
    RefinerGameViewModel noHintVm = new RefinerGameViewModel(managerNoHint, result -> {});
    noHintVm.initialize(RefinerDifficulty.HARD);
    noHintVm.onSubmitSentence("Could I appreciate a short extension?", true);
    RefinerGameViewModel.GameUiState noHintState = noHintVm.getUiState().getValue();
    assertNotNull(noHintState);
    assertEquals(370, noHintState.getLastQuestionScore()); // 270 + 50 + 20 + 30

    QueueManager managerHint = new QueueManager();
    managerHint.enqueueQuestion(question("Q2", true));
    managerHint.enqueueEvaluation(evaluation(RefinerLevel.B2, true));
    RefinerGameViewModel hintVm = new RefinerGameViewModel(managerHint, result -> {});
    hintVm.initialize(RefinerDifficulty.HARD);
    hintVm.onHintRequested();
    hintVm.onSubmitSentence("Could I appreciate a short extension?", true);
    RefinerGameViewModel.GameUiState hintState = hintVm.getUiState().getValue();
    assertNotNull(hintState);
    assertEquals(290, hintState.getLastQuestionScore()); // 270 - 30 + 20 + 30
  }

  @Test
  public void completeRound_afterFiveQuestions() {
    QueueManager manager = new QueueManager();
    for (int i = 1; i <= 5; i++) {
      manager.enqueueQuestion(question("Q" + i, false));
      manager.enqueueEvaluation(evaluation(RefinerLevel.B1, false));
    }

    final List<RefinerRoundResult> savedResults = new ArrayList<>();
    RefinerGameViewModel viewModel = new RefinerGameViewModel(manager, savedResults::add);
    viewModel.initialize(RefinerDifficulty.EASY);

    for (int i = 0; i < 5; i++) {
      viewModel.onSubmitSentence("Could I request a brief extension?", true);
      viewModel.onNextFromFeedback();
    }

    RefinerGameViewModel.GameUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(RefinerGameViewModel.Stage.ROUND_COMPLETED, state.getStage());
    assertNotNull(state.getRoundResult());
    assertEquals(1, savedResults.size());
  }

  @Test
  public void retry_ignoresStaleQuestionCallbacks() {
    DelayedManager manager = new DelayedManager();
    RefinerGameViewModel viewModel = new RefinerGameViewModel(manager, result -> {});
    viewModel.initialize(RefinerDifficulty.NORMAL);
    viewModel.retry();

    manager.questionCallbacks.get(0).onSuccess(question("OLD", true));
    RefinerGameViewModel.GameUiState staleState = viewModel.getUiState().getValue();
    assertNotNull(staleState);
    assertEquals(RefinerGameViewModel.Stage.LOADING, staleState.getStage());

    manager.questionCallbacks.get(1).onSuccess(question("NEW", true));
    RefinerGameViewModel.GameUiState finalState = viewModel.getUiState().getValue();
    assertNotNull(finalState);
    assertEquals(RefinerGameViewModel.Stage.ANSWERING, finalState.getStage());
    assertTrue(finalState.getQuestion().getSourceSentence().contains("NEW"));
  }

  @NonNull
  private static RefinerQuestion question(@NonNull String suffix, boolean withRequiredWord) {
    RefinerConstraints constraints =
        withRequiredWord
            ? new RefinerConstraints(
                null,
                new RefinerWordLimit(RefinerWordLimitMode.MAX, 10),
                "appreciate")
            : new RefinerConstraints(
                Collections.singletonList("want"),
                new RefinerWordLimit(RefinerWordLimitMode.MAX, 10),
                "");
    return new RefinerQuestion(
        "I want you to give me more time for the project " + suffix + ".",
        "비즈니스 이메일",
        constraints,
        "request/extension 중 하나를 써보세요.",
        RefinerDifficulty.NORMAL);
  }

  @NonNull
  private static RefinerEvaluation evaluation(@NonNull RefinerLevel level, boolean creative) {
    List<RefinerLevelExample> examples =
        Arrays.asList(
            new RefinerLevelExample(RefinerLevel.A2, "Please give me more time.", "직접적입니다."),
            new RefinerLevelExample(RefinerLevel.B1, "Can I get more time?", "무난합니다."),
            new RefinerLevelExample(
                RefinerLevel.B2, "Could I request a deadline extension?", "간결하고 격식 있습니다."),
            new RefinerLevelExample(
                RefinerLevel.C1,
                "I'd appreciate some flexibility on the deadline.",
                "완곡한 고급 표현입니다."),
            new RefinerLevelExample(
                RefinerLevel.C2,
                "Would it be possible to revisit the timeline on this?",
                "협상 뉘앙스가 있습니다."));
    return new RefinerEvaluation(level, 80, 78, 82, 90, creative, "완곡 표현을 연습해보세요.", examples);
  }

  private static class QueueManager implements IRefinerGenerationManager {
    private final Queue<RefinerQuestion> questions = new ArrayDeque<>();
    private final Queue<RefinerEvaluation> evaluations = new ArrayDeque<>();

    void enqueueQuestion(@NonNull RefinerQuestion question) {
      questions.add(question);
    }

    void enqueueEvaluation(@NonNull RefinerEvaluation evaluation) {
      evaluations.add(evaluation);
    }

    @Override
    public void initializeCache(@NonNull InitCallback callback) {
      callback.onReady();
    }

    @Override
    public void generateQuestionAsync(
        @NonNull RefinerDifficulty difficulty,
        @NonNull Set<String> excludedSignatures,
        @NonNull QuestionCallback callback) {
      RefinerQuestion question = questions.poll();
      if (question == null) {
        callback.onFailure("no_question");
        return;
      }
      callback.onSuccess(question);
    }

    @Override
    public void evaluateAnswerAsync(
        @NonNull RefinerQuestion question,
        @NonNull String userSentence,
        @NonNull EvaluationCallback callback) {
      RefinerEvaluation evaluation = evaluations.poll();
      if (evaluation == null) {
        callback.onFailure("no_evaluation");
        return;
      }
      callback.onSuccess(evaluation);
    }
  }

  private static class DelayedManager implements IRefinerGenerationManager {
    private final List<QuestionCallback> questionCallbacks = new ArrayList<>();

    @Override
    public void initializeCache(@NonNull InitCallback callback) {
      callback.onReady();
    }

    @Override
    public void generateQuestionAsync(
        @NonNull RefinerDifficulty difficulty,
        @NonNull Set<String> excludedSignatures,
        @NonNull QuestionCallback callback) {
      questionCallbacks.add(callback);
    }

    @Override
    public void evaluateAnswerAsync(
        @NonNull RefinerQuestion question,
        @NonNull String userSentence,
        @NonNull EvaluationCallback callback) {
      callback.onFailure("unused");
    }
  }
}
