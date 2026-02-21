package com.jjundev.oneclickeng.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import com.jjundev.oneclickeng.widget.SlotMachineTextView;

public class LearningModeSelectFragment extends Fragment {

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_learning_mode_select, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    setupClickListeners(view);
    startSlotMachineAnimations(view);
    startCardAnimations(view);
    setupGreeting(view);
  }

  private void setupGreeting(View view) {
    TextView tvGreeting = view.findViewById(R.id.tv_greeting);
    if (tvGreeting == null) return;

    String nickname = "";
    if (getContext() != null) {
      AppSettingsStore appSettingsStore =
          new AppSettingsStore(getContext().getApplicationContext());
      nickname = appSettingsStore.getSettings().getUserNickname();
    }

    if (nickname.isEmpty()) {
      FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
      if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
        nickname = user.getDisplayName();
      } else {
        nickname = "학습자";
      }
    }

    tvGreeting.setText(getString(R.string.greeting_format, nickname));
  }

  private void startCardAnimations(View view) {
    View[] cards = {
      view.findViewById(R.id.card_script_mode),
      view.findViewById(R.id.card_free_mode),
      view.findViewById(R.id.card_mode_speaking),
      view.findViewById(R.id.card_mode)
    };

    long baseDelay = 0; // Start simultaneously with slot machine animations

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

  private void startSlotMachineAnimations(View view) {
    SlotMachineTextView tvStreak = view.findViewById(R.id.tv_streak_info);
    SlotMachineTextView tvStudyTime = view.findViewById(R.id.tv_study_time);
    SlotMachineTextView tvPoints = view.findViewById(R.id.tv_points);

    tvStreak.animateValue(3, "일째 열공 중 🔥", 800, 0);
    tvStudyTime.animateValue(15, "분", 800, 200);
    tvPoints.animateValue(150, "XP", 1000, 400);
  }

  private void setupClickListeners(View view) {
    view.findViewById(R.id.card_script_mode)
        .setOnClickListener(
            v -> {
              Navigation.findNavController(v)
                  .navigate(R.id.action_studyModeSelectFragment_to_scriptSelectFragment);
            });

    view.findViewById(R.id.card_free_mode)
        .setOnClickListener(
            v -> Toast.makeText(getContext(), "AI 자유 대화 모드 진입", Toast.LENGTH_SHORT).show());
  }
}
