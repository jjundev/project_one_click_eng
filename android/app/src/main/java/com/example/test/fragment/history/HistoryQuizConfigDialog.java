package com.example.test.fragment.history;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.example.test.R;

public class HistoryQuizConfigDialog extends DialogFragment {

  public static final String REQUEST_KEY = "history_quiz_config_request";
  public static final String BUNDLE_KEY_PERIOD_BUCKET = "bundle_key_period_bucket";
  public static final String BUNDLE_KEY_QUESTION_COUNT = "bundle_key_question_count";

  public static final int PERIOD_1W = 0;
  public static final int PERIOD_2W = 1;
  public static final int PERIOD_3W = 2;
  public static final int PERIOD_OLDER = 3;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    if (getDialog() != null && getDialog().getWindow() != null) {
      getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
    }
    return inflater.inflate(R.layout.dialog_history_quiz_config, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    RadioGroup rgPeriod = view.findViewById(R.id.rg_quiz_period);
    SeekBar sbQuestionCount = view.findViewById(R.id.sb_question_count);
    TextView tvQuizCountValue = view.findViewById(R.id.tv_quiz_count_value);

    sbQuestionCount.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            int count = progress + 1;
            tvQuizCountValue.setText(count + "문제");
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    view.findViewById(R.id.btn_quiz_cancel).setOnClickListener(v -> dismiss());

    view.findViewById(R.id.btn_quiz_start)
        .setOnClickListener(
            v -> {
              int selectedPeriodId = rgPeriod.getCheckedRadioButtonId();
              int periodBucket = PERIOD_1W;

              if (selectedPeriodId == R.id.rb_period_2w) {
                periodBucket = PERIOD_2W;
              } else if (selectedPeriodId == R.id.rb_period_3w) {
                periodBucket = PERIOD_3W;
              } else if (selectedPeriodId == R.id.rb_period_older) {
                periodBucket = PERIOD_OLDER;
              }

              int questionCount = sbQuestionCount.getProgress() + 1;

              Bundle result = new Bundle();
              result.putInt(BUNDLE_KEY_PERIOD_BUCKET, periodBucket);
              result.putInt(BUNDLE_KEY_QUESTION_COUNT, questionCount);

              getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
              dismiss();
            });
  }

  @Override
  public void onStart() {
    super.onStart();
    if (getDialog() != null && getDialog().getWindow() != null) {
      int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
      getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
  }
}
