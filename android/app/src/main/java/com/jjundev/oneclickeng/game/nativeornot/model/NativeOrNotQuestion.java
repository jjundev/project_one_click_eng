package com.jjundev.oneclickeng.game.nativeornot.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class NativeOrNotQuestion {

  @NonNull private final String situation;
  @NonNull private final List<String> options;
  private final int correctIndex;
  private final int awkwardOptionIndex;
  @NonNull private final List<String> reasonChoices;
  private final int reasonAnswerIndex;
  @NonNull private final String explanation;
  @NonNull private final String learningPoint;
  @NonNull private final NativeOrNotTag tag;
  @NonNull private final String hint;
  @NonNull private final NativeOrNotDifficulty difficulty;

  public NativeOrNotQuestion(
      @Nullable String situation,
      @Nullable List<String> options,
      int correctIndex,
      int awkwardOptionIndex,
      @Nullable List<String> reasonChoices,
      int reasonAnswerIndex,
      @Nullable String explanation,
      @Nullable String learningPoint,
      @Nullable NativeOrNotTag tag,
      @Nullable String hint,
      @Nullable NativeOrNotDifficulty difficulty) {
    this.situation = normalizeOrEmpty(situation);
    this.options = toImmutableList(options);
    this.correctIndex = correctIndex;
    this.awkwardOptionIndex = awkwardOptionIndex;
    this.reasonChoices = toImmutableList(reasonChoices);
    this.reasonAnswerIndex = reasonAnswerIndex;
    this.explanation = normalizeOrEmpty(explanation);
    this.learningPoint = normalizeOrEmpty(learningPoint);
    this.tag = tag == null ? NativeOrNotTag.REGISTER : tag;
    this.hint = normalizeOrEmpty(hint);
    this.difficulty = difficulty == null ? NativeOrNotDifficulty.EASY : difficulty;
  }

  @NonNull
  public String getSituation() {
    return situation;
  }

  @NonNull
  public List<String> getOptions() {
    return options;
  }

  public int getCorrectIndex() {
    return correctIndex;
  }

  public int getAwkwardOptionIndex() {
    return awkwardOptionIndex;
  }

  @NonNull
  public List<String> getReasonChoices() {
    return reasonChoices;
  }

  public int getReasonAnswerIndex() {
    return reasonAnswerIndex;
  }

  @NonNull
  public String getExplanation() {
    return explanation;
  }

  @NonNull
  public String getLearningPoint() {
    return learningPoint;
  }

  @NonNull
  public NativeOrNotTag getTag() {
    return tag;
  }

  @NonNull
  public String getHint() {
    return hint;
  }

  @NonNull
  public NativeOrNotDifficulty getDifficulty() {
    return difficulty;
  }

  @NonNull
  public String getAwkwardSentence() {
    if (awkwardOptionIndex < 0 || awkwardOptionIndex >= options.size()) {
      return "";
    }
    return options.get(awkwardOptionIndex);
  }

  @NonNull
  public String signature() {
    StringBuilder builder = new StringBuilder();
    builder.append(situation.trim().toLowerCase(Locale.US));
    for (String option : options) {
      builder.append('|').append(option.trim().toLowerCase(Locale.US));
    }
    return builder.toString();
  }

  public boolean isValidForV1() {
    if (situation.isEmpty()) {
      return false;
    }
    if (options.size() != 3) {
      return false;
    }
    if (correctIndex < 0 || correctIndex >= options.size()) {
      return false;
    }
    if (awkwardOptionIndex < 0 || awkwardOptionIndex >= options.size()) {
      return false;
    }
    if (correctIndex == awkwardOptionIndex) {
      return false;
    }
    if (reasonChoices.size() != 4) {
      return false;
    }
    if (reasonAnswerIndex < 0 || reasonAnswerIndex >= reasonChoices.size()) {
      return false;
    }
    if (explanation.isEmpty() || learningPoint.isEmpty() || hint.isEmpty()) {
      return false;
    }
    return true;
  }

  @NonNull
  private static String normalizeOrEmpty(@Nullable String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? "" : trimmed;
  }

  @NonNull
  private static List<String> toImmutableList(@Nullable List<String> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    for (String item : source) {
      String normalized = normalizeOrEmpty(item);
      if (!normalized.isEmpty()) {
        result.add(normalized);
      }
    }
    return Collections.unmodifiableList(result);
  }
}
