package com.jjundev.oneclickeng.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.QuizData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import com.jjundev.oneclickeng.learning.quiz.DialogueQuizViewModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public class DialogueQuizViewModelTest {

  @Rule public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

  @Test
  public void initialize_transitionsToReadyWhenFirstStreamingQuestionArrives() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");

    DialogueQuizViewModel.QuizUiState loadingState = viewModel.getUiState().getValue();
    assertNotNull(loadingState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.LOADING, loadingState.getStatus());

    manager.emitQuestion(0, buildChoiceQuestion("Q1", "A1"));

    DialogueQuizViewModel.QuizUiState readyState = viewModel.getUiState().getValue();
    assertNotNull(readyState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, readyState.getStatus());
    assertEquals("Q1", readyState.getQuestionState().getQuestion());
    assertEquals(5, readyState.getTotalQuestions());
    assertEquals(1, readyState.getCurrentQuestionNumber());
  }

  @Test
  public void onPrimaryAction_waitsUntilNextQuestionArrives() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    manager.emitQuestion(0, buildChoiceQuestion("Q1", "A1"));

    viewModel.onChoiceSelected("A1");
    assertEquals(DialogueQuizViewModel.PrimaryActionResult.CHECKED, viewModel.onPrimaryAction());
    assertEquals(
        DialogueQuizViewModel.PrimaryActionResult.WAITING_NEXT_QUESTION,
        viewModel.onPrimaryAction());

    DialogueQuizViewModel.QuizUiState waitingState = viewModel.getUiState().getValue();
    assertNotNull(waitingState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, waitingState.getStatus());
    assertEquals("Q1", waitingState.getQuestionState().getQuestion());
    assertTrue(waitingState.getQuestionState().isChecked());

    manager.emitQuestion(0, buildChoiceQuestion("Q2", "A2"));

    assertEquals(
        DialogueQuizViewModel.PrimaryActionResult.MOVED_TO_NEXT, viewModel.onPrimaryAction());
    DialogueQuizViewModel.QuizUiState secondState = viewModel.getUiState().getValue();
    assertNotNull(secondState);
    assertEquals("Q2", secondState.getQuestionState().getQuestion());
    assertEquals(2, secondState.getCurrentQuestionNumber());
  }

  @Test
  public void partialStreamCompletion_allowsCompletionWithBufferedQuestions() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    manager.emitQuestion(0, buildChoiceQuestion("Q1", "A1"));
    manager.emitQuestion(0, buildChoiceQuestion("Q2", "A2"));
    manager.completeRequest(0, "partial stream interrupted");

    DialogueQuizViewModel.QuizUiState completedStreamState = viewModel.getUiState().getValue();
    assertNotNull(completedStreamState);
    assertEquals(2, completedStreamState.getTotalQuestions());

    viewModel.onChoiceSelected("A1");
    assertEquals(DialogueQuizViewModel.PrimaryActionResult.CHECKED, viewModel.onPrimaryAction());
    assertEquals(
        DialogueQuizViewModel.PrimaryActionResult.MOVED_TO_NEXT, viewModel.onPrimaryAction());

    viewModel.onChoiceSelected("A2");
    assertEquals(DialogueQuizViewModel.PrimaryActionResult.CHECKED, viewModel.onPrimaryAction());
    assertEquals(DialogueQuizViewModel.PrimaryActionResult.COMPLETED, viewModel.onPrimaryAction());

    DialogueQuizViewModel.QuizUiState finalState = viewModel.getUiState().getValue();
    assertNotNull(finalState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.COMPLETED, finalState.getStatus());
    assertEquals(2, finalState.getTotalQuestions());
    assertEquals(2, finalState.getCorrectAnswerCount());
  }

  @Test
  public void streamFailureBeforeFirstQuestion_showsError() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    manager.failRequest(0, "network_error");

    DialogueQuizViewModel.QuizUiState errorState = viewModel.getUiState().getValue();
    assertNotNull(errorState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.ERROR, errorState.getStatus());
    assertEquals("network_error", errorState.getErrorMessage());
  }

  @Test
  public void retryLoad_ignoresStaleStreamingCallbacks() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    manager.emitQuestion(0, buildChoiceQuestion("OLD_Q1", "A1"));

    viewModel.retryLoad();
    assertEquals(2, manager.requestCount);

    manager.emitQuestion(0, buildChoiceQuestion("OLD_Q2", "A2"));
    manager.failRequest(0, "old_request_failed");

    DialogueQuizViewModel.QuizUiState loadingState = viewModel.getUiState().getValue();
    assertNotNull(loadingState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.LOADING, loadingState.getStatus());

    manager.emitQuestion(1, buildChoiceQuestion("NEW_Q1", "N1"));

    DialogueQuizViewModel.QuizUiState readyState = viewModel.getUiState().getValue();
    assertNotNull(readyState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, readyState.getStatus());
    assertEquals("NEW_Q1", readyState.getQuestionState().getQuestion());
  }

  @NonNull
  private static QuizData.QuizQuestion buildChoiceQuestion(
      @NonNull String question, @NonNull String answer) {
    return new QuizData.QuizQuestion(
        question, answer, Arrays.asList(answer, "Wrong-A", "Wrong-B"), null);
  }

  private static class FakeQuizGenerationManager implements IQuizGenerationManager {
    @NonNull
    private final List<IQuizGenerationManager.QuizStreamingCallback> streamingCallbacks =
        new ArrayList<>();

    @Nullable SummaryData lastSummaryData;
    int requestCount = 0;

    @Override
    public void initializeCache(@NonNull InitCallback callback) {
      callback.onReady();
    }

    @Override
    public void generateQuizFromSummaryAsync(
        @NonNull SummaryData summaryData,
        int requestedQuestionCount,
        @NonNull QuizCallback callback) {
      callback.onFailure("one-shot path is not used in this test");
    }

    @Override
    public void generateQuizFromSummaryStreamingAsync(
        @NonNull SummaryData summaryData,
        int requestedQuestionCount,
        @NonNull QuizStreamingCallback callback) {
      requestCount++;
      lastSummaryData = summaryData;
      streamingCallbacks.add(callback);
    }

    void emitQuestion(int requestIndex, @NonNull QuizData.QuizQuestion question) {
      streamingCallbacks.get(requestIndex).onQuestion(question);
    }

    void completeRequest(int requestIndex, @Nullable String warning) {
      streamingCallbacks.get(requestIndex).onComplete(warning);
    }

    void failRequest(int requestIndex, @NonNull String error) {
      streamingCallbacks.get(requestIndex).onFailure(error);
    }
  }
}
