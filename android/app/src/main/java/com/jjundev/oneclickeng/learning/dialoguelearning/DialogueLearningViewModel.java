package com.jjundev.oneclickeng.learning.dialoguelearning;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.ExtraQuestionFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.FeedbackFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.ScriptFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.SpeakingFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.DialogueScript;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ScriptTurn;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SpeakingAnalysisResult;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.LearningSessionOrchestrator;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.LearningSessionSnapshot;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.BottomSheetMode;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.ConsumableEvent;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.DialogueUiEvent;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.ExtraQuestionUiState;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.FeedbackUiState;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.ScriptUiState;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.SpeakingUiState;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.ChatMessage;
import com.jjundev.oneclickeng.tool.AudioRecorder;
import java.util.ArrayList;
import java.util.List;

public class DialogueLearningViewModel extends ViewModel {

  private final ScriptFlowController scriptFlowController;
  private final SpeakingFlowController speakingFlowController;
  private final FeedbackFlowController feedbackFlowController;
  private final ExtraQuestionFlowController extraQuestionFlowController;
  private final LearningSessionOrchestrator sessionOrchestrator;
  @Nullable private final AudioRecorder audioRecorder;

  private final MutableLiveData<BottomSheetMode> bottomSheetMode =
      new MutableLiveData<>(BottomSheetMode.DEFAULT_INPUT);
  private final MutableLiveData<ScriptUiState> scriptUiState =
      new MutableLiveData<>(ScriptUiState.empty());
  private final MutableLiveData<SpeakingUiState> speakingUiState =
      new MutableLiveData<>(SpeakingUiState.idle());
  private final MutableLiveData<FeedbackUiState> feedbackUiState =
      new MutableLiveData<>(FeedbackUiState.idle());
  private final MutableLiveData<ConsumableEvent<DialogueUiEvent>> uiEvent = new MutableLiveData<>();
  private final MutableLiveData<LearningSessionSnapshot> sessionSnapshot =
      new MutableLiveData<>(
          new LearningSessionSnapshot(
              ScriptUiState.empty(),
              SpeakingUiState.idle(),
              FeedbackUiState.idle(),
              BottomSheetMode.DEFAULT_INPUT));
  @NonNull private final List<ChatMessage> retainedChatMessages = new ArrayList<>();

  private long speakingEmissionId = 0L;
  private long feedbackEmissionId = 0L;
  private long extraEmissionId = 0L;
  private final StringBuilder extraResponseBuilder = new StringBuilder();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  public DialogueLearningViewModel(
      @NonNull ScriptFlowController scriptFlowController,
      @NonNull SpeakingFlowController speakingFlowController,
      @NonNull FeedbackFlowController feedbackFlowController,
      @NonNull ExtraQuestionFlowController extraQuestionFlowController,
      @Nullable AudioRecorder audioRecorder) {
    this(
        scriptFlowController,
        speakingFlowController,
        feedbackFlowController,
        extraQuestionFlowController,
        audioRecorder,
        new LearningSessionOrchestrator(
            scriptFlowController,
            speakingFlowController,
            feedbackFlowController,
            extraQuestionFlowController));
  }

  public DialogueLearningViewModel(
      @NonNull ScriptFlowController scriptFlowController,
      @NonNull SpeakingFlowController speakingFlowController,
      @NonNull FeedbackFlowController feedbackFlowController,
      @NonNull ExtraQuestionFlowController extraQuestionFlowController,
      @Nullable AudioRecorder audioRecorder,
      @NonNull LearningSessionOrchestrator sessionOrchestrator) {
    this.scriptFlowController = scriptFlowController;
    this.speakingFlowController = speakingFlowController;
    this.feedbackFlowController = feedbackFlowController;
    this.extraQuestionFlowController = extraQuestionFlowController;
    this.sessionOrchestrator = sessionOrchestrator;
    this.audioRecorder = audioRecorder;
    publishSessionSnapshot();
  }

  public LiveData<BottomSheetMode> getBottomSheetMode() {
    return bottomSheetMode;
  }

  public LiveData<ScriptUiState> getScriptUiState() {
    return scriptUiState;
  }

  public LiveData<SpeakingUiState> getSpeakingUiState() {
    return speakingUiState;
  }

  public LiveData<FeedbackUiState> getFeedbackUiState() {
    return feedbackUiState;
  }

  public LiveData<ConsumableEvent<DialogueUiEvent>> getUiEvent() {
    return uiEvent;
  }

  public LiveData<LearningSessionSnapshot> getSessionSnapshotState() {
    return sessionSnapshot;
  }

