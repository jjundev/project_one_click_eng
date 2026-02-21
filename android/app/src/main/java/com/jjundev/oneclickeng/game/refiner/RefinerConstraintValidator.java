package com.jjundev.oneclickeng.game.refiner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.game.refiner.model.RefinerConstraints;
import com.jjundev.oneclickeng.game.refiner.model.RefinerWordLimit;
import com.jjundev.oneclickeng.game.refiner.model.RefinerWordLimitMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RefinerConstraintValidator {
  private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z']+");

  private RefinerConstraintValidator() {}

  @NonNull
  public static ValidationResult validate(
      @NonNull RefinerConstraints constraints, @Nullable String sentence) {
    String safeSentence = sentence == null ? "" : sentence;
    List<TokenRange> tokenRanges = tokenizeWithRanges(safeSentence);
    Set<String> sentenceStems = extractSentenceStems(tokenRanges);

    List<TokenRange> bannedRanges = new ArrayList<>();
    Set<String> bannedWords = toStemSet(constraints.getBannedWords());
    for (TokenRange tokenRange : tokenRanges) {
      if (intersects(tokenRange.variants, bannedWords)) {
        bannedRanges.add(tokenRange);
      }
    }
    boolean bannedWordsSatisfied = bannedRanges.isEmpty();

    int wordCount = tokenRanges.size();
    boolean wordLimitSatisfied = isWordLimitSatisfied(constraints.getWordLimit(), wordCount);
    boolean requiredWordSatisfied =
        isRequiredWordSatisfied(constraints.getRequiredWord(), safeSentence, sentenceStems);
    boolean allSatisfied =
        wordCount > 0 && bannedWordsSatisfied && wordLimitSatisfied && requiredWordSatisfied;

    return new ValidationResult(
        wordCount,
        bannedWordsSatisfied,
        wordLimitSatisfied,
        requiredWordSatisfied,
        allSatisfied,
        bannedRanges);
  }

  @NonNull
  private static Set<String> toStemSet(@NonNull List<String> sourceWords) {
    Set<String> stems = new LinkedHashSet<>();
    for (String sourceWord : sourceWords) {
      String normalized = normalizeForSentence(sourceWord);
      if (normalized.isEmpty()) {
        continue;
      }
      if (normalized.contains(" ")) {
        String[] parts = normalized.split("\\s+");
        for (String part : parts) {
          stems.addAll(stemVariants(part));
        }
        continue;
      }
      stems.addAll(stemVariants(normalized));
    }
    return stems;
  }

  private static boolean isWordLimitSatisfied(@Nullable RefinerWordLimit wordLimit, int wordCount) {
    if (wordLimit == null) {
      return true;
    }
    RefinerWordLimitMode mode = wordLimit.getMode();
    if (mode == RefinerWordLimitMode.EXACT) {
      return wordCount == wordLimit.getValue();
    }
    return wordCount <= wordLimit.getValue();
  }

  private static boolean isRequiredWordSatisfied(
      @Nullable String requiredWordRaw,
      @NonNull String rawSentence,
      @NonNull Set<String> sentenceStems) {
    String requiredWord = normalizeForSentence(requiredWordRaw);
    if (requiredWord.isEmpty()) {
      return true;
    }
    String normalizedSentence = normalizeForSentence(rawSentence);
    if (requiredWord.contains(" ")) {
      if (normalizedSentence.contains(requiredWord)) {
        return true;
      }
      String[] parts = requiredWord.split("\\s+");
      for (String part : parts) {
        if (!intersects(stemVariants(part), sentenceStems)) {
          return false;
        }
      }
      return true;
    }
    return intersects(stemVariants(requiredWord), sentenceStems);
  }

  @NonNull
  private static Set<String> extractSentenceStems(@NonNull List<TokenRange> tokenRanges) {
    Set<String> stems = new LinkedHashSet<>();
    for (TokenRange tokenRange : tokenRanges) {
      stems.addAll(tokenRange.variants);
    }
    return stems;
  }

  @NonNull
  private static List<TokenRange> tokenizeWithRanges(@NonNull String sentence) {
    List<TokenRange> result = new ArrayList<>();
    Matcher matcher = TOKEN_PATTERN.matcher(sentence);
    while (matcher.find()) {
      String token = matcher.group();
      if (token == null) {
        continue;
      }
      Set<String> variants = stemVariants(token);
      if (variants.isEmpty()) {
        continue;
      }
      String stem = variants.iterator().next();
      result.add(new TokenRange(matcher.start(), matcher.end(), token, stem, variants));
    }
    return result;
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
  private static String stemToken(@Nullable String tokenRaw) {
    Set<String> variants = stemVariants(tokenRaw);
    if (variants.isEmpty()) {
      return "";
    }
    return variants.iterator().next();
  }

  @NonNull
  private static Set<String> stemVariants(@Nullable String tokenRaw) {
    Set<String> variants = new LinkedHashSet<>();
    if (tokenRaw == null) {
      return variants;
    }
    String token = tokenRaw.trim().toLowerCase(Locale.US);
    if (token.isEmpty()) {
      return variants;
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
      String base = token.substring(0, token.length() - 2);
      candidates.add(base);
      candidates.add(base + "e");
    }
    if (token.endsWith("es") && token.length() > 3) {
      String base = token.substring(0, token.length() - 2);
      candidates.add(base);
      candidates.add(base + "e");
    }
    if (token.endsWith("s") && token.length() > 2) {
      candidates.add(token.substring(0, token.length() - 1));
    }
    if (token.endsWith("ly") && token.length() > 3) {
      candidates.add(token.substring(0, token.length() - 2));
    }

    for (String candidate : candidates) {
      String clean = candidate.replaceAll("[^a-z']", "").trim();
      if (clean.length() >= 1) {
        variants.add(clean);
      }
    }
    return variants;
  }

  private static boolean intersects(@NonNull Set<String> left, @NonNull Set<String> right) {
    for (String item : left) {
      if (right.contains(item)) {
        return true;
      }
    }
    return false;
  }

  public static final class ValidationResult {
    private final int wordCount;
    private final boolean bannedWordsSatisfied;
    private final boolean wordLimitSatisfied;
    private final boolean requiredWordSatisfied;
    private final boolean allConstraintsSatisfied;
    @NonNull private final List<TokenRange> bannedWordRanges;

    private ValidationResult(
        int wordCount,
        boolean bannedWordsSatisfied,
        boolean wordLimitSatisfied,
        boolean requiredWordSatisfied,
        boolean allConstraintsSatisfied,
        @NonNull List<TokenRange> bannedWordRanges) {
      this.wordCount = Math.max(0, wordCount);
      this.bannedWordsSatisfied = bannedWordsSatisfied;
      this.wordLimitSatisfied = wordLimitSatisfied;
      this.requiredWordSatisfied = requiredWordSatisfied;
      this.allConstraintsSatisfied = allConstraintsSatisfied;
      this.bannedWordRanges = Collections.unmodifiableList(new ArrayList<>(bannedWordRanges));
    }

    public int getWordCount() {
      return wordCount;
    }

    public boolean isBannedWordsSatisfied() {
      return bannedWordsSatisfied;
    }

    public boolean isWordLimitSatisfied() {
      return wordLimitSatisfied;
    }

    public boolean isRequiredWordSatisfied() {
      return requiredWordSatisfied;
    }

    public boolean isAllConstraintsSatisfied() {
      return allConstraintsSatisfied;
    }

    @NonNull
    public List<TokenRange> getBannedWordRanges() {
      return bannedWordRanges;
    }
  }

  public static final class TokenRange {
    private final int start;
    private final int end;
    @NonNull private final String token;
    @NonNull private final String stem;
    @NonNull private final Set<String> variants;

    private TokenRange(
        int start,
        int end,
        @NonNull String token,
        @NonNull String stem,
        @NonNull Set<String> variants) {
      this.start = Math.max(0, start);
      this.end = Math.max(this.start, end);
      this.token = token;
      this.stem = stem;
      this.variants = Collections.unmodifiableSet(new LinkedHashSet<>(variants));
    }

    public int getStart() {
      return start;
    }

    public int getEnd() {
      return end;
    }

    @NonNull
    public String getToken() {
      return token;
    }

    @NonNull
    public String getStem() {
      return stem;
    }
  }
}
