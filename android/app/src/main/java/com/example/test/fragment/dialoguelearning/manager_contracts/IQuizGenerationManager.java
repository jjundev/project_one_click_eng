package com.example.test.fragment.dialoguelearning.manager_contracts;

import androidx.annotation.NonNull;
import com.example.test.fragment.dialoguelearning.model.QuizData;
import com.example.test.fragment.dialoguelearning.model.SummaryData;
import java.util.List;

public interface IQuizGenerationManager {
  interface QuizCallback {
    void onSuccess(@NonNull List<QuizData.QuizQuestion> questions);

    void onFailure(@NonNull String error);
  }

  interface InitCallback {
    void onReady();

    void onError(@NonNull String error);
  }

  void initializeCache(@NonNull InitCallback callback);

  void generateQuizFromSummaryAsync(
      @NonNull SummaryData summaryData, int requestedQuestionCount, @NonNull QuizCallback callback);

  default void generateQuizFromSummaryAsync(
      @NonNull SummaryData summaryData, @NonNull QuizCallback callback) {
    generateQuizFromSummaryAsync(summaryData, 5, callback);
  }
}
