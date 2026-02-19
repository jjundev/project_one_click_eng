package com.example.test.fragment.dialoguelearning.orchestrator;

import androidx.annotation.NonNull;
import com.example.test.fragment.dialoguelearning.state.BottomSheetMode;
import com.example.test.fragment.dialoguelearning.state.ExtraQuestionUiState;
import com.example.test.fragment.dialoguelearning.state.FeedbackUiState;
import com.example.test.fragment.dialoguelearning.state.ScriptUiState;
import com.example.test.fragment.dialoguelearning.state.SpeakingUiState;

public class LearningSessionSnapshot {

  @NonNull private final ScriptUiState scriptUiState;
  @NonNull private final SpeakingUiState speakingUiState;
  @NonNull private final FeedbackUiState feedbackUiState;
  @NonNull private final BottomSheetMode bottomSheetMode;

  public LearningSessionSnapshot(
      @NonNull ScriptUiState scriptUiState,
      @NonNull SpeakingUiState speakingUiState,
      @NonNull FeedbackUiState feedbackUiState,
      @NonNull BottomSheetMode bottomSheetMode) {
    this.scriptUiState = scriptUiState;
    this.speakingUiState = speakingUiState;
    this.feedbackUiState = feedbackUiState;
    this.bottomSheetMode = bottomSheetMode;
  }

  @NonNull
  public ScriptUiState getScriptUiState() {
    return scriptUiState;
  }

  @NonNull
  public SpeakingUiState getSpeakingUiState() {
    return speakingUiState;
  }

  @NonNull
  public FeedbackUiState getFeedbackUiState() {
    return feedbackUiState;
  }

  @NonNull
  public BottomSheetMode getBottomSheetMode() {
    return bottomSheetMode;
  }

  @NonNull
  public ExtraQuestionUiState getExtraQuestionUiState() {
    return feedbackUiState.getExtraQuestionUiState();
  }

  public boolean isFinished() {
    return scriptUiState.isFinished();
  }
}
