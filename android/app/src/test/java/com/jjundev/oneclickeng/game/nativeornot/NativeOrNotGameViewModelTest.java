package com.jjundev.oneclickeng.game.nativeornot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.jjundev.oneclickeng.game.nativeornot.manager.INativeOrNotGenerationManager;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotDifficulty;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotQuestion;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotRoundResult;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotTag;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;

public class NativeOrNotGameViewModelTest {

  @Rule public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

  @Test
  public void initialize_movesToPhase1_whenFirstQuestionLoaded() {
    QueueManager manager = new QueueManager();
    manager.enqueueRegular(question("상황1", NativeOrNotTag.SPOKEN));

    NativeOrNotGameViewModel viewModel =
        new NativeOrNotGameViewModel(manager, result -> {});
    viewModel.initialize();

    NativeOrNotGameViewModel.GameUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(NativeOrNotGameViewModel.Stage.PHASE1_SELECTING, state.getStage());
    assertEquals(1, state.getCurrentQuestionNumber());
    assertEquals(5, state.getTotalQuestions());
  }

  @Test
  public void hintPenalty_andReasonBonus_areApplied() {
    QueueManager manager = new QueueManager();
    manager.enqueueRegular(question("상황1", NativeOrNotTag.SPOKEN));

    NativeOrNotGameViewModel viewModel =
        new NativeOrNotGameViewModel(manager, result -> {});
    viewModel.initialize();

    viewModel.onHintRequested();
    viewModel.onOptionSelected(1);
    viewModel.onConfirmOption();
    viewModel.onReasonSelected(1);
    viewModel.onConfirmReason();

    NativeOrNotGameViewModel.GameUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(NativeOrNotGameViewModel.Stage.PHASE3_EXPLANATION, state.getStage());
    assertEquals(130, state.getTotalScore());
    assertEquals(1, state.getStreak());
  }

  @Test
  public void comboMultiplier_appliesAtThirdStreak() {
    QueueManager manager = new QueueManager();
    manager.enqueueRegular(question("상황1", NativeOrNotTag.SPOKEN));
    manager.enqueueRegular(question("상황2", NativeOrNotTag.SPOKEN));
    manager.enqueueRegular(question("상황3", NativeOrNotTag.SPOKEN));

    NativeOrNotGameViewModel viewModel =
        new NativeOrNotGameViewModel(manager, result -> {});
    viewModel.initialize();

    playRegular(viewModel, 1, 0, true);
    playRegular(viewModel, 1, 0, true);
    playRegular(viewModel, 1, 0, false);

    NativeOrNotGameViewModel.GameUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(NativeOrNotGameViewModel.Stage.PHASE3_EXPLANATION, state.getStage());
    assertEquals(440, state.getTotalScore());
    assertEquals(3, state.getStreak());
  }

  @Test
  public void retryLoad_ignoresStaleCallbacks() {
    DelayedManager manager = new DelayedManager();
    NativeOrNotGameViewModel viewModel =
        new NativeOrNotGameViewModel(manager, result -> {});

    viewModel.initialize();
    viewModel.retryLoad();

    manager.regularCallbacks.get(0).onSuccess(question("OLD", NativeOrNotTag.SPOKEN));
    NativeOrNotGameViewModel.GameUiState staleState = viewModel.getUiState().getValue();
    assertNotNull(staleState);
    assertEquals(NativeOrNotGameViewModel.Stage.LOADING_QUESTION, staleState.getStage());

    manager.regularCallbacks.get(1).onSuccess(question("NEW", NativeOrNotTag.REGISTER));
    NativeOrNotGameViewModel.GameUiState finalState = viewModel.getUiState().getValue();
    assertNotNull(finalState);
    assertEquals(NativeOrNotGameViewModel.Stage.PHASE1_SELECTING, finalState.getStage());
    assertTrue(finalState.getQuestion().getSituation().contains("NEW"));
  }

