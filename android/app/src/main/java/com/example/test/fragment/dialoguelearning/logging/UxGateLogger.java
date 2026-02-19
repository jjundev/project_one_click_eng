package com.example.test.fragment.dialoguelearning.logging;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.test.BuildConfig;

public final class UxGateLogger {
  private static final String TAG = "DialogueLearningUX";

  private final long sessionId;
  private long seq = 0L;

  public UxGateLogger(long sessionId) {
    this.sessionId = sessionId;
  }

  public void log(@NonNull String key) {
    log(key, null);
  }

  public void log(@NonNull String key, @Nullable String fields) {
    if (!BuildConfig.DEBUG) {
      return;
    }

    String safeKey = key == null ? "" : key.trim();
    if (safeKey.isEmpty()) {
      return;
    }

    long nextSeq = ++seq;
    String base = safeKey + " session=" + sessionId + " seq=" + nextSeq;
    if (fields == null) {
      Log.d(TAG, base);
      return;
    }

    String safeFields = fields.trim();
    if (safeFields.isEmpty()) {
      Log.d(TAG, base);
      return;
    }

    Log.d(TAG, base + " " + safeFields);
  }
}
