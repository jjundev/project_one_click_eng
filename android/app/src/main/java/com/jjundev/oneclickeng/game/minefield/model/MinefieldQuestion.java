package com.jjundev.oneclickeng.game.minefield.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MinefieldQuestion {
  @NonNull private final String situation;
  @NonNull private final String question;
  @NonNull private final List<String> words;
  @NonNull private final List<Integer> requiredWordIndices;
  @NonNull private final MinefieldDifficulty difficulty;

  public MinefieldQuestion(
      @Nullable String situation,
      @Nullable String question,
      @Nullable List<String> words,
      @Nullable List<Integer> requiredWordIndices,
      @Nullable MinefieldDifficulty difficulty) {
    this.situation = normalizeOrEmpty(situation);
    this.question = normalizeOrEmpty(question);
    this.words = toImmutableWords(words);
    this.requiredWordIndices = toImmutableRequiredIndices(requiredWordIndices, this.words.size());
    this.difficulty = difficulty == null ? MinefieldDifficulty.EASY : difficulty;
  }

  @NonNull
  public String getSituation() {
    return situation;
  }

  @NonNull
  public String getQuestion() {
    return question;
  }

  @NonNull
  public List<String> getWords() {
    return words;
  }

  @NonNull
  public List<Integer> getRequiredWordIndices() {
    return requiredWordIndices;
  }

  @NonNull
  public MinefieldDifficulty getDifficulty() {
    return difficulty;
  }

  @NonNull
  public String signature() {
    StringBuilder builder = new StringBuilder();
    builder.append(situation.toLowerCase(Locale.US));
    builder.append('|').append(question.toLowerCase(Locale.US));
    for (String word : words) {
      builder.append('|').append(word.toLowerCase(Locale.US));
    }
    for (Integer index : requiredWordIndices) {
      builder.append('|').append(index == null ? -1 : index);
    }
    return builder.toString();
  }

  public boolean isValidForV1() {
    if (situation.isEmpty() || question.isEmpty()) {
      return false;
    }
    if (words.size() < 6 || words.size() > 8) {
      return false;
    }
    if (requiredWordIndices.isEmpty()) {
      return false;
    }
    if (requiredWordIndices.size() < difficulty.requiredMineWordCount()) {
      return false;
    }
    for (Integer index : requiredWordIndices) {
      if (index == null || index < 0 || index >= words.size()) {
        return false;
      }
    }
    return true;
  }

  @NonNull
  private static String normalizeOrEmpty(@Nullable String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? "" : trimmed;
  }

  @NonNull
  private static List<String> toImmutableWords(@Nullable List<String> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    Set<String> dedupe = new HashSet<>();
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
  private static List<Integer> toImmutableRequiredIndices(
      @Nullable List<Integer> source, int maxExclusive) {
    if (source == null || source.isEmpty() || maxExclusive <= 0) {
      return Collections.emptyList();
    }
    List<Integer> result = new ArrayList<>();
    Set<Integer> dedupe = new HashSet<>();
    for (Integer index : source) {
      if (index == null || index < 0 || index >= maxExclusive || !dedupe.add(index)) {
        continue;
      }
      result.add(index);
    }
    return Collections.unmodifiableList(result);
  }
}
