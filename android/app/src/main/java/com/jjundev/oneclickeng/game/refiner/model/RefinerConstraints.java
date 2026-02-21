package com.jjundev.oneclickeng.game.refiner.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RefinerConstraints {
  @NonNull private final List<String> bannedWords;
  @Nullable private final RefinerWordLimit wordLimit;
  @NonNull private final String requiredWord;

  public RefinerConstraints(
      @Nullable List<String> bannedWords,
      @Nullable RefinerWordLimit wordLimit,
      @Nullable String requiredWord) {
    this.bannedWords = toImmutableWords(bannedWords);
    this.wordLimit = wordLimit;
    this.requiredWord = normalizeOrEmpty(requiredWord);
  }

  @NonNull
  public List<String> getBannedWords() {
    return bannedWords;
  }

  @Nullable
  public RefinerWordLimit getWordLimit() {
    return wordLimit;
  }

  @NonNull
  public String getRequiredWord() {
    return requiredWord;
  }

  public int getActiveConstraintCount() {
    int count = 0;
    if (!bannedWords.isEmpty()) {
      count++;
    }
    if (wordLimit != null) {
      count++;
    }
    if (!requiredWord.isEmpty()) {
      count++;
    }
    return count;
  }

  public boolean hasRequiredWord() {
    return !requiredWord.isEmpty();
  }

  @NonNull
  public String signature() {
    StringBuilder builder = new StringBuilder();
    if (!bannedWords.isEmpty()) {
      builder.append("B:");
      for (String word : bannedWords) {
        builder.append(word.toLowerCase(Locale.US)).append(',');
      }
    }
    if (wordLimit != null) {
      builder
          .append("|W:")
          .append(wordLimit.getMode().name())
          .append(':')
          .append(wordLimit.getValue());
    }
    if (!requiredWord.isEmpty()) {
      builder.append("|R:").append(requiredWord.toLowerCase(Locale.US));
    }
    return builder.toString();
  }

  @NonNull
  private static List<String> toImmutableWords(@Nullable List<String> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    Set<String> dedupe = new LinkedHashSet<>();
    for (String raw : source) {
      String normalized = normalizeOrEmpty(raw);
      if (normalized.isEmpty()) {
        continue;
      }
      String key = normalized.toLowerCase(Locale.US);
      if (!dedupe.add(key)) {
        continue;
      }
      result.add(normalized);
    }
    return Collections.unmodifiableList(result);
  }

  @NonNull
  private static String normalizeOrEmpty(@Nullable String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? "" : trimmed;
  }
}
