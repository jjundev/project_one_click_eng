package com.jjundev.oneclickeng.game.nativeornot.manager;

import androidx.annotation.NonNull;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotDifficulty;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotQuestion;
import java.util.Set;

public interface INativeOrNotGenerationManager {
  interface InitCallback {
    void onReady();

    void onError(@NonNull String error);
  }

  interface QuestionCallback {
    void onSuccess(@NonNull NativeOrNotQuestion question);

    void onFailure(@NonNull String error);
  }

  void initializeCache(@NonNull InitCallback callback);

  void generateQuestionAsync(
      @NonNull NativeOrNotDifficulty difficulty,
      @NonNull Set<String> excludedSignatures,
      @NonNull QuestionCallback callback);

  void generateRelatedQuestionAsync(
      @NonNull NativeOrNotQuestion baseQuestion,
      @NonNull Set<String> excludedSignatures,
      @NonNull QuestionCallback callback);
}
