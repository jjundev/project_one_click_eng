package com.jjundev.oneclickeng.fragment.dialoguelearning.manager_contracts;

import androidx.annotation.NonNull;
import com.jjundev.oneclickeng.fragment.dialoguelearning.model.FutureFeedbackResult;
import com.jjundev.oneclickeng.summary.SummaryFeatureBundle;
import java.util.List;

public interface ISessionSummaryLlmManager {
  interface FutureFeedbackCallback {
    void onSuccess(@NonNull FutureFeedbackResult feedback);

    void onFailure(@NonNull String error);
  }

  interface WordExtractionCallback {
    void onSuccess(@NonNull List<ExtractedWord> words);

    void onFailure(@NonNull String error);
  }

  final class ExtractedWord {
    @NonNull private final String en;
    @NonNull private final String ko;
    @NonNull private final String exampleEn;
    @NonNull private final String exampleKo;

    public ExtractedWord(
        @NonNull String en,
        @NonNull String ko,
        @NonNull String exampleEn,
        @NonNull String exampleKo) {
      this.en = en;
      this.ko = ko;
      this.exampleEn = exampleEn;
      this.exampleKo = exampleKo;
    }

    @NonNull
    public String getEn() {
      return en;
    }

    @NonNull
    public String getKo() {
      return ko;
    }

    @NonNull
    public String getExampleEn() {
      return exampleEn;
    }

    @NonNull
    public String getExampleKo() {
      return exampleKo;
    }
  }

  void generateFutureFeedbackStreamingAsync(
      @NonNull SummaryFeatureBundle bundle, @NonNull FutureFeedbackCallback callback);

  void extractWordsFromSentencesAsync(
      @NonNull List<String> words,
      @NonNull List<String> sentences,
      @NonNull WordExtractionCallback callback);
}
