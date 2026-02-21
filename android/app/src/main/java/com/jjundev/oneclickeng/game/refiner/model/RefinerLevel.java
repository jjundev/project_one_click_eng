package com.jjundev.oneclickeng.game.refiner.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;

public enum RefinerLevel {
  A1,
  A2,
  B1,
  B2,
  C1,
  C2;

  @NonNull
  public static RefinerLevel fromRaw(@Nullable String raw, @NonNull RefinerLevel fallback) {
    if (raw == null) {
      return fallback;
    }
    String normalized = raw.trim().toUpperCase(Locale.US);
    for (RefinerLevel value : values()) {
      if (value.name().equals(normalized)) {
        return value;
      }
    }
    return fallback;
  }

  public int baseScore() {
    switch (this) {
      case A1:
        return 100;
      case A2:
        return 150;
      case B1:
        return 200;
      case B2:
        return 270;
      case C1:
        return 350;
      case C2:
      default:
        return 450;
    }
  }
}
