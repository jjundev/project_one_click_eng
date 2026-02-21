package com.jjundev.oneclickeng.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.jjundev.oneclickeng.fragment.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.jjundev.oneclickeng.fragment.dialoguelearning.model.QuizData;
import com.jjundev.oneclickeng.fragment.dialoguelearning.model.SummaryData;
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
    manager.nextQuestions = buildChoiceQuestions(7);
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");

    DialogueQuizViewModel.QuizUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, state.getStatus());
    assertEquals(5, state.getTotalQuestions());
    assertEquals(1, state.getCurrentQuestionNumber());
  }

  @Test
  public void onPrimaryAction_checksAndMovesToNextQuestion() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    List<QuizData.QuizQuestion> questions = new ArrayList<>();
    questions.add(new QuizData.QuizQuestion("Q1", "A1", Arrays.asList("A1", "B1"), null));
    questions.add(new QuizData.QuizQuestion("Q2", "A2", Arrays.asList("A2", "B2"), null));
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
    assertTrue(second.getQuestionState().isMultipleChoice());
    assertFalse(second.getQuestionState().isChecked());
  }

  @Test
  public void onPrimaryAction_movesToCompleteState() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    List<QuizData.QuizQuestion> questions = new ArrayList<>();
    questions.add(new QuizData.QuizQuestion("Q1", "A1", Arrays.asList("A1", "B1"), null));
    questions.add(new QuizData.QuizQuestion("Q2", "A2", Arrays.asList("A2", "B2"), null));
    manager.nextQuestions = questions;
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    viewModel.onChoiceSelected("A1");
    viewModel.onPrimaryAction();
    viewModel.onPrimaryAction();
    viewModel.onChoiceSelected("B2");
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
    manager.nextQuestions = buildChoiceQuestions(1);
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
        Arrays.asList(
            new QuizData.QuizQuestion(
                "Q1", "Look Forward To", Arrays.asList("  look forward to ", "give up"), null));
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    viewModel.onChoiceSelected("  look forward to ");
    viewModel.onPrimaryAction();

    DialogueQuizViewModel.QuizUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, state.getStatus());
    assertTrue(state.getQuestionState().isChecked());
    assertTrue(state.getQuestionState().isCorrect());
  }

  @Test
  public void initialize_skipsQuestionsWithoutEnoughChoices() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    manager.nextQuestions =
        Arrays.asList(
            new QuizData.QuizQuestion("Q1", "A1", null, null),
            new QuizData.QuizQuestion("Q2", "A2", Arrays.asList("A2"), null),
            new QuizData.QuizQuestion("Q3", "A3", Arrays.asList("A3", "B3"), null),
            new QuizData.QuizQuestion("Q4", "A4", Arrays.asList("A4", "B4", "C4"), null));
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");

    DialogueQuizViewModel.QuizUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, state.getStatus());
    assertEquals(2, state.getTotalQuestions());
    assertEquals("Q3", state.getQuestionState().getQuestion());
  }

  @Test
  public void initialize_returnsErrorWhenAllQuestionsAreInvalid() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    manager.nextQuestions =
        Arrays.asList(
            new QuizData.QuizQuestion("Q1", "A1", null, null),
            new QuizData.QuizQuestion("Q2", "A2", Arrays.asList("A2"), null));
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");

    DialogueQuizViewModel.QuizUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.ERROR, state.getStatus());
  }

  @NonNull
  private static List<QuizData.QuizQuestion> buildChoiceQuestions(int count) {
    List<QuizData.QuizQuestion> result = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      result.add(
          new QuizData.QuizQuestion(
              "Question " + i,
              "Answer " + i,
              Arrays.asList("Answer " + i, "Wrong " + i + " A", "Wrong " + i + " B"),
              null));
    }
    return result;
  }

  private static class FakeQuizGenerationManager implements IQuizGenerationManager {
    @Nullable List<QuizData.QuizQuestion> nextQuestions;
    @Nullable String nextError;
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
