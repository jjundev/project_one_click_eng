package com.example.test.fragment.dialoguelearning;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.test.fragment.dialoguelearning.controller.ExtraQuestionFlowController;
import com.example.test.fragment.dialoguelearning.controller.FeedbackFlowController;
import com.example.test.fragment.dialoguelearning.controller.ScriptFlowController;
import com.example.test.fragment.dialoguelearning.controller.SpeakingFlowController;
import com.example.test.fragment.dialoguelearning.di.LearningDependencyProvider;
import com.example.test.fragment.dialoguelearning.di.LearningManagerInitializer;
import com.example.test.fragment.dialoguelearning.manager_contracts.IExtraQuestionManager;
import com.example.test.fragment.dialoguelearning.manager_contracts.ISentenceFeedbackManager;
import com.example.test.fragment.dialoguelearning.manager_contracts.ISpeakingFeedbackManager;
import com.example.test.fragment.dialoguelearning.orchestrator.AsyncRequestTracker;
import com.example.test.fragment.dialoguelearning.parser.DialogueScriptParser;
import com.example.test.tool.AudioRecorder;

public class DialogueLearningViewModelFactory implements ViewModelProvider.Factory {

  private final ISpeakingFeedbackManager speakingFeedbackManager;
  private final ISentenceFeedbackManager sentenceFeedbackManager;
  private final IExtraQuestionManager extraQuestionManager;
  private final AudioRecorder audioRecorder;
  private final LearningManagerInitializer managerInitializer;
  private final AsyncRequestTracker requestTracker;

  public DialogueLearningViewModelFactory(
      @NonNull ISpeakingFeedbackManager speakingFeedbackManager,
      @NonNull ISentenceFeedbackManager sentenceFeedbackManager,
      @NonNull IExtraQuestionManager extraQuestionManager,
      @NonNull AudioRecorder audioRecorder) {
    this(
        speakingFeedbackManager,
        sentenceFeedbackManager,
        extraQuestionManager,
        audioRecorder,
        LearningDependencyProvider.provideLearningManagerInitializer(
            speakingFeedbackManager, sentenceFeedbackManager));
  }

  public DialogueLearningViewModelFactory(
      @NonNull ISpeakingFeedbackManager speakingFeedbackManager,
      @NonNull ISentenceFeedbackManager sentenceFeedbackManager,
      @NonNull IExtraQuestionManager extraQuestionManager,
      @NonNull AudioRecorder audioRecorder,
      @NonNull LearningManagerInitializer managerInitializer) {
    this.speakingFeedbackManager = speakingFeedbackManager;
    this.sentenceFeedbackManager = sentenceFeedbackManager;
    this.extraQuestionManager = extraQuestionManager;
    this.audioRecorder = audioRecorder;
    this.managerInitializer = managerInitializer;
    this.requestTracker = new AsyncRequestTracker();
  }

  @NonNull
  @Override
  @SuppressWarnings("unchecked")
  public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
    if (!modelClass.isAssignableFrom(DialogueLearningViewModel.class)) {
      throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }

    managerInitializer.initializeCaches();

    ScriptFlowController scriptFlowController =
        new ScriptFlowController(new DialogueScriptParser());
    SpeakingFlowController speakingFlowController =
        new SpeakingFlowController(
            new SpeakingFlowController.ManagerSpeakingAnalyzer(speakingFeedbackManager),
            requestTracker,
            AsyncRequestTracker.CHANNEL_SPEAKING);
    FeedbackFlowController feedbackFlowController =
        new FeedbackFlowController(
            new FeedbackFlowController.ManagerSentenceFeedbackService(sentenceFeedbackManager),
            requestTracker,
            AsyncRequestTracker.CHANNEL_FEEDBACK);
    ExtraQuestionFlowController extraQuestionFlowController =
        new ExtraQuestionFlowController(
            new ExtraQuestionFlowController.ManagerExtraQuestionService(extraQuestionManager),
            requestTracker,
            AsyncRequestTracker.CHANNEL_EXTRA);

    return (T)
        new DialogueLearningViewModel(
            scriptFlowController,
            speakingFlowController,
            feedbackFlowController,
            extraQuestionFlowController,
            audioRecorder);
  }
}