  public void setBottomSheetMode(@NonNull BottomSheetMode mode) {
    BottomSheetMode currentMode = bottomSheetMode.getValue();
    if (currentMode == mode) {
      return;
    }
    applyStateAndPublish(() -> bottomSheetMode.setValue(mode));
  }

  @NonNull
  public List<ChatMessage> getRetainedChatMessages() {
    return retainedChatMessages;
  }

  public void emitScriptTtsEvent(@NonNull String scriptText) {
    emitUiEvent(new DialogueUiEvent.PlayScriptTts(scriptText));
  }

  public LearningSessionSnapshot getSessionSnapshot() {
    return buildSessionSnapshot();
  }

  private LearningSessionSnapshot buildSessionSnapshot() {
    ScriptUiState currentScript = scriptUiState.getValue();
    if (currentScript == null) {
      currentScript = ScriptUiState.empty();
    }

    SpeakingUiState currentSpeaking = speakingUiState.getValue();
    if (currentSpeaking == null) {
      currentSpeaking = SpeakingUiState.idle();
    }

    FeedbackUiState currentFeedback = feedbackUiState.getValue();
    if (currentFeedback == null) {
      currentFeedback = FeedbackUiState.idle();
    }

    BottomSheetMode currentMode = bottomSheetMode.getValue();
    if (currentMode == null) {
      currentMode = BottomSheetMode.DEFAULT_INPUT;
    }

    return new LearningSessionSnapshot(
        currentScript, currentSpeaking, currentFeedback, currentMode);
  }

  /**
   * Internal state mutation contract: 1) mutate state 2) publish session snapshot exactly once from
   * same main-thread frame.
   */
  private void applyStateAndPublish(@NonNull Runnable stateMutation) {
    runOnMainThread(
        () -> {
          stateMutation.run();
          sessionSnapshot.setValue(buildSessionSnapshot());
        });
  }

  private void publishSessionSnapshot() {
    runOnMainThread(() -> sessionSnapshot.setValue(buildSessionSnapshot()));
  }

