package com.example.test.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.example.test.fragment.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.example.test.fragment.dialoguelearning.model.QuizData;
import com.example.test.fragment.dialoguelearning.model.SummaryData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public class DialogueQuizViewModelTest {

  @Rule public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

  @Test
  public void initialize_limitsQuestionsToFive() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    manager.nextQuestions = buildQuestions(7);
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");

    DialogueQuizViewModel.QuizUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, state.getStatus());
    assertEquals(5, state.getTotalQuestions());
    assertEquals(1, state.getCurrentQuestionNumber());
  }

  @Test
  public void onPrimaryAction_supportsHybridChoiceAndInputMode() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    List<QuizData.QuizQuestion> questions = new ArrayList<>();
    questions.add(new QuizData.QuizQuestion("Q1", "A1", Arrays.asList("A1", "B1"), null));
    questions.add(new QuizData.QuizQuestion("Q2", "A2", null, null));
    manager.nextQuestions = questions;
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    DialogueQuizViewModel.QuizUiState initial = viewModel.getUiState().getValue();
    assertNotNull(initial);
    assertTrue(initial.getQuestionState().isMultipleChoice());

    viewModel.onChoiceSelected("A1");
    viewModel.onPrimaryAction();
    DialogueQuizViewModel.QuizUiState checked = viewModel.getUiState().getValue();
    assertNotNull(checked);
    assertTrue(checked.getQuestionState().isChecked());
    assertTrue(checked.getQuestionState().isCorrect());

    viewModel.onPrimaryAction();
    DialogueQuizViewModel.QuizUiState second = viewModel.getUiState().getValue();
    assertNotNull(second);
    assertEquals(2, second.getCurrentQuestionNumber());
    assertFalse(second.getQuestionState().isMultipleChoice());
  }

  @Test
  public void onPrimaryAction_movesToCompleteState() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    List<QuizData.QuizQuestion> questions = new ArrayList<>();
    questions.add(new QuizData.QuizQuestion("Q1", "A1", Arrays.asList("A1", "B1"), null));
    questions.add(new QuizData.QuizQuestion("Q2", "A2", null, null));
    manager.nextQuestions = questions;
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    viewModel.onChoiceSelected("A1");
    viewModel.onPrimaryAction();
    viewModel.onPrimaryAction();
    viewModel.onAnswerInputChanged("wrong");
    viewModel.onPrimaryAction();
    viewModel.onPrimaryAction();

    DialogueQuizViewModel.QuizUiState completed = viewModel.getUiState().getValue();
    assertNotNull(completed);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.COMPLETED, completed.getStatus());
    assertEquals(2, completed.getTotalQuestions());
    assertEquals(1, completed.getCorrectAnswerCount());
  }

  @Test
  public void retryLoad_recoversFromFailure() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    manager.nextError = "network_error";
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    DialogueQuizViewModel.QuizUiState errorState = viewModel.getUiState().getValue();
    assertNotNull(errorState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.ERROR, errorState.getStatus());

    manager.nextError = null;
    manager.nextQuestions = buildQuestions(1);
    viewModel.retryLoad();

    DialogueQuizViewModel.QuizUiState recovered = viewModel.getUiState().getValue();
    assertNotNull(recovered);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, recovered.getStatus());
    assertEquals(2, manager.requestCount);
  }

  @Test
  public void checkAnswer_ignoresCaseAndWhitespace() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    manager.nextQuestions =
        Arrays.asList(new QuizData.QuizQuestion("Q1", "Look Forward To", null, null));
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    viewModel.onAnswerInputChanged("  look forward to ");
    viewModel.onPrimaryAction();

    DialogueQuizViewModel.QuizUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, state.getStatus());
    assertTrue(state.getQuestionState().isChecked());
    assertTrue(state.getQuestionState().isCorrect());
  }

  @NonNull
  private static List<QuizData.QuizQuestion> buildQuestions(int count) {
    List<QuizData.QuizQuestion> result = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      result.add(new QuizData.QuizQuestion("Question " + i, "Answer " + i));
    }
    return result;
  }

  private static class FakeQuizGenerationManager implements IQuizGenerationManager {
    @Nullable List<QuizData.QuizQuestion> nextQuestions;
    @Nullable String nextError;
    @Nullable SummaryData lastSummaryData;
    int requestCount = 0;

    @Override
    public void generateQuizFromSummaryAsync(
        @NonNull SummaryData summaryData, @NonNull QuizCallback callback) {
      requestCount++;
      lastSummaryData = summaryData;
      if (nextError != null) {
        callback.onFailure(nextError);
        return;
      }
      callback.onSuccess(nextQuestions == null ? new ArrayList<>() : nextQuestions);
    }
  }
}
