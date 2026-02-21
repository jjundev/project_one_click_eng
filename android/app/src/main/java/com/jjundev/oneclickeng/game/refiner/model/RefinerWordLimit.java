package com.jjundev.oneclickeng.game.refiner.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class RefinerWordLimit {
  @NonNull private final RefinerWordLimitMode mode;
  private final int value;

  public RefinerWordLimit(@Nullable RefinerWordLimitMode mode, int value) {
    this.mode = mode == null ? RefinerWordLimitMode.MAX : mode;
    this.value = Math.max(1, value);
  }

  @NonNull
  public RefinerWordLimitMode getMode() {
    return mode;
  }

  public int getValue() {
    return value;
  }
}
