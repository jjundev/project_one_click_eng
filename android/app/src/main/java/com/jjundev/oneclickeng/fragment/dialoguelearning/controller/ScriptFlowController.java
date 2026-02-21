package com.jjundev.oneclickeng.fragment.dialoguelearning.controller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.fragment.dialoguelearning.model.DialogueScript;
import com.jjundev.oneclickeng.fragment.dialoguelearning.model.ScriptTurn;
import com.jjundev.oneclickeng.fragment.dialoguelearning.parser.DialogueScriptParser;

public class ScriptFlowController {

  public static class NextTurnResult {
    public enum Type {
      EMPTY,
      TURN,
      FINISHED
    }

    @NonNull private final Type type;
    @Nullable private final ScriptTurn turn;
    private final int currentStep;
    private final int totalSteps;

    public NextTurnResult(
        @NonNull Type type, @Nullable ScriptTurn turn, int currentStep, int totalSteps) {
      this.type = type;
      this.turn = turn;
      this.currentStep = currentStep;
      this.totalSteps = totalSteps;
    }

    @NonNull
    public Type getType() {
      return type;
    }

    @Nullable
    public ScriptTurn getTurn() {
      return turn;
    }

    public int getCurrentStep() {
      return currentStep;
    }

    public int getTotalSteps() {
      return totalSteps;
    }
  }

  private final DialogueScriptParser parser;
  @Nullable private DialogueScript script;
  private int currentIndex = -1;

  public ScriptFlowController(@NonNull DialogueScriptParser parser) {
    this.parser = parser;
  }

  public void loadScript(@NonNull String scriptJson) throws Exception {
    this.script = parser.parse(scriptJson);
    this.currentIndex = -1;
  }

  @Nullable
  public DialogueScript getScript() {
    return script;
  }

  public int getCurrentIndex() {
    return currentIndex;
  }

  public int getTotalSteps() {
    return script == null ? 0 : script.size();
  }

  @NonNull
  public NextTurnResult moveToNextTurn() {
    if (script == null || script.size() == 0) {
      return new NextTurnResult(NextTurnResult.Type.EMPTY, null, 0, 0);
    }

    currentIndex += 1;
    if (currentIndex >= script.size()) {
      return new NextTurnResult(NextTurnResult.Type.FINISHED, null, script.size(), script.size());
    }

    ScriptTurn turn = script.getTurns().get(currentIndex);
    return new NextTurnResult(NextTurnResult.Type.TURN, turn, currentIndex + 1, script.size());
  }
}
