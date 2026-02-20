package com.example.test.fragment.dialoguelearning.di;

import android.content.Context;
import androidx.annotation.NonNull;
import com.example.test.fragment.dialoguelearning.manager_contracts.IDialogueGenerateManager;
import com.example.test.fragment.dialoguelearning.manager_contracts.IExtraQuestionManager;
import com.example.test.fragment.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.example.test.fragment.dialoguelearning.manager_contracts.ISentenceFeedbackManager;
import com.example.test.fragment.dialoguelearning.manager_contracts.ISessionSummaryLlmManager;
import com.example.test.fragment.dialoguelearning.manager_contracts.ISpeakingFeedbackManager;
import com.example.test.manager_gemini.DialogueGenerateManager;
import com.example.test.manager_gemini.ExtraQuestionManager;
import com.example.test.manager_gemini.QuizGenerateManager;
import com.example.test.manager_gemini.SentenceFeedbackManager;
import com.example.test.manager_gemini.SpeakingFeedbackManager;
import com.example.test.summary.SessionSummaryManager;
import com.example.test.tool.AudioRecorder;

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
  public static AudioRecorder provideAudioRecorder() {
    return new AudioRecorder();
  }
}
