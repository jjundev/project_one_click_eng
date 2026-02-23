package com.jjundev.oneclickeng.fragment.history;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.jjundev.oneclickeng.R;

public class HistoryQuizConfigDialog extends DialogFragment {

  public static final String REQUEST_KEY = "history_quiz_config_request";
  public static final String BUNDLE_KEY_PERIOD_BUCKET = "bundle_key_period_bucket";
  public static final String BUNDLE_KEY_QUESTION_COUNT = "bundle_key_question_count";

  public static final int PERIOD_1W = 0;
  public static final int PERIOD_2W = 1;
  public static final int PERIOD_3W = 2;
  public static final int PERIOD_OLDER = 3;

  @Nullable private View loadingContainer;
  @Nullable private View configContainer;
  @Nullable private Button btnCancel;
  @Nullable private Button btnStart;

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

    configContainer = view.findViewById(R.id.layout_quiz_config_content);
    loadingContainer = view.findViewById(R.id.layout_quiz_loading);
    btnCancel = (Button) view.findViewById(R.id.btn_quiz_cancel);
    btnStart = (Button) view.findViewById(R.id.btn_quiz_start);
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

    if (btnCancel != null) {
      btnCancel.setOnClickListener(v -> dismiss());
    }

    if (btnStart != null) {
      btnStart.setOnClickListener(
          v -> {
            if (isLoadingState()) {
              return;
            }

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

            setLoadingState(true);
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
          });
    }

    setLoadingState(false);
  }

  @Override
  public void onStart() {
    super.onStart();
    if (getDialog() != null && getDialog().getWindow() != null) {
      int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
      getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
      getDialog().setCancelable(!isLoadingState());
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    configContainer = null;
    loadingContainer = null;
    btnCancel = null;
    btnStart = null;
  }

  public void setLoadingState(boolean loading) {
    View root = getView();
    if (configContainer == null && root != null) {
      configContainer = root.findViewById(R.id.layout_quiz_config_content);
    }
    if (loadingContainer == null && root != null) {
      loadingContainer = root.findViewById(R.id.layout_quiz_loading);
    }
    if (btnCancel == null && root != null) {
      View view = root.findViewById(R.id.btn_quiz_cancel);
      btnCancel = view instanceof Button ? (Button) view : null;
    }
    if (btnStart == null && root != null) {
      View view = root.findViewById(R.id.btn_quiz_start);
      btnStart = view instanceof Button ? (Button) view : null;
    }

    if (configContainer != null) {
      configContainer.setVisibility(loading ? View.GONE : View.VISIBLE);
    }
    if (loadingContainer != null) {
      loadingContainer.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    if (root != null) {
      setInputsEnabled(root, !loading);
    }
    setButtonsEnabled(!loading);

    if (getDialog() != null) {
      getDialog().setCancelable(!loading);
    }
  }

  public boolean isLoadingState() {
    return loadingContainer != null && loadingContainer.getVisibility() == View.VISIBLE;
  }

  private void setButtonsEnabled(boolean enabled) {
    if (btnCancel != null) {
      btnCancel.setEnabled(enabled);
    }
    if (btnStart != null) {
      btnStart.setEnabled(enabled);
    }
  }

  private void setInputsEnabled(@NonNull View root, boolean enabled) {
    RadioGroup rgPeriod = root.findViewById(R.id.rg_quiz_period);
    SeekBar sbQuestionCount = root.findViewById(R.id.sb_question_count);
    if (rgPeriod != null) {
      rgPeriod.setEnabled(enabled);
      for (int i = 0; i < rgPeriod.getChildCount(); i++) {
        View child = rgPeriod.getChildAt(i);
        if (child != null) {
          child.setEnabled(enabled);
        }
      }
    }
    if (sbQuestionCount != null) {
      sbQuestionCount.setEnabled(enabled);
    }
  }
}
