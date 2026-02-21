package com.jjundev.oneclickeng.learning.dialoguelearning.di;

import android.content.Context;
import androidx.annotation.NonNull;
import com.jjundev.oneclickeng.game.minefield.manager.IMinefieldGenerationManager;
import com.jjundev.oneclickeng.game.nativeornot.manager.INativeOrNotGenerationManager;
import com.jjundev.oneclickeng.game.refiner.manager.IRefinerGenerationManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IDialogueGenerateManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IExtraQuestionManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISentenceFeedbackManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISessionSummaryLlmManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISpeakingFeedbackManager;
import com.jjundev.oneclickeng.manager_gemini.DialogueGenerateManager;
import com.jjundev.oneclickeng.manager_gemini.ExtraQuestionManager;
import com.jjundev.oneclickeng.manager_gemini.MinefieldGenerateManager;
import com.jjundev.oneclickeng.manager_gemini.NativeOrNotGenerateManager;
import com.jjundev.oneclickeng.manager_gemini.QuizGenerateManager;
import com.jjundev.oneclickeng.manager_gemini.RefinerGenerateManager;
import com.jjundev.oneclickeng.manager_gemini.SentenceFeedbackManager;
import com.jjundev.oneclickeng.manager_gemini.SpeakingFeedbackManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.summary.SessionSummaryManager;
import com.jjundev.oneclickeng.tool.AudioRecorder;

public final class LearningDependencyProvider {

  private LearningDependencyProvider() {}

  @NonNull
  public static ISpeakingFeedbackManager provideSpeakingFeedbackManager(
      @NonNull Context appContext, @NonNull String apiKey, @NonNull String modelName) {
    return new SpeakingFeedbackManager(appContext, apiKey, modelName);
  }

  @NonNull
  public static ISentenceFeedbackManager provideSentenceFeedbackManager(
      @NonNull Context appContext, @NonNull String apiKey, @NonNull String modelName) {
    return new SentenceFeedbackManager(appContext, apiKey, modelName);
  }

  @NonNull
  public static LearningManagerInitializer provideLearningManagerInitializer(
      @NonNull ISpeakingFeedbackManager speakingFeedbackManager,
      @NonNull ISentenceFeedbackManager sentenceFeedbackManager) {
    return new LearningManagerInitializer(speakingFeedbackManager, sentenceFeedbackManager, true);
  }

  @NonNull
  public static IExtraQuestionManager provideExtraQuestionManager(
      @NonNull String apiKey, @NonNull String modelName) {
    return new ExtraQuestionManager(apiKey, modelName);
  }

  @NonNull
  public static IDialogueGenerateManager provideDialogueGenerateManager(
      @NonNull Context appContext, @NonNull String apiKey, @NonNull String modelName) {
    return new DialogueGenerateManager(appContext, apiKey, modelName);
  }

  @NonNull
  public static ISessionSummaryLlmManager provideSessionSummaryLlmManager(
      @NonNull String apiKey, @NonNull String modelName) {
    return new SessionSummaryManager(apiKey, modelName);
  }

  @NonNull
  public static IQuizGenerationManager provideQuizGenerationManager(
      @NonNull Context appContext, @NonNull String apiKey, @NonNull String modelName) {
    return new QuizGenerateManager(appContext, apiKey, modelName);
  }

  @NonNull
  public static INativeOrNotGenerationManager provideNativeOrNotGenerationManager(
      @NonNull Context appContext, @NonNull String apiKey, @NonNull String modelName) {
    return new NativeOrNotGenerateManager(appContext, apiKey, modelName);
  }

  @NonNull
  public static IMinefieldGenerationManager provideMinefieldGenerationManager(
      @NonNull Context appContext, @NonNull String apiKey, @NonNull String modelName) {
    return new MinefieldGenerateManager(appContext, apiKey, modelName);
  }

  @NonNull
  public static IRefinerGenerationManager provideRefinerGenerationManager(
      @NonNull Context appContext, @NonNull String apiKey, @NonNull String modelName) {
    return new RefinerGenerateManager(appContext, apiKey, modelName);
  }

  @NonNull
  public static AudioRecorder provideAudioRecorder() {
    return new AudioRecorder();
  }
}
