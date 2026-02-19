package com.example.test.fragment.dialoguelearning.model;

import androidx.annotation.NonNull;

public class FutureFeedbackResult {
  @NonNull private final String positive;
  @NonNull private final String toImprove;

  public FutureFeedbackResult(@NonNull String positive, @NonNull String toImprove) {
    this.positive = positive;
    this.toImprove = toImprove;
  }

  @NonNull
  public String getPositive() {
    return positive;
  }

  @NonNull
  public String getToImprove() {
    return toImprove;
  }
}
