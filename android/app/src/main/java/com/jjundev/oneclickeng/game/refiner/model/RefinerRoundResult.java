package com.jjundev.oneclickeng.game.refiner.model;

public final class RefinerRoundResult {
  private final long timestampEpochMs;
  private final int totalQuestions;
  private final int totalScore;
  private final int averageLexicalScore;
  private final int averageSyntaxScore;
  private final int averageNaturalnessScore;
  private final int averageComplianceScore;

  public RefinerRoundResult(
      long timestampEpochMs,
      int totalQuestions,
      int totalScore,
      int averageLexicalScore,
      int averageSyntaxScore,
      int averageNaturalnessScore,
      int averageComplianceScore) {
    this.timestampEpochMs = timestampEpochMs;
    this.totalQuestions = Math.max(1, totalQuestions);
    this.totalScore = Math.max(0, totalScore);
    this.averageLexicalScore = clampScore(averageLexicalScore);
    this.averageSyntaxScore = clampScore(averageSyntaxScore);
    this.averageNaturalnessScore = clampScore(averageNaturalnessScore);
    this.averageComplianceScore = clampScore(averageComplianceScore);
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

  public int getAverageLexicalScore() {
    return averageLexicalScore;
  }

  public int getAverageSyntaxScore() {
    return averageSyntaxScore;
  }

  public int getAverageNaturalnessScore() {
    return averageNaturalnessScore;
  }

  public int getAverageComplianceScore() {
    return averageComplianceScore;
  }

  private static int clampScore(int score) {
    if (score < 0) {
      return 0;
    }
    return Math.min(score, 100);
  }
}
