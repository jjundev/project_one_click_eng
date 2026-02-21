package com.jjundev.oneclickeng.game.minefield;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MinefieldWordUsageMatcher {
  private MinefieldWordUsageMatcher() {}

  @NonNull
  public static Set<Integer> findUsedWordIndices(
      @NonNull List<String> sourceWords, @Nullable String sentence) {
    Set<Integer> used = new LinkedHashSet<>();
    String normalizedSentence = normalizeForSentence(sentence);
    if (normalizedSentence.isEmpty() || sourceWords.isEmpty()) {
      return used;
    }

    Set<String> sentenceStems = tokenizeAndStem(normalizedSentence);
    for (int i = 0; i < sourceWords.size(); i++) {
      String sourceWord = sourceWords.get(i);
      if (isWordUsed(sourceWord, normalizedSentence, sentenceStems)) {
        used.add(i);
      }
    }
    return used;
  }

  private static boolean isWordUsed(
      @Nullable String sourceWord,
      @NonNull String normalizedSentence,
      @NonNull Set<String> sentenceStems) {
    String normalizedWord = normalizeForSentence(sourceWord);
    if (normalizedWord.isEmpty()) {
      return false;
    }

    if (normalizedWord.contains(" ")) {
      if (normalizedSentence.contains(normalizedWord)) {
        return true;
      }
      String[] parts = normalizedWord.split("\\s+");
      for (String part : parts) {
        if (!sentenceStems.contains(stemToken(part))) {
          return false;
        }
      }
      return true;
    }

    return sentenceStems.contains(stemToken(normalizedWord));
  }

  @NonNull
  private static String normalizeForSentence(@Nullable String raw) {
    if (raw == null) {
      return "";
    }
    String lower = raw.toLowerCase(Locale.US);
    String replaced = lower.replaceAll("[^a-z\\s']", " ");
    return replaced.replaceAll("\\s+", " ").trim();
  }

  @NonNull
  private static Set<String> tokenizeAndStem(@NonNull String normalizedText) {
    Set<String> stems = new LinkedHashSet<>();
    if (normalizedText.isEmpty()) {
      return stems;
    }
    String[] tokens = normalizedText.split("\\s+");
    for (String token : tokens) {
      String stem = stemToken(token);
      if (!stem.isEmpty()) {
        stems.add(stem);
      }
    }
    return stems;
  }

  @NonNull
  private static String stemToken(@Nullable String tokenRaw) {
    if (tokenRaw == null) {
      return "";
    }
    String token = tokenRaw.trim();
    if (token.isEmpty()) {
      return "";
    }
    List<String> candidates = new ArrayList<>();
    candidates.add(token);

    if (token.endsWith("'s") && token.length() > 2) {
      candidates.add(token.substring(0, token.length() - 2));
    }
    if (token.endsWith("ing") && token.length() > 4) {
      candidates.add(token.substring(0, token.length() - 3));
    }
    if (token.endsWith("ied") && token.length() > 4) {
      candidates.add(token.substring(0, token.length() - 3) + "y");
    }
    if (token.endsWith("ed") && token.length() > 3) {
      candidates.add(token.substring(0, token.length() - 2));
    }
    if (token.endsWith("es") && token.length() > 3) {
      candidates.add(token.substring(0, token.length() - 2));
    }
    if (token.endsWith("s") && token.length() > 2) {
      candidates.add(token.substring(0, token.length() - 1));
    }
    if (token.endsWith("ly") && token.length() > 3) {
      candidates.add(token.substring(0, token.length() - 2));
    }

    for (String candidate : candidates) {
      String clean = candidate.replaceAll("[^a-z']", "").trim();
      if (clean.length() >= 2) {
        return clean;
      }
    }
    return token.replaceAll("[^a-z']", "");
  }
}
