package com.jjundev.oneclickeng.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AppSettings {
  public static final String DEFAULT_MODEL_SENTENCE = "gemini-3-flash-preview";
  public static final String DEFAULT_MODEL_SPEAKING = "gemini-2.5-flash-lite";
  public static final String DEFAULT_MODEL_SCRIPT = "gemini-3-flash-preview";
  public static final String DEFAULT_MODEL_SUMMARY = "gemini-3-flash-preview";
  public static final String DEFAULT_MODEL_EXTRA = "gemini-3-flash-preview";
  public static final String DEFAULT_MODEL_MINEFIELD = "gemini-3-flash-preview";
  public static final String DEFAULT_MODEL_REFINER = "gemini-3-flash-preview";
  public static final String TTS_PROVIDER_ANDROID = "android";
  public static final String TTS_PROVIDER_GOOGLE = "google";
  public static final String DEFAULT_TTS_PROVIDER = TTS_PROVIDER_ANDROID;
  public static final float DEFAULT_TTS_SPEECH_RATE = 0.85f;
  public static final String DEFAULT_TTS_LOCALE_TAG = "en-US";

  private final boolean muteAllPlayback;
  @NonNull private final String userNickname;
  @NonNull private final String llmApiKeyOverride;
  @NonNull private final String llmModelSentence;
  @NonNull private final String llmModelSpeaking;
  @NonNull private final String llmModelScript;
  @NonNull private final String llmModelSummary;
  @NonNull private final String llmModelExtra;
  @NonNull private final String llmModelMinefield;
  @NonNull private final String llmModelRefiner;
  @NonNull private final String ttsProvider;
  private final float ttsSpeechRate;
  @NonNull private final String ttsLocaleTag;

  public AppSettings(
      boolean muteAllPlayback,
      @Nullable String userNickname,
      @Nullable String llmApiKeyOverride,
      @Nullable String llmModelSentence,
      @Nullable String llmModelSpeaking,
      @Nullable String llmModelScript,
      @Nullable String llmModelSummary,
      @Nullable String llmModelExtra,
      @Nullable String llmModelMinefield,
      @Nullable String llmModelRefiner,
      @Nullable String ttsProvider,
      float ttsSpeechRate,
      @Nullable String ttsLocaleTag) {
    this.muteAllPlayback = muteAllPlayback;
    this.userNickname = normalizeOrEmpty(userNickname);
    this.llmApiKeyOverride = normalizeOrEmpty(llmApiKeyOverride);
    this.llmModelSentence = normalizeOrDefault(llmModelSentence, DEFAULT_MODEL_SENTENCE);
    this.llmModelSpeaking = normalizeOrDefault(llmModelSpeaking, DEFAULT_MODEL_SPEAKING);
    this.llmModelScript = normalizeOrDefault(llmModelScript, DEFAULT_MODEL_SCRIPT);
    this.llmModelSummary = normalizeOrDefault(llmModelSummary, DEFAULT_MODEL_SUMMARY);
    this.llmModelExtra = normalizeOrDefault(llmModelExtra, DEFAULT_MODEL_EXTRA);
    this.llmModelMinefield = normalizeOrDefault(llmModelMinefield, DEFAULT_MODEL_MINEFIELD);
    this.llmModelRefiner = normalizeOrDefault(llmModelRefiner, DEFAULT_MODEL_REFINER);
    this.ttsProvider = normalizeOrDefault(ttsProvider, DEFAULT_TTS_PROVIDER);
    this.ttsSpeechRate = clamp(ttsSpeechRate, 0.5f, 1.5f);
    this.ttsLocaleTag = normalizeOrDefault(ttsLocaleTag, DEFAULT_TTS_LOCALE_TAG);
  }

  public boolean isMuteAllPlayback() {
    return muteAllPlayback;
  }

  @NonNull
  public String getUserNickname() {
    return userNickname;
  }

  @NonNull
  public String getLlmApiKeyOverride() {
    return llmApiKeyOverride;
  }

  @NonNull
  public String getLlmModelSentence() {
    return llmModelSentence;
  }

  @NonNull
  public String getLlmModelSpeaking() {
    return llmModelSpeaking;
  }

  @NonNull
  public String getLlmModelScript() {
    return llmModelScript;
  }

  @NonNull
  public String getLlmModelSummary() {
    return llmModelSummary;
  }

  @NonNull
  public String getLlmModelExtra() {
    return llmModelExtra;
  }

  @NonNull
  public String getLlmModelMinefield() {
    return llmModelMinefield;
  }

  @NonNull
  public String getLlmModelRefiner() {
    return llmModelRefiner;
  }

  @NonNull
  public String getTtsProvider() {
    return ttsProvider;
  }

  public float getTtsSpeechRate() {
    return ttsSpeechRate;
  }

  @NonNull
  public String getTtsLocaleTag() {
    return ttsLocaleTag;
  }

  @NonNull
  public String resolveEffectiveApiKey(@Nullable String defaultApiKey) {
    if (!llmApiKeyOverride.isEmpty()) {
      return llmApiKeyOverride;
    }
    return normalizeOrEmpty(defaultApiKey);
  }

  @NonNull
  private static String normalizeOrDefault(@Nullable String value, @NonNull String defaultValue) {
    String trimmed = normalizeOrEmpty(value);
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }

  @NonNull
  private static String normalizeOrEmpty(@Nullable String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? "" : trimmed;
  }

  private static float clamp(float value, float min, float max) {
    if (value < min) {
      return min;
    }
    return Math.min(value, max);
  }
}
