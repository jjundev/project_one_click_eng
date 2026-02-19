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

  void generateQuizFromSummaryAsync(
      @NonNull SummaryData summaryData, @NonNull QuizCallback callback);
}
