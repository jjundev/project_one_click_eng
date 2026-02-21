package com.jjundev.oneclickeng.game.minefield.model;

public final class MinefieldRoundResult {
  private final long timestampEpochMs;
  private final int totalQuestions;
  private final int totalScore;
  private final int averageGrammarScore;
  private final int averageNaturalnessScore;
  private final int averageWordUsageScore;

  public MinefieldRoundResult(
      long timestampEpochMs,
      int totalQuestions,
      int totalScore,
      int averageGrammarScore,
      int averageNaturalnessScore,
      int averageWordUsageScore) {
    this.timestampEpochMs = timestampEpochMs;
    this.totalQuestions = Math.max(0, totalQuestions);
    this.totalScore = Math.max(0, totalScore);
    this.averageGrammarScore = clampScore(averageGrammarScore);
    this.averageNaturalnessScore = clampScore(averageNaturalnessScore);
    this.averageWordUsageScore = clampScore(averageWordUsageScore);
  }

  public long getTimestampEpochMs() {
    return timestampEpochMs;
  }

  public int getTotalQuestions() {
    return totalQuestions;
  }

  public int getTotalScore() {
    return totalScore;
  }

  public int getAverageGrammarScore() {
    return averageGrammarScore;
  }

  public int getAverageNaturalnessScore() {
    return averageNaturalnessScore;
  }

  public int getAverageWordUsageScore() {
    return averageWordUsageScore;
  }

  private static int clampScore(int score) {
    if (score < 0) {
      return 0;
    }
    return Math.min(score, 100);
  }
}