  @Test
  public void completeRound_savesResultAndWeakTag() {
    QueueManager manager = new QueueManager();
    manager.enqueueRegular(question("상황1", NativeOrNotTag.LITERAL_TRANSLATION));
    manager.enqueueRegular(question("상황2", NativeOrNotTag.LITERAL_TRANSLATION));
    manager.enqueueRegular(question("상황3", NativeOrNotTag.LITERAL_TRANSLATION));
    manager.enqueueRegular(question("상황4", NativeOrNotTag.SPOKEN));
    manager.enqueueRegular(question("상황5", NativeOrNotTag.SPOKEN));

    final List<NativeOrNotRoundResult> savedResults = new ArrayList<>();
    NativeOrNotGameViewModel viewModel =
        new NativeOrNotGameViewModel(manager, savedResults::add);
    viewModel.initialize();

    // Intentionally wrong selection to build weak-tag stats.
    playRegular(viewModel, 0, 0, true);
    playRegular(viewModel, 0, 0, true);
    playRegular(viewModel, 0, 0, true);
    playRegular(viewModel, 0, 0, true);
    playRegular(viewModel, 0, 0, true);

    viewModel.onNextFromExplanation();

    NativeOrNotGameViewModel.GameUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(NativeOrNotGameViewModel.Stage.ROUND_COMPLETED, state.getStage());
    assertNotNull(state.getRoundResult());
    assertEquals(NativeOrNotTag.LITERAL_TRANSLATION, state.getRoundResult().getWeakTag());
    assertEquals(1, savedResults.size());
  }

  private static void playRegular(
      @NonNull NativeOrNotGameViewModel viewModel,
      int selectedOption,
      int selectedReason,
      boolean moveNext) {
    viewModel.onOptionSelected(selectedOption);
    viewModel.onConfirmOption();
    viewModel.onReasonSelected(selectedReason);
    viewModel.onConfirmReason();
    if (moveNext) {
      viewModel.onNextFromExplanation();
    }
  }

  @NonNull
  private static NativeOrNotQuestion question(@NonNull String suffix, @NonNull NativeOrNotTag tag) {
    return new NativeOrNotQuestion(
        "상황 " + suffix,
        Arrays.asList(
            "I haven't seen you for a long time " + suffix,
            "Long time no see " + suffix,
            "It's been a while " + suffix),
        1,
        0,
        Arrays.asList("문법 오류", "짧은 표현 선호", "격식체", "단어 선택"),
        1,
        "교과서식 문장보다 짧은 관용구가 자연스럽습니다.",
        "짧고 리드미컬한 표현을 먼저 떠올리세요.",
        tag,
        "구어체에서는 짧은 표현을 우선해보세요.",
        NativeOrNotDifficulty.EASY);
  }

  private static class QueueManager implements INativeOrNotGenerationManager {
    private final Queue<NativeOrNotQuestion> regularQuestions = new ArrayDeque<>();
    private final Queue<NativeOrNotQuestion> relatedQuestions = new ArrayDeque<>();

    void enqueueRegular(@NonNull NativeOrNotQuestion question) {
      regularQuestions.add(question);
    }

    void enqueueRelated(@NonNull NativeOrNotQuestion question) {
      relatedQuestions.add(question);
    }

    @Override
    public void initializeCache(@NonNull InitCallback callback) {
      callback.onReady();
    }

    @Override
    public void generateQuestionAsync(
        @NonNull NativeOrNotDifficulty difficulty,
        @NonNull Set<String> excludedSignatures,
        @NonNull QuestionCallback callback) {
      NativeOrNotQuestion question = regularQuestions.poll();
      if (question == null) {
        callback.onFailure("no_regular_question");
        return;
      }
      callback.onSuccess(question);
    }

    @Override
    public void generateRelatedQuestionAsync(
        @NonNull NativeOrNotQuestion baseQuestion,
        @NonNull Set<String> excludedSignatures,
        @NonNull QuestionCallback callback) {
      NativeOrNotQuestion question = relatedQuestions.poll();
      if (question == null) {
        callback.onFailure("no_related_question");
        return;
      }
      callback.onSuccess(question);
    }
  }

  private static class DelayedManager implements INativeOrNotGenerationManager {
    private final List<QuestionCallback> regularCallbacks = new ArrayList<>();

    @Override
    public void initializeCache(@NonNull InitCallback callback) {
      callback.onReady();
    }

    @Override
    public void generateQuestionAsync(
        @NonNull NativeOrNotDifficulty difficulty,
        @NonNull Set<String> excludedSignatures,
        @NonNull QuestionCallback callback) {
      regularCallbacks.add(callback);
    }

    @Override
    public void generateRelatedQuestionAsync(
        @NonNull NativeOrNotQuestion baseQuestion,
        @NonNull Set<String> excludedSignatures,
        @NonNull QuestionCallback callback) {
      callback.onFailure("unused");
    }
  }
}
