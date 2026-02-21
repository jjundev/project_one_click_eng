package com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts;

import androidx.annotation.NonNull;

public interface IDialogueGenerateManager {

  interface InitCallback {
    void onReady();

    void onError(String error);
  }

  interface ScriptGenerationCallback {
    void onSuccess(String jsonResult);

    void onError(Throwable t);
  }

  void initializeCache(@NonNull InitCallback callback);

  void generateScript(
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length,
      @NonNull ScriptGenerationCallback callback);

  @NonNull
  String getPredefinedScript(@NonNull String title);
}
