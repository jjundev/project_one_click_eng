package com.example.test.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.DialogFragment;
import com.example.test.R;

public class DialogueGenerateDialog extends DialogFragment {
  private View layoutInputForm;
  private View layoutLoading;
  private EditText etTopic;
  private AutoCompleteTextView spinnerLevel;
  private TextView tvLevelDescription;
  private android.widget.SeekBar sbLength;
  private TextView tvLengthValue;
  private AppCompatButton btnConfirm;
  private OnScriptParamsSelectedListener listener;

  public interface OnScriptParamsSelectedListener {
    void onScriptParamsSelected(
        String level, String topic, String format, int length, DialogueGenerateDialog dialog);
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (getParentFragment() instanceof OnScriptParamsSelectedListener) {
      listener = (OnScriptParamsSelectedListener) getParentFragment();
    } else if (context instanceof OnScriptParamsSelectedListener) {
      listener = (OnScriptParamsSelectedListener) context;
    } else {
      throw new RuntimeException(
          context.toString() + " must implement OnScriptParamsSelectedListener");
    }
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.dialog_dialogue_generate, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    layoutInputForm = view.findViewById(R.id.layout_input_form);
    layoutLoading = view.findViewById(R.id.layout_loading);
    etTopic = view.findViewById(R.id.et_topic_input);
    spinnerLevel = view.findViewById(R.id.spinner_level);
    tvLevelDescription = view.findViewById(R.id.tv_level_description);
    sbLength = view.findViewById(R.id.sb_length);
    tvLengthValue = view.findViewById(R.id.tv_length_value);
    btnConfirm = view.findViewById(R.id.btn_confirm_generate);

    setupDropdowns();
    setupSeekBar();

    btnConfirm.setOnClickListener(v -> generateScript());
  }

  @Override
  public void onStart() {
    super.onStart();
    if (getDialog() != null && getDialog().getWindow() != null) {
      // Set transparent background to show rounded corners
      getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);

      // Set dialog width to 90% of screen width
      int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
      getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
  }

  private void setupDropdowns() {
    String[] levels = getResources().getStringArray(R.array.levels);
    ArrayAdapter<String> levelAdapter =
        new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, levels);
    spinnerLevel.setAdapter(levelAdapter);

    spinnerLevel.setOnItemClickListener(
        (parent, view, position, id) -> {
          String selectedLevel = (String) parent.getItemAtPosition(position);
          updateLevelDescription(selectedLevel);
        });

    // Initialize description
    updateLevelDescription(spinnerLevel.getText().toString());
  }

  private void updateLevelDescription(String level) {
    String description;
    switch (level) {
      case "Beginner":
        description = "기초적인 단어와 간단한 문장으로 대화합니다.";
        break;
      case "Elementary":
        description = "익숙한 주제에 대해 짧고 쉬운 표현을 사용합니다.";
        break;
      case "Intermediate":
        description = "일상적인 대화를 자연스럽게 나눌 수 있는 수준입니다.";
        break;
      case "Upper-Intermediate":
        description = "다양한 주제에 대해 복잡한 문장을 구사합니다.";
        break;
      case "Advanced":
        description = "전문적인 주제나 추상적인 개념도 유창하게 표현합니다.";
        break;
      default:
        description = "";
        break;
    }
    tvLevelDescription.setText(description);
  }

  private void setupSeekBar() {
    sbLength.setOnSeekBarChangeListener(
        new android.widget.SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(
              android.widget.SeekBar seekBar, int progress, boolean fromUser) {
            int length = progress + 2;
            tvLengthValue.setText(length + "줄");
          }

          @Override
          public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

    // Initialize text
    tvLengthValue.setText((sbLength.getProgress() + 2) + "줄");
  }

  public void showLoading(boolean loading) {
    if (loading) {
      layoutInputForm.setVisibility(View.GONE);
      layoutLoading.setVisibility(View.VISIBLE);
      setCancelable(false);
    } else {
      layoutInputForm.setVisibility(View.VISIBLE);
      layoutLoading.setVisibility(View.GONE);
      setCancelable(true);
    }
  }

  private void hideKeyboard() {
    View view = getView();
    if (view != null) {
      InputMethodManager imm =
          (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null) {
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
      }
    }
  }

  private void generateScript() {
    String topic = etTopic.getText().toString().trim();
    if (topic.isEmpty()) {
      Toast.makeText(getContext(), "주제를 입력해주세요", Toast.LENGTH_SHORT).show();
      return;
    }

    hideKeyboard();

    String level = spinnerLevel.getText().toString().toLowerCase().replace(" ", "-");
    String format = "dialogue";
    int length = sbLength.getProgress() + 2;

    if (listener != null) {
      showLoading(true);
      listener.onScriptParamsSelected(level, topic, format, length, this);
    }
  }
}
