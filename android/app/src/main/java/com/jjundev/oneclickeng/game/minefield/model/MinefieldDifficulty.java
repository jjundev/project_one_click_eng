package com.jjundev.oneclickeng.game.minefield.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;

public enum MinefieldDifficulty {
  EASY,
  NORMAL,
  HARD,
  EXPERT;

  @NonNull
  public static MinefieldDifficulty fromRaw(
      @Nullable String raw, @NonNull MinefieldDifficulty fallback) {
    if (raw == null) {
      return fallback;
    }
    String normalized = raw.trim().toUpperCase(Locale.US);
    for (MinefieldDifficulty value : values()) {
      if (value.name().equals(normalized)) {
        return value;
      }
    }
    return fallback;
  }

  public int requiredMineWordCount() {
    switch (this) {
      case HARD:
      case EXPERT:
        return 2;
      case EASY:
      case NORMAL:
      default:
        return 1;
    }
  }
}
