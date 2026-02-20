package com.example.test.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.example.test.R;
import com.example.test.widget.SlotMachineTextView;

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
