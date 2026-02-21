package com.jjundev.oneclickeng.game.refiner.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class RefinerEvaluation {
  @NonNull private final RefinerLevel level;
  private final int lexicalScore;
  private final int syntaxScore;
  private final int naturalnessScore;
  private final int complianceScore;
  private final boolean creativeRequiredWordUse;
  @NonNull private final String insight;
  @NonNull private final List<RefinerLevelExample> levelExamples;

  public RefinerEvaluation(
      @Nullable RefinerLevel level,
      int lexicalScore,
      int syntaxScore,
      int naturalnessScore,
      int complianceScore,
      boolean creativeRequiredWordUse,
      @Nullable String insight,
      @Nullable List<RefinerLevelExample> levelExamples) {
    this.level = level == null ? RefinerLevel.A1 : level;
    this.lexicalScore = clampScore(lexicalScore);
    this.syntaxScore = clampScore(syntaxScore);
    this.naturalnessScore = clampScore(naturalnessScore);
    this.complianceScore = clampScore(complianceScore);
    this.creativeRequiredWordUse = creativeRequiredWordUse;
    this.insight = normalizeOrEmpty(insight);
    this.levelExamples = toImmutableList(levelExamples);
  }

  @NonNull
  public RefinerLevel getLevel() {
    return level;
  }

  public int getLexicalScore() {
    return lexicalScore;
  }

  public int getSyntaxScore() {
    return syntaxScore;
  }

  public int getNaturalnessScore() {
    return naturalnessScore;
  }

  public int getComplianceScore() {
    return complianceScore;
  }

  public boolean isCreativeRequiredWordUse() {
    return creativeRequiredWordUse;
  }

  @NonNull
  public String getInsight() {
    return insight;
  }

  @NonNull
  public List<RefinerLevelExample> getLevelExamples() {
    return levelExamples;
  }

  public boolean isValidForV1() {
    if (insight.isEmpty()) {
      return false;
    }
    Set<RefinerLevel> expected =
        EnumSet.of(
            RefinerLevel.A2,
            RefinerLevel.B1,
            RefinerLevel.B2,
            RefinerLevel.C1,
            RefinerLevel.C2);
    for (RefinerLevelExample example : levelExamples) {
      if (example == null || !example.isValidForV1()) {
        return false;
      }
      expected.remove(example.getLevel());
    }
    return expected.isEmpty();
  }

  private static int clampScore(int score) {
    if (score < 0) {
      return 0;
    }
    return Math.min(score, 100);
  }

  @NonNull
  private static List<RefinerLevelExample> toImmutableList(
      @Nullable List<RefinerLevelExample> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    List<RefinerLevelExample> result = new ArrayList<>();
    for (RefinerLevelExample item : source) {
      if (item != null) {
        result.add(item);
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
