package com.jjundev.oneclickeng.game.nativeornot;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotRoundResult;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NativeOrNotStatsStore {
  private static final String PREF_NAME = "native_or_not_stats_store";
  private static final String KEY_ROUNDS = "rounds";
  private static final int MAX_HISTORY_SIZE = 20;

  @NonNull private final SharedPreferences preferences;
  @NonNull private final Gson gson;

  public NativeOrNotStatsStore(@NonNull Context context) {
    this.preferences =
        context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    this.gson = new Gson();
  }

  public void saveRoundResult(@NonNull NativeOrNotRoundResult result) {
    List<NativeOrNotRoundResult> existing = getRecentResults();
    existing.add(0, result);
    if (existing.size() > MAX_HISTORY_SIZE) {
      existing = new ArrayList<>(existing.subList(0, MAX_HISTORY_SIZE));
    }
    preferences.edit().putString(KEY_ROUNDS, gson.toJson(existing)).apply();
  }

  @NonNull
  public List<NativeOrNotRoundResult> getRecentResults() {
    String raw = preferences.getString(KEY_ROUNDS, "");
    if (raw == null || raw.trim().isEmpty()) {
      return new ArrayList<>();
    }
    try {
      Type type = new TypeToken<List<NativeOrNotRoundResult>>() {}.getType();
      List<NativeOrNotRoundResult> parsed = gson.fromJson(raw, type);
      if (parsed == null) {
        return new ArrayList<>();
      }
      return new ArrayList<>(parsed);
    } catch (Exception ignored) {
      return new ArrayList<>();
    }
  }

  public void clear() {
    preferences.edit().remove(KEY_ROUNDS).apply();
  }

  public int getMaxHistorySize() {
    return MAX_HISTORY_SIZE;
  }
}
