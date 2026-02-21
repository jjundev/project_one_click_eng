package com.jjundev.oneclickeng.game.minefield.manager;

import androidx.annotation.NonNull;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldDifficulty;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldEvaluation;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldQuestion;
import java.util.Set;

public interface IMinefieldGenerationManager {
  interface InitCallback {
    void onReady();

    void onError(@NonNull String error);
  }

  interface QuestionCallback {
    void onSuccess(@NonNull MinefieldQuestion question);

    void onFailure(@NonNull String error);
  }

  interface EvaluationCallback {
    void onSuccess(@NonNull MinefieldEvaluation evaluation);

    void onFailure(@NonNull String error);
  }

  void initializeCache(@NonNull InitCallback callback);

  void generateQuestionAsync(
      @NonNull MinefieldDifficulty difficulty,
      @NonNull Set<String> excludedSignatures,
      @NonNull QuestionCallback callback);

  void evaluateAnswerAsync(
      @NonNull MinefieldQuestion question,
      @NonNull String userSentence,
      @NonNull EvaluationCallback callback);
}
