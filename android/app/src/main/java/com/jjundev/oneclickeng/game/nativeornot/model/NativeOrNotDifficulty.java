package com.jjundev.oneclickeng.game.nativeornot.model;

import androidx.annotation.NonNull;

public enum NativeOrNotDifficulty {
  EASY,
  NORMAL,
  HARD,
  EXPERT;

  @NonNull
  public static NativeOrNotDifficulty forStreakV1(int streak) {
    return streak >= 3 ? NORMAL : EASY;
  }
}
