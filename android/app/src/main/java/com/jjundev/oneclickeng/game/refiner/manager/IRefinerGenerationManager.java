package com.jjundev.oneclickeng.game.refiner.manager;

import androidx.annotation.NonNull;
import com.jjundev.oneclickeng.game.refiner.model.RefinerDifficulty;
import com.jjundev.oneclickeng.game.refiner.model.RefinerEvaluation;
import com.jjundev.oneclickeng.game.refiner.model.RefinerQuestion;
import java.util.Set;

public interface IRefinerGenerationManager {
  interface InitCallback {
    void onReady();

    void onError(@NonNull String error);
  }

  interface QuestionCallback {
    void onSuccess(@NonNull RefinerQuestion question);

    void onFailure(@NonNull String error);
  }

  interface EvaluationCallback {
    void onSuccess(@NonNull RefinerEvaluation evaluation);

    void onFailure(@NonNull String error);
  }

  void initializeCache(@NonNull InitCallback callback);

  void generateQuestionAsync(
      @NonNull RefinerDifficulty difficulty,
      @NonNull Set<String> excludedSignatures,
      @NonNull QuestionCallback callback);

  void evaluateAnswerAsync(
      @NonNull RefinerQuestion question,
      @NonNull String userSentence,
      @NonNull EvaluationCallback callback);
}
