package com.jjundev.oneclickeng.activity;

import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.jjundev.oneclickeng.settings.LearningStudyTimeCloudRepository;
import com.jjundev.oneclickeng.settings.LearningStudyTimeStore;

/** Base activity for learning screens that should contribute to daily visible study time. */
public abstract class LearningActivity extends AppCompatActivity {
  private static final long UNSET = -1L;

  @Nullable private LearningStudyTimeStore learningStudyTimeStore;
  @Nullable private LearningStudyTimeCloudRepository learningStudyTimeCloudRepository;
  private long visibleStartElapsedRealtimeMs = UNSET;
  private long visibleStartWallClockMs = UNSET;

  @Override
  protected void onStart() {
    super.onStart();
    visibleStartElapsedRealtimeMs = SystemClock.elapsedRealtime();
    visibleStartWallClockMs = System.currentTimeMillis();
  }

  @Override
  protected void onStop() {
    recordVisibleIntervalIfNeeded();
    super.onStop();
  }

  private void recordVisibleIntervalIfNeeded() {
    if (visibleStartElapsedRealtimeMs < 0L || visibleStartWallClockMs <= 0L) {
      resetVisibleStartState();
      return;
    }

    long endElapsedRealtimeMs = SystemClock.elapsedRealtime();
    long visibleDurationMs = endElapsedRealtimeMs - visibleStartElapsedRealtimeMs;
    if (visibleDurationMs <= 0L) {
      resetVisibleStartState();
      return;
    }

    long endWallClockMs = visibleStartWallClockMs + visibleDurationMs;
    if (endWallClockMs < visibleStartWallClockMs) {
      endWallClockMs = System.currentTimeMillis();
    }

    LearningStudyTimeStore localStore = getLearningStudyTimeStore();
    localStore.recordVisibleInterval(visibleStartWallClockMs, endWallClockMs);
    getLearningStudyTimeCloudRepository()
        .recordIntervalForCurrentUser(visibleStartWallClockMs, endWallClockMs);
    resetVisibleStartState();
  }

  @NonNull
  private LearningStudyTimeStore getLearningStudyTimeStore() {
    if (learningStudyTimeStore == null) {
      learningStudyTimeStore = new LearningStudyTimeStore(getApplicationContext());
    }
    return learningStudyTimeStore;
  }

  @NonNull
  private LearningStudyTimeCloudRepository getLearningStudyTimeCloudRepository() {
    if (learningStudyTimeCloudRepository == null) {
      learningStudyTimeCloudRepository =
          new LearningStudyTimeCloudRepository(getApplicationContext());
    }
    return learningStudyTimeCloudRepository;
  }

  private void resetVisibleStartState() {
    visibleStartElapsedRealtimeMs = UNSET;
    visibleStartWallClockMs = UNSET;
  }
}
