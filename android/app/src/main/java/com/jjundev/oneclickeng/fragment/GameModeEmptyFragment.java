package com.jjundev.oneclickeng.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.activity.MinefieldGameActivity;
import com.jjundev.oneclickeng.activity.NativeOrNotGameActivity;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;

public class GameModeEmptyFragment extends Fragment {

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_game_mode_empty, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    setupClickListeners(view);
    startCardAnimations(view);
  }

  private void setupClickListeners(@NonNull View view) {
    View nativeOrNotCard = view.findViewById(R.id.card_game_native_or_not);
    View minefieldCard = view.findViewById(R.id.card_game_fill_blank);

    if (nativeOrNotCard != null) {
      nativeOrNotCard.setOnClickListener(
          v -> {
            if (!hasEffectiveApiKey()) {
              showNativeOrNotApiKeyRequiredDialog(v);
              return;
            }
            startActivity(new Intent(requireContext(), NativeOrNotGameActivity.class));
          });
    }

    if (minefieldCard != null) {
      minefieldCard.setOnClickListener(
          v -> {
            if (!hasEffectiveApiKey()) {
              showMinefieldApiKeyRequiredDialog(v);
              return;
            }
            startActivity(new Intent(requireContext(), MinefieldGameActivity.class));
          });
    }
  }

  private boolean hasEffectiveApiKey() {
    AppSettingsStore store = new AppSettingsStore(requireContext().getApplicationContext());
    AppSettings settings = store.getSettings();
    String apiKey = settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY);
    return apiKey != null && !apiKey.trim().isEmpty();
  }

  private void showNativeOrNotApiKeyRequiredDialog(@NonNull View anchorView) {
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.native_or_not_api_missing_title)
        .setMessage(R.string.native_or_not_api_missing_message)
        .setNegativeButton(R.string.native_or_not_api_missing_cancel, null)
        .setPositiveButton(
            R.string.native_or_not_api_missing_open_settings,
            (dialog, which) ->
                Navigation.findNavController(anchorView).navigate(R.id.settingFragment))
        .show();
  }

  private void showMinefieldApiKeyRequiredDialog(@NonNull View anchorView) {
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.minefield_api_missing_title)
        .setMessage(R.string.minefield_api_missing_message)
        .setNegativeButton(R.string.minefield_api_missing_cancel, null)
        .setPositiveButton(
            R.string.minefield_api_missing_open_settings,
            (dialog, which) ->
                Navigation.findNavController(anchorView).navigate(R.id.settingFragment))
        .show();
  }

  private void startCardAnimations(View view) {
    View[] cards = {
      view.findViewById(R.id.card_game_native_or_not),
      view.findViewById(R.id.card_game_word_speed),
      view.findViewById(R.id.card_game_fill_blank),
      view.findViewById(R.id.card_game_grammar_race),
      view.findViewById(R.id.card_game_survival)
    };

    long baseDelay = 0; // Start immediately

    for (int i = 0; i < cards.length; i++) {
      View card = cards[i];
      if (card != null) {
        card.setAlpha(0f);
        card.setTranslationX(-100f);
        card.animate()
            .alpha(1f)
            .translationX(0f)
            .setStartDelay(baseDelay + (i * 150L))
            .setDuration(500)
            .start();
      }
    }
  }
}
