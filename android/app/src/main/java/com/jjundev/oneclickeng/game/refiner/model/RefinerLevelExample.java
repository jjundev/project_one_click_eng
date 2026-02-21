package com.jjundev.oneclickeng.game.refiner.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class RefinerLevelExample {
  @NonNull private final RefinerLevel level;
  @NonNull private final String sentence;
  @NonNull private final String comment;

  public RefinerLevelExample(
      @Nullable RefinerLevel level, @Nullable String sentence, @Nullable String comment) {
    this.level = level == null ? RefinerLevel.A2 : level;
    this.sentence = normalizeOrEmpty(sentence);
    this.comment = normalizeOrEmpty(comment);
  }

  @NonNull
  public RefinerLevel getLevel() {
    return level;
  }

  @NonNull
  public String getSentence() {
    return sentence;
  }

  @NonNull
  public String getComment() {
    return comment;
  }

  public boolean isValidForV1() {
    return !sentence.isEmpty() && !comment.isEmpty();
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
