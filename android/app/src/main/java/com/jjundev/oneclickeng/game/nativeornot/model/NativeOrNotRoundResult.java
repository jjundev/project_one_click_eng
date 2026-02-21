package com.jjundev.oneclickeng.game.nativeornot.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class NativeOrNotRoundResult {
  private final long timestampEpochMs;
  private final int totalQuestions;
  private final int correctAnswers;
  private final int highestStreak;
  private final int totalScore;
  @Nullable private final NativeOrNotTag weakTag;

  public NativeOrNotRoundResult(
      long timestampEpochMs,
      int totalQuestions,
      int correctAnswers,
      int highestStreak,
      int totalScore,
      @Nullable NativeOrNotTag weakTag) {
    this.timestampEpochMs = timestampEpochMs;
    this.totalQuestions = Math.max(0, totalQuestions);
    this.correctAnswers = Math.max(0, correctAnswers);
    this.highestStreak = Math.max(0, highestStreak);
    this.totalScore = totalScore;
    this.weakTag = weakTag;
  }

  public long getTimestampEpochMs() {
    return timestampEpochMs;
  }

  public int getTotalQuestions() {
    return totalQuestions;
  }

  public int getCorrectAnswers() {
    return correctAnswers;
  }

  public int getHighestStreak() {
    return highestStreak;
  }

  public int getTotalScore() {
    return totalScore;
  }

  @Nullable
  public NativeOrNotTag getWeakTag() {
    return weakTag;
  }

  public int getAccuracyPercent() {
    if (totalQuestions == 0) {
      return 0;
    }
    return Math.round((correctAnswers * 100f) / totalQuestions);
  }

  @NonNull
  public String getWeakTagDisplay() {
    return weakTag == null ? "없음" : weakTag.toDisplayLabel();
  }
}
