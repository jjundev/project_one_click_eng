package com.jjundev.oneclickeng.learning.dialoguelearning.model;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DialogueScript {
  @NonNull private final String topic;
  @NonNull private final String opponentName;
  @NonNull private final String opponentRole;
  @NonNull private final String opponentGender;
  @NonNull private final List<ScriptTurn> turns;

  public DialogueScript(
      @NonNull String topic,
      @NonNull String opponentName,
      @NonNull String opponentRole,
      @NonNull String opponentGender,
      @NonNull List<ScriptTurn> turns) {
    this.topic = topic;
    this.opponentName = opponentName;
    this.opponentRole = opponentRole;
    this.opponentGender = opponentGender;
    this.turns = new ArrayList<>(turns);
  }

  @NonNull
  public String getTopic() {
    return topic;
  }

  @NonNull
  public String getOpponentName() {
    return opponentName;
  }

  @NonNull
  public String getOpponentRole() {
    return opponentRole;
  }

  @NonNull
  public String getOpponentGender() {
    return opponentGender;
  }

  @NonNull
  public List<ScriptTurn> getTurns() {
    return Collections.unmodifiableList(turns);
  }

  public int size() {
    return turns.size();
  }
}
