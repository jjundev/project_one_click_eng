package com.jjundev.oneclickeng.game.nativeornot.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public enum NativeOrNotTag {
  COLLOCATION,
  SPOKEN,
  LITERAL_TRANSLATION,
  REGISTER,
  REGIONAL_VARIANT,
  TENSE_SENSE;

  @NonNull
  public static NativeOrNotTag fromRaw(@Nullable String raw) {
    if (raw == null) {
      return REGISTER;
    }
    String normalized = raw.trim().toUpperCase();
    for (NativeOrNotTag value : values()) {
      if (value.name().equals(normalized)) {
        return value;
      }
    }
    return REGISTER;
  }

  @NonNull
  public String toDisplayLabel() {
    switch (this) {
      case COLLOCATION:
        return "콜로케이션";
      case SPOKEN:
        return "구어체";
      case LITERAL_TRANSLATION:
        return "과도한 직역";
      case REGISTER:
        return "레지스터";
      case REGIONAL_VARIANT:
        return "지역 변이";
      case TENSE_SENSE:
      default:
        return "시제 감각";
    }
  }
}
