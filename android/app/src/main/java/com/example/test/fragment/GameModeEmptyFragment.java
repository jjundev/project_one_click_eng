package com.example.test.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.test.R;

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
    startCardAnimations(view);
  }

  private void startCardAnimations(View view) {
    View[] cards = {
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
