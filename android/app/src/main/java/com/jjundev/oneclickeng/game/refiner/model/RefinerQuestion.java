package com.jjundev.oneclickeng.game.refiner.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;

public final class RefinerQuestion {
  @NonNull private final String sourceSentence;
  @NonNull private final String styleContext;
  @NonNull private final RefinerConstraints constraints;
  @NonNull private final String hint;
  @NonNull private final RefinerDifficulty difficulty;

  public RefinerQuestion(
      @Nullable String sourceSentence,
      @Nullable String styleContext,
      @Nullable RefinerConstraints constraints,
      @Nullable String hint,
      @Nullable RefinerDifficulty difficulty) {
    this.sourceSentence = normalizeOrEmpty(sourceSentence);
    this.styleContext = normalizeOrEmpty(styleContext);
    this.constraints =
        constraints == null
            ? new RefinerConstraints(null, null, null)
            : constraints;
    this.hint = normalizeOrEmpty(hint);
    this.difficulty = difficulty == null ? RefinerDifficulty.EASY : difficulty;
  }

  @NonNull
  public String getSourceSentence() {
    return sourceSentence;
  }

  @NonNull
  public String getStyleContext() {
    return styleContext;
  }

  @NonNull
  public RefinerConstraints getConstraints() {
    return constraints;
  }

  @NonNull
  public String getHint() {
    return hint;
  }

  @NonNull
  public RefinerDifficulty getDifficulty() {
    return difficulty;
  }

  @NonNull
  public String signature() {
    return sourceSentence.toLowerCase(Locale.US)
        + '|'
        + styleContext.toLowerCase(Locale.US)
        + '|'
        + constraints.signature();
  }

  public boolean isValidForV1() {
    if (sourceSentence.isEmpty() || styleContext.isEmpty() || hint.isEmpty()) {
      return false;
    }
    int activeConstraints = constraints.getActiveConstraintCount();
    return activeConstraints >= 1 && activeConstraints <= 2;
  }

  @NonNull
  private static String normalizeOrEmpty(@Nullable String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? "" : trimmed;
  }
}
