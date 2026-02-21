package com.jjundev.oneclickeng.game.refiner.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;

public enum RefinerWordLimitMode {
  MAX,
  EXACT;

  @NonNull
  public static RefinerWordLimitMode fromRaw(
      @Nullable String raw, @NonNull RefinerWordLimitMode fallback) {
    if (raw == null) {
      return fallback;
    }
    String normalized = raw.trim().toUpperCase(Locale.US);
    for (RefinerWordLimitMode value : values()) {
      if (value.name().equals(normalized)) {
        return value;
      }
    }
    return fallback;
  }
}
