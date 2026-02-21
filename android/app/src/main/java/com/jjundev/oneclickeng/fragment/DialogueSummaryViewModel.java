package com.jjundev.oneclickeng.fragment;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.gson.Gson;
import com.jjundev.oneclickeng.fragment.dialoguelearning.manager_contracts.ISessionSummaryLlmManager;
import com.jjundev.oneclickeng.fragment.dialoguelearning.model.FutureFeedbackResult;
import com.jjundev.oneclickeng.fragment.dialoguelearning.model.SummaryData;
import com.jjundev.oneclickeng.summary.SessionSummaryGenerator;
import com.jjundev.oneclickeng.summary.SummaryFeatureBundle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DialogueSummaryViewModel extends ViewModel {

  private static final String STATE_FUTURE_FEEDBACK_STATUS = "state_future_feedback_status";
  private static final String STATE_FUTURE_FEEDBACK_JSON = "state_future_feedback_json";
  private static final String STATE_FUTURE_FEEDBACK_ERROR = "state_future_feedback_error";
  private static final String STATE_SUMMARY_DATA_JSON = "state_summary_data_json";
  private static final String STATE_WORD_LOAD_STATUS = "state_word_load_status";
  private static final String STATE_WORD_LOAD_ERROR = "state_word_load_error";
  private static final int MAX_WORDS = 12;
  private static final Gson GSON = new Gson();

  private final ISessionSummaryLlmManager sessionSummaryLlmManager;
  private final MutableLiveData<SummaryData> summaryData =
      new MutableLiveData<>(new SessionSummaryGenerator().createEmptySummary());
  private final MutableLiveData<FutureFeedbackState> futureFeedbackState =
      new MutableLiveData<>(FutureFeedbackState.loading());
  private final MutableLiveData<WordLoadState> wordLoadState =
      new MutableLiveData<>(WordLoadState.loading());

  @Nullable private SummaryFeatureBundle featureBundle;
  private int requestToken = 0;
  private boolean hasInitialized = false;
  private boolean hasWordExtractionStarted = false;

  public DialogueSummaryViewModel(@NonNull ISessionSummaryLlmManager sessionSummaryLlmManager) {
    this.sessionSummaryLlmManager = sessionSummaryLlmManager;
  }

  public LiveData<SummaryData> getSummaryData() {
    return summaryData;
  }

  public LiveData<FutureFeedbackState> getFutureFeedbackState() {
    return futureFeedbackState;
  }

  public LiveData<WordLoadState> getWordLoadState() {
    return wordLoadState;
  }

  public void initialize(
      @Nullable String summaryJson,
      @Nullable String featureBundleJson,
      @Nullable Bundle savedInstanceState) {
    SummaryData restoredSummary = resolveSavedSummaryData(savedInstanceState);
    SummaryData initialSummary =
        restoredSummary == null ? resolveSummaryData(summaryJson) : restoredSummary;
    if (initialSummary.getWords() == null) {
      initialSummary.setWords(new ArrayList<>());
    }
    if (restoredSummary == null) {
      // Words are API-only. Hide any pre-generated fallback words until API succeeds.
      initialSummary.setWords(new ArrayList<>());
    }
    summaryData.setValue(initialSummary);

    if (!hasInitialized) {
      hasInitialized = true;
      featureBundle = resolveFeatureBundle(featureBundleJson);
      restoreFutureFeedbackState(savedInstanceState);
      restoreWordLoadState(savedInstanceState);
    }

    FutureFeedbackState currentState = futureFeedbackState.getValue();
    if (currentState != null && currentState.status == FutureFeedbackStatus.LOADING) {
      startFutureFeedbackStreaming();
    }

    if (!hasWordExtractionStarted) {
      WordLoadState currentWordState = wordLoadState.getValue();
      if (currentWordState != null && currentWordState.status == WordLoadStatus.LOADING) {
        hasWordExtractionStarted = true;
        startWordExtractionIfPossible();
      }
    }
  }

  public void onViewDestroyed() {
    requestToken++;
  }

  public void saveState(@NonNull Bundle outState) {
    FutureFeedbackState state = futureFeedbackState.getValue();
    if (state != null) {
      outState.putString(STATE_FUTURE_FEEDBACK_STATUS, state.status.name());
      outState.putString(STATE_FUTURE_FEEDBACK_ERROR, state.errorMessage);
      if (state.feedback != null) {
        outState.putString(STATE_FUTURE_FEEDBACK_JSON, GSON.toJson(state.feedback));
      }
    }
    SummaryData currentSummary = summaryData.getValue();
    if (currentSummary != null) {
      outState.putString(STATE_SUMMARY_DATA_JSON, GSON.toJson(currentSummary));
    }

    WordLoadState wordState = wordLoadState.getValue();
    if (wordState != null) {
      outState.putString(STATE_WORD_LOAD_STATUS, wordState.status.name());
      outState.putString(STATE_WORD_LOAD_ERROR, wordState.errorMessage);
    }
  }

  private void startFutureFeedbackStreaming() {
    if (featureBundle == null) {
      setFutureFeedbackError(null);
      return;
    }

    final int currentToken = ++requestToken;
    futureFeedbackState.setValue(FutureFeedbackState.loading());
    sessionSummaryLlmManager.generateFutureFeedbackStreamingAsync(
        featureBundle,
        new ISessionSummaryLlmManager.FutureFeedbackCallback() {
          @Override
          public void onSuccess(FutureFeedbackResult feedback) {
            if (!isRequestActive(currentToken)) {
              return;
            }

            String positive = feedback == null ? "" : feedback.getPositive();
            String toImprove = feedback == null ? "" : feedback.getToImprove();
            futureFeedbackState.setValue(
                FutureFeedbackState.ready(
                    new SummaryData.FutureSelfFeedback(
                        positive == null ? "" : positive, toImprove == null ? "" : toImprove),
                    null));
          }

          @Override
          public void onFailure(@NonNull String error) {
            if (!isRequestActive(currentToken)) {
              return;
            }
            setFutureFeedbackError(error);
          }
        });
  }

  private void setFutureFeedbackError(@Nullable String message) {
    futureFeedbackState.setValue(FutureFeedbackState.error(message));
  }

  private boolean isRequestActive(int token) {
    return token == requestToken;
  }

  private void restoreFutureFeedbackState(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      futureFeedbackState.setValue(FutureFeedbackState.loading());
      return;
    }

    FutureFeedbackStatus status = FutureFeedbackStatus.LOADING;
    try {
      String rawStatus = savedInstanceState.getString(STATE_FUTURE_FEEDBACK_STATUS);
      if (rawStatus != null) {
        status = FutureFeedbackStatus.valueOf(rawStatus);
      }
    } catch (Exception ignored) {
      status = FutureFeedbackStatus.LOADING;
    }

    String errorMessage = savedInstanceState.getString(STATE_FUTURE_FEEDBACK_ERROR);
    SummaryData.FutureSelfFeedback restoredFeedback =
        parseFutureFeedback(savedInstanceState.getString(STATE_FUTURE_FEEDBACK_JSON));

    if (status == FutureFeedbackStatus.READY && restoredFeedback == null) {
      futureFeedbackState.setValue(FutureFeedbackState.error(null));
      return;
    }

    if (status == FutureFeedbackStatus.READY) {
      futureFeedbackState.setValue(FutureFeedbackState.ready(restoredFeedback, errorMessage));
      return;
    }

    if (status == FutureFeedbackStatus.ERROR) {
      futureFeedbackState.setValue(FutureFeedbackState.error(errorMessage));
      return;
    }

    futureFeedbackState.setValue(FutureFeedbackState.loading());
  }

  private void restoreWordLoadState(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      wordLoadState.setValue(WordLoadState.loading());
      return;
    }

    WordLoadStatus status = WordLoadStatus.LOADING;
    try {
      String rawStatus = savedInstanceState.getString(STATE_WORD_LOAD_STATUS);
      if (rawStatus != null) {
        status = WordLoadStatus.valueOf(rawStatus);
      }
    } catch (Exception ignored) {
      status = WordLoadStatus.LOADING;
    }

    String errorMessage = savedInstanceState.getString(STATE_WORD_LOAD_ERROR);
    if (status == WordLoadStatus.READY) {
      wordLoadState.setValue(WordLoadState.ready());
      return;
    }
    if (status == WordLoadStatus.ERROR) {
      wordLoadState.setValue(WordLoadState.error(errorMessage));
      return;
    }
    wordLoadState.setValue(WordLoadState.loading());
  }

  private SummaryData resolveSummaryData(@Nullable String summaryJson) {
    if (summaryJson != null && !summaryJson.trim().isEmpty()) {
      try {
        SummaryData parsed = GSON.fromJson(summaryJson, SummaryData.class);
        if (parsed != null) {
          return parsed;
        }
      } catch (Exception ignored) {
        // Fallback handled below.
      }
    }
    return new SessionSummaryGenerator().createEmptySummary();
  }

  @Nullable
  private SummaryData resolveSavedSummaryData(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      return null;
    }
    return parseSummaryData(savedInstanceState.getString(STATE_SUMMARY_DATA_JSON));
  }

  @Nullable
  private SummaryData parseSummaryData(@Nullable String json) {
    if (json == null || json.trim().isEmpty()) {
      return null;
    }
    try {
      return GSON.fromJson(json, SummaryData.class);
    } catch (Exception ignored) {
      return null;
    }
  }

  private SummaryFeatureBundle resolveFeatureBundle(@Nullable String bundleJson) {
    if (bundleJson == null || bundleJson.trim().isEmpty()) {
      return null;
    }
    try {
      return GSON.fromJson(bundleJson, SummaryFeatureBundle.class);
    } catch (Exception ignored) {
      return null;
    }
  }

  private void startWordExtractionIfPossible() {
    if (featureBundle == null) {
      setWordError(null);
      return;
    }

    List<String> words = collectWordSeeds(featureBundle.getWordCandidates());
    List<String> sentences = collectSentenceCandidates(featureBundle.getSentenceCandidates());
    if (words.isEmpty() || sentences.isEmpty()) {
      setWordError(null);
      return;
    }

    wordLoadState.setValue(WordLoadState.loading());
    final int currentToken = requestToken;
    sessionSummaryLlmManager.extractWordsFromSentencesAsync(
        words,
        sentences,
        new ISessionSummaryLlmManager.WordExtractionCallback() {
          @Override
          public void onSuccess(@NonNull List<ISessionSummaryLlmManager.ExtractedWord> words) {
            if (!isRequestActive(currentToken)) {
              return;
            }

            List<SummaryData.WordItem> apiWords = toApiWordItems(words);
            applyWordsToSummary(apiWords);
            if (apiWords.isEmpty()) {
              setWordError(null);
              return;
            }
            wordLoadState.setValue(WordLoadState.ready());
          }

          @Override
          public void onFailure(@NonNull String error) {
            if (!isRequestActive(currentToken)) {
              return;
            }
            applyWordsToSummary(new ArrayList<>());
            setWordError(error);
          }
        });
  }

  @NonNull
  private List<String> collectWordSeeds(
      @Nullable List<SummaryFeatureBundle.WordCandidate> candidates) {
    List<String> result = new ArrayList<>();
    if (candidates == null) {
      return result;
    }
    for (SummaryFeatureBundle.WordCandidate candidate : candidates) {
      if (candidate == null) {
        continue;
      }
      String english = trimToNull(candidate.getEnglish());
      if (english == null) {
        continue;
      }
      if (!containsNormalized(result, english)) {
        result.add(english);
      }
    }
    return result;
  }

  @NonNull
  private List<String> collectSentenceCandidates(@Nullable List<String> candidates) {
    List<String> result = new ArrayList<>();
    if (candidates == null) {
      return result;
    }
    for (String candidate : candidates) {
      String sentence = trimToNull(candidate);
      if (sentence == null) {
        continue;
      }
      if (!containsNormalized(result, sentence)) {
        result.add(sentence);
      }
    }
    return result;
  }

  private boolean containsNormalized(@NonNull List<String> values, @NonNull String target) {
    String normalizedTarget = normalize(target);
    for (String value : values) {
      if (normalize(value).equals(normalizedTarget)) {
        return true;
      }
    }
    return false;
  }

  @NonNull
  private List<SummaryData.WordItem> toApiWordItems(
      @Nullable List<ISessionSummaryLlmManager.ExtractedWord> extractedWords) {
    List<SummaryData.WordItem> result = new ArrayList<>();
    if (extractedWords == null || extractedWords.isEmpty()) {
      return result;
    }
    Set<String> seen = new HashSet<>();
    for (ISessionSummaryLlmManager.ExtractedWord extracted : extractedWords) {
      if (extracted == null || result.size() >= MAX_WORDS) {
        continue;
      }
      String english = trimToNull(extracted.getEn());
      String korean = trimToNull(extracted.getKo());
      if (english == null || korean == null) {
        continue;
      }
      String key = normalize(english);
      if (seen.contains(key)) {
        continue;
      }
      seen.add(key);
      String exampleEnglish = firstNonBlank(extracted.getExampleEn(), english);
      String exampleKorean = firstNonBlank(extracted.getExampleKo(), korean);
      result.add(new SummaryData.WordItem(english, korean, exampleEnglish, exampleKorean));
    }
    return result;
  }

  private void applyWordsToSummary(@NonNull List<SummaryData.WordItem> words) {
    SummaryData current = summaryData.getValue();
    if (current == null) {
      current = new SessionSummaryGenerator().createEmptySummary();
    }
    SummaryData updated = new SummaryData();
    updated.setTotalScore(current.getTotalScore());
    updated.setHighlights(current.getHighlights());
    updated.setExpressions(current.getExpressions());
    updated.setWords(new ArrayList<>(words));
    updated.setLikedSentences(current.getLikedSentences());
    updated.setFutureSelfFeedback(current.getFutureSelfFeedback());
    summaryData.setValue(updated);
  }

  private SummaryData.FutureSelfFeedback parseFutureFeedback(@Nullable String json) {
    if (json == null || json.trim().isEmpty()) {
      return null;
    }
    try {
      return GSON.fromJson(json, SummaryData.FutureSelfFeedback.class);
    } catch (Exception ignored) {
      return null;
    }
  }

  @Nullable
  private String trimToNull(@Nullable String text) {
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @NonNull
  private String normalize(@Nullable String text) {
    return text == null ? "" : text.trim().toLowerCase();
  }

  @NonNull
  private String firstNonBlank(@Nullable String first, @NonNull String fallback) {
    String value = trimToNull(first);
    return value == null ? fallback : value;
  }

  private void setWordError(@Nullable String message) {
    wordLoadState.setValue(WordLoadState.error(message));
  }

  public static final class FutureFeedbackState {
    private final FutureFeedbackStatus status;
    @Nullable private final SummaryData.FutureSelfFeedback feedback;
    @Nullable private final String errorMessage;

    private FutureFeedbackState(
        @NonNull FutureFeedbackStatus status,
        @Nullable SummaryData.FutureSelfFeedback feedback,
        @Nullable String errorMessage) {
      this.status = status;
      this.feedback = feedback;
      this.errorMessage = errorMessage;
    }

    public static FutureFeedbackState loading() {
      return new FutureFeedbackState(FutureFeedbackStatus.LOADING, null, null);
    }

    public static FutureFeedbackState ready(
        @Nullable SummaryData.FutureSelfFeedback feedback, @Nullable String errorMessage) {
      return new FutureFeedbackState(FutureFeedbackStatus.READY, feedback, errorMessage);
    }

    public static FutureFeedbackState error(@Nullable String errorMessage) {
      return new FutureFeedbackState(FutureFeedbackStatus.ERROR, null, errorMessage);
    }

    @NonNull
    public FutureFeedbackStatus getStatus() {
      return status;
    }

    @Nullable
    public SummaryData.FutureSelfFeedback getFeedback() {
      return feedback;
    }

    @Nullable
    public String getErrorMessage() {
      return errorMessage;
    }
  }

  public enum FutureFeedbackStatus {
    LOADING,
    READY,
    ERROR
  }

  public static final class WordLoadState {
    private final WordLoadStatus status;
    @Nullable private final String errorMessage;

    private WordLoadState(@NonNull WordLoadStatus status, @Nullable String errorMessage) {
      this.status = status;
      this.errorMessage = errorMessage;
    }

    public static WordLoadState loading() {
      return new WordLoadState(WordLoadStatus.LOADING, null);
    }

    public static WordLoadState ready() {
      return new WordLoadState(WordLoadStatus.READY, null);
    }

    public static WordLoadState error(@Nullable String errorMessage) {
      return new WordLoadState(WordLoadStatus.ERROR, errorMessage);
    }

    @NonNull
    public WordLoadStatus getStatus() {
      return status;
    }

    @Nullable
    public String getErrorMessage() {
      return errorMessage;
    }
  }

  public enum WordLoadStatus {
    LOADING,
    READY,
    ERROR
  }
}
