package com.jjundev.oneclickeng.game.minefield;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldRoundResult;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class MinefieldStatsStore {
  private static final String PREF_NAME = "minefield_stats_store";
  private static final String KEY_ROUNDS = "rounds";
  private static final int MAX_HISTORY_SIZE = 20;

  @NonNull private final SharedPreferences preferences;
  @NonNull private final Gson gson;

  public MinefieldStatsStore(@NonNull Context context) {
    this.preferences =
        context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    this.gson = new Gson();
  }

  public void saveRoundResult(@NonNull MinefieldRoundResult result) {
    List<MinefieldRoundResult> rounds = getRecentResults();
    rounds.add(0, result);
    if (rounds.size() > MAX_HISTORY_SIZE) {
      rounds = new ArrayList<>(rounds.subList(0, MAX_HISTORY_SIZE));
    }
    preferences.edit().putString(KEY_ROUNDS, gson.toJson(rounds)).apply();
  }

  @NonNull
  public List<MinefieldRoundResult> getRecentResults() {
    String raw = preferences.getString(KEY_ROUNDS, "");
    if (raw == null || raw.trim().isEmpty()) {
      return new ArrayList<>();
    }
    try {
      Type type = new TypeToken<List<MinefieldRoundResult>>() {}.getType();
      List<MinefieldRoundResult> parsed = gson.fromJson(raw, type);
      return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
    } catch (Exception ignored) {
      return new ArrayList<>();
    }
  }

  public void clear() {
    preferences.edit().remove(KEY_ROUNDS).apply();
  }
}
