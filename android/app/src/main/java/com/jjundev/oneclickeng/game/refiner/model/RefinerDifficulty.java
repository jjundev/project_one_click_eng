package com.jjundev.oneclickeng.game.refiner.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;

public enum RefinerDifficulty {
  EASY,
  NORMAL,
  HARD,
  EXPERT;

  @NonNull
  public static RefinerDifficulty fromRaw(
      @Nullable String raw, @NonNull RefinerDifficulty fallback) {
    if (raw == null) {
      return fallback;
    }
    String normalized = raw.trim().toUpperCase(Locale.US);
    for (RefinerDifficulty value : values()) {
      if (value.name().equals(normalized)) {
        return value;
      }
    }
    return fallback;
  }
}