  private void runOnMainThread(@NonNull Runnable action) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      action.run();
      return;
    }
    mainHandler.post(action);
  }

  private void emitUiEvent(@NonNull DialogueUiEvent event) {
    uiEvent.postValue(new ConsumableEvent<>(event));
  }

  public boolean loadScriptData(@NonNull String scriptJson) {
    try {
      sessionOrchestrator.loadScript(scriptJson);
      DialogueScript script = scriptFlowController.getScript();
      if (script == null) {
        emitUiEvent(new DialogueUiEvent.ShowToast("데이터 로드 실패"));
        return false;
      }

      applyStateAndPublish(
          () ->
              scriptUiState.setValue(
                  new ScriptUiState(
                      0,
                      script.size(),
                      script.getTopic(),
                      script.getOpponentName(),
                      script.getOpponentGender(),
                      false,
                      null)));
      return true;
    } catch (Exception e) {
      emitUiEvent(new DialogueUiEvent.ShowToast("데이터 로드 실패"));
      return false;
    }
  }

  @NonNull
  public List<ScriptTurn> getScriptTurnsSnapshot() {
    DialogueScript script = scriptFlowController.getScript();
    if (script == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(script.getTurns());
  }

  @NonNull
  public LearningSessionOrchestrator.TurnDecision moveToNextTurnDecision() {
    LearningSessionOrchestrator.TurnDecision turnDecision =
        sessionOrchestrator.moveToNextTurnDecision();
    ScriptFlowController.NextTurnResult result = turnDecision.getNextTurnResult();

    ScriptUiState prev = scriptUiState.getValue();
    String topic = prev == null ? "" : prev.getTopic();
    String opponentName = prev == null ? "" : prev.getOpponentName();
    String opponentGender = prev == null ? "female" : prev.getOpponentGender();

    if (result.getType() == ScriptFlowController.NextTurnResult.Type.FINISHED) {
      applyStateAndPublish(
          () -> {
            scriptUiState.setValue(
                new ScriptUiState(
                    result.getCurrentStep(),
                    result.getTotalSteps(),
                    topic,
                    opponentName,
                    opponentGender,
                    true,
                    null));
            bottomSheetMode.setValue(BottomSheetMode.FINISHED);
          });
      return turnDecision;
    }

    if (result.getType() == ScriptFlowController.NextTurnResult.Type.TURN) {
      ScriptTurn turn = result.getTurn();
      applyStateAndPublish(
          () -> {
            scriptUiState.setValue(
                new ScriptUiState(
                    result.getCurrentStep(),
                    result.getTotalSteps(),
                    topic,
                    opponentName,
                    opponentGender,
                    false,
                    turn));

            if (turn != null && !turn.isOpponentTurn()) {
              bottomSheetMode.setValue(BottomSheetMode.DEFAULT_INPUT);
            } else if (turn != null
                && turnDecision != null
                && turnDecision.getProposedBottomSheetMode() == BottomSheetMode.BEFORE_SPEAKING) {
              bottomSheetMode.setValue(BottomSheetMode.BEFORE_SPEAKING);
            }
          });
    }

    return turnDecision;
  }

  @NonNull
  public ScriptFlowController.NextTurnResult moveToNextTurn() {
    return moveToNextTurnDecision().getNextTurnResult();
  }

  @Nullable
  public LearningSessionOrchestrator.TurnDecision getLastTurnDecision() {
    return sessionOrchestrator.getLastTurnDecision();
  }

  public void onRecordingRequested(@NonNull String sentenceToTranslate) {
    sessionOrchestrator.onRecordingRequested(sentenceToTranslate);
    emitUiEvent(new DialogueUiEvent.RequestMicPermission(sentenceToTranslate));
  }

  public void onRecordingStarted() {
    sessionOrchestrator.onRecordingStarted();
    SpeakingUiState previous = speakingUiState.getValue();
    applyStateAndPublish(
        () -> {
          speakingUiState.setValue(
              new SpeakingUiState(
                  ++speakingEmissionId,
                  previous == null ? 0L : previous.getRequestId(),
                  true,
                  false,
                  0f,
                  previous == null ? null : previous.getLastRecordedAudio(),
                  null,
                  previous == null ? null : previous.getOriginalSentence(),
                  previous == null ? null : previous.getRecognizedText(),
                  null));
          bottomSheetMode.setValue(BottomSheetMode.WHILE_SPEAKING);
        });
  }

  public void onAudioChunk(@NonNull byte[] audioChunk) {
    sessionOrchestrator.onAudioChunk(audioChunk);
    SpeakingUiState previous = speakingUiState.getValue();
    if (previous == null) {
      return;
    }
    applyStateAndPublish(
        () ->
            speakingUiState.setValue(
                new SpeakingUiState(
                    ++speakingEmissionId,
                    previous.getRequestId(),
                    previous.isRecording(),
                    previous.isAnalyzing(),
                    SpeakingFlowController.calculateAmplitude(audioChunk),
                    previous.getLastRecordedAudio(),
                    previous.getFluencyResult(),
                    previous.getOriginalSentence(),
                    previous.getRecognizedText(),
                    previous.getError())));
  }

  public long analyzeSpeaking(
      @NonNull String originalSentence,
      @NonNull byte[] audioData,
      @Nullable String fallbackRecognizedText) {
    long requestId =
        sessionOrchestrator.analyzeSpeaking(
            originalSentence,
            audioData,
            fallbackRecognizedText,
            new SpeakingFlowController.Callback() {
              @Override
              public void onSuccess(@NonNull SpeakingAnalysisResult result) {
                applyStateAndPublish(
                    () -> {
                      speakingUiState.setValue(
                          new SpeakingUiState(
                              ++speakingEmissionId,
                              result.getRequestId(),
                              false,
                              false,
                              0f,
                              result.getRecordedAudio(),
                              result.getFluencyResult(),
                              result.getOriginalSentence(),
                              result.getRecognizedText(),
                              null));
                      bottomSheetMode.setValue(BottomSheetMode.FEEDBACK);
                    });

                startSentenceFeedback(result.getOriginalSentence(), result.getRecognizedText());
              }

              @Override
              public void onError(long requestId, @NonNull String error) {
                applyStateAndPublish(
                    () ->
                        speakingUiState.setValue(
                            new SpeakingUiState(
                                ++speakingEmissionId,
                                requestId,
                                false,
                                false,
                                0f,
                                null,
                                null,
                                originalSentence,
                                fallbackRecognizedText,
                                error)));
              }
            });

    applyStateAndPublish(
        () ->
            speakingUiState.setValue(
                new SpeakingUiState(
                    ++speakingEmissionId,
                    requestId,
                    false,
                    true,
                    0f,
                    null,
                    null,
                    originalSentence,
                    fallbackRecognizedText,
                    null)));
    return requestId;
  }

  public void startSentenceFeedback(
      @NonNull String originalSentence, @NonNull String userTranscript) {
    FeedbackUiState previous = feedbackUiState.getValue();
    ExtraQuestionUiState extraState =
        previous == null ? ExtraQuestionUiState.idle() : previous.getExtraQuestionUiState();

    applyStateAndPublish(
        () -> {
          feedbackUiState.setValue(
              new FeedbackUiState(
                  ++feedbackEmissionId, true, null, null, null, false, null, false, extraState));
          bottomSheetMode.setValue(BottomSheetMode.FEEDBACK);
        });

    sessionOrchestrator.startSentenceFeedback(
        originalSentence,
        userTranscript,
        new FeedbackFlowController.Callback() {
          @Override
          public void onSkipped() {
            applyStateAndPublish(
                () ->
                    feedbackUiState.setValue(
                        new FeedbackUiState(
                            ++feedbackEmissionId,
                            false,
                            null,
                            null,
                            null,
                            false,
                            null,
                            true,
                            extraState)));
          }

          @Override
          public void onSectionReady(
              long requestId,
              @NonNull String sectionKey,
              @NonNull SentenceFeedback partialFeedback) {
            applyStateAndPublish(
                () ->
                    feedbackUiState.setValue(
                        new FeedbackUiState(
                            ++feedbackEmissionId,
                            true,
                            sectionKey,
                            partialFeedback,
                            null,
                            false,
                            null,
                            false,
                            extraState)));
          }

          @Override
          public void onComplete(long requestId, SentenceFeedback fullFeedback) {
            applyStateAndPublish(
                () ->
                    feedbackUiState.setValue(
                        new FeedbackUiState(
                            ++feedbackEmissionId,
                            false,
                            null,
                            null,
                            fullFeedback,
                            shouldShowNextButton(fullFeedback),
                            null,
                            true,
                            extraState)));
          }

          @Override
          public void onError(long requestId, @NonNull String error) {
            FeedbackUiState current = feedbackUiState.getValue();
            SentenceFeedback existingFull = current == null ? null : current.getFullFeedback();
            applyStateAndPublish(
                () ->
                    feedbackUiState.setValue(
                        new FeedbackUiState(
                            ++feedbackEmissionId,
                            false,
                            null,
                            null,
                            existingFull,
                            shouldShowNextButton(existingFull),
                            error,
                            true,
                            extraState)));
          }
        });
  }

  public void askExtraQuestion(
      @NonNull String originalSentence, @NonNull String userSentence, @NonNull String question) {
    extraResponseBuilder.setLength(0);

    updateExtraQuestionState(new ExtraQuestionUiState(++extraEmissionId, true, question, "", null));

    sessionOrchestrator.askExtraQuestion(
        originalSentence,
        userSentence,
        question,
        new ExtraQuestionFlowController.Callback() {
          @Override
          public void onChunk(@NonNull String chunk) {
            extraResponseBuilder.append(chunk);
            updateExtraQuestionState(
                new ExtraQuestionUiState(
                    ++extraEmissionId, true, question, extraResponseBuilder.toString(), null));
          }

          @Override
          public void onComplete() {
            updateExtraQuestionState(
                new ExtraQuestionUiState(
                    ++extraEmissionId, false, question, extraResponseBuilder.toString(), null));
          }

          @Override
          public void onError(@NonNull String error) {
            updateExtraQuestionState(
                new ExtraQuestionUiState(
                    ++extraEmissionId, false, question, extraResponseBuilder.toString(), error));
          }
        });
  }

  public void emitScrollChatToBottom() {
    emitUiEvent(new DialogueUiEvent.ScrollChatToBottom());
  }

  public void emitOpenSummary() {
    emitUiEvent(new DialogueUiEvent.OpenSummary());
  }

  public void invalidateSpeakingRequest() {
    sessionOrchestrator.invalidateSpeakingRequest();
  }

  public void clearRequests() {
    sessionOrchestrator.clearRequests();
  }

  @Nullable
  public AudioRecorder getAudioRecorder() {
    return audioRecorder;
  }

  private boolean shouldShowNextButton(@Nullable SentenceFeedback feedback) {
    if (feedback == null || feedback.getWritingScore() == null) {
      return false;
    }
    return feedback.getWritingScore().getScore() >= 70;
  }

  private void updateExtraQuestionState(@NonNull ExtraQuestionUiState extraQuestionUiState) {
    applyStateAndPublish(
        () -> {
          FeedbackUiState source = feedbackUiState.getValue();
          if (source == null) {
            source = FeedbackUiState.idle();
          }
          feedbackUiState.setValue(
              new FeedbackUiState(
                  ++feedbackEmissionId,
                  source.isLoading(),
                  source.getSectionKey(),
                  source.getPartialFeedback(),
                  source.getFullFeedback(),
                  source.isShowNextButton(),
                  source.getError(),
                  source.isCompleted(),
                  extraQuestionUiState));
        });
  }

  @Override
  protected void onCleared() {
    clearRequests();
    retainedChatMessages.clear();
    super.onCleared();
  }
}
