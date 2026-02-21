package com.jjundev.oneclickeng.game.minefield.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MinefieldEvaluation {
  private final int grammarScore;
  private final int naturalnessScore;
  private final int wordUsageScore;
  private final int usedWordCount;
  private final int totalWordCount;
  @NonNull private final List<String> usedWords;
  @NonNull private final List<String> unusedWords;
  @NonNull private final List<String> missingRequiredWords;
  private final boolean advancedTransformUsed;
  @NonNull private final String strengthsComment;
  @NonNull private final String improvementComment;
  @NonNull private final String improvedSentence;
  @NonNull private final String exampleBasic;
  @NonNull private final String exampleIntermediate;
  @NonNull private final String exampleAdvanced;

  public MinefieldEvaluation(
      int grammarScore,
      int naturalnessScore,
      int wordUsageScore,
      int usedWordCount,
      int totalWordCount,
      @Nullable List<String> usedWords,
      @Nullable List<String> unusedWords,
      @Nullable List<String> missingRequiredWords,
      boolean advancedTransformUsed,
      @Nullable String strengthsComment,
      @Nullable String improvementComment,
      @Nullable String improvedSentence,
      @Nullable String exampleBasic,
      @Nullable String exampleIntermediate,
      @Nullable String exampleAdvanced) {
    this.grammarScore = clampScore(grammarScore);
    this.naturalnessScore = clampScore(naturalnessScore);
    this.wordUsageScore = clampScore(wordUsageScore);
    this.usedWordCount = Math.max(0, usedWordCount);
    this.totalWordCount = Math.max(0, totalWordCount);
    this.usedWords = toImmutableList(usedWords);
    this.unusedWords = toImmutableList(unusedWords);
    this.missingRequiredWords = toImmutableList(missingRequiredWords);
    this.advancedTransformUsed = advancedTransformUsed;
    this.strengthsComment = normalizeOrEmpty(strengthsComment);
    this.improvementComment = normalizeOrEmpty(improvementComment);
    this.improvedSentence = normalizeOrEmpty(improvedSentence);
    this.exampleBasic = normalizeOrEmpty(exampleBasic);
    this.exampleIntermediate = normalizeOrEmpty(exampleIntermediate);
    this.exampleAdvanced = normalizeOrEmpty(exampleAdvanced);
  }

  public int getGrammarScore() {
    return grammarScore;
  }

  public int getNaturalnessScore() {
    return naturalnessScore;
  }

  public int getWordUsageScore() {
    return wordUsageScore;
  }

  public int getUsedWordCount() {
    return usedWordCount;
  }

  public int getTotalWordCount() {
    return totalWordCount;
  }

  @NonNull
  public List<String> getUsedWords() {
    return usedWords;
  }

  @NonNull
  public List<String> getUnusedWords() {
    return unusedWords;
  }

  @NonNull
  public List<String> getMissingRequiredWords() {
    return missingRequiredWords;
  }

  public boolean isAdvancedTransformUsed() {
    return advancedTransformUsed;
  }

  @NonNull
  public String getStrengthsComment() {
    return strengthsComment;
  }

  @NonNull
  public String getImprovementComment() {
    return improvementComment;
  }

  @NonNull
  public String getImprovedSentence() {
    return improvedSentence;
  }

  @NonNull
  public String getExampleBasic() {
    return exampleBasic;
  }

  @NonNull
  public String getExampleIntermediate() {
    return exampleIntermediate;
  }

  @NonNull
  public String getExampleAdvanced() {
    return exampleAdvanced;
  }

  public boolean isValidForV1() {
    if (totalWordCount <= 0) {
      return false;
    }
    if (usedWordCount < 0 || usedWordCount > totalWordCount) {
      return false;
    }
    return !strengthsComment.isEmpty()
        && !improvementComment.isEmpty()
        && !improvedSentence.isEmpty()
        && !exampleBasic.isEmpty()
        && !exampleIntermediate.isEmpty()
        && !exampleAdvanced.isEmpty();
  }

  private static int clampScore(int score) {
    if (score < 0) {
      return 0;
    }
    return Math.min(score, 100);
  }

  @NonNull
  private static List<String> toImmutableList(@Nullable List<String> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    for (String raw : source) {
      String normalized = normalizeOrEmpty(raw);
      if (!normalized.isEmpty()) {
        result.add(normalized);
      }
    }
    return Collections.unmodifiableList(result);
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
