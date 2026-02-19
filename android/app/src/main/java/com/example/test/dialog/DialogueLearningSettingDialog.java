package com.example.test.dialog;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.DialogFragment;
import com.example.test.BuildConfig;
import com.example.test.R;
import com.example.test.settings.AppSettings;
import com.example.test.settings.AppSettingsStore;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class DialogueLearningSettingDialog extends DialogFragment {
  private static final String TAG = "JOB_J-20260216-005";

  @Nullable private AppSettingsStore appSettingsStore;
  @Nullable private ArrayAdapter<String> localePresetAdapter;

  private boolean bindingState;

  private SwitchMaterial switchMuteAll;
  private SeekBar seekTtsRate;
  private TextView tvTtsRateValue;
  private Spinner spinnerTtsLocalePreset;
  private EditText etTtsLocaleCustom;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.dialog_dialogue_learning_setting, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    appSettingsStore = new AppSettingsStore(requireContext().getApplicationContext());

    bindViews(view);
    setupAdapter();
    setupListeners();
    renderSettings();

    AppCompatButton btnClose = view.findViewById(R.id.btn_close);
    btnClose.setOnClickListener(
        v -> {
          logDebug("settings dialog close clicked");
          dismiss();
        });
  }

  @Override
  public void onStart() {
    super.onStart();
    if (getDialog() != null && getDialog().getWindow() != null) {
      getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
      int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
      getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);
    logDebug("settings dialog dismissed");
  }

  private void bindViews(@NonNull View view) {
    switchMuteAll = view.findViewById(R.id.switch_mute_all);
    seekTtsRate = view.findViewById(R.id.seek_tts_rate);
    tvTtsRateValue = view.findViewById(R.id.tv_tts_rate_value);
    spinnerTtsLocalePreset = view.findViewById(R.id.spinner_tts_locale_preset);
    etTtsLocaleCustom = view.findViewById(R.id.et_tts_locale_custom);
  }

  private void setupAdapter() {
    localePresetAdapter =
        new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            getResources().getStringArray(R.array.settings_tts_locale_presets));
    localePresetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinnerTtsLocalePreset.setAdapter(localePresetAdapter);
  }

  private void setupListeners() {
    switchMuteAll.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          if (bindingState) {
            return;
          }
          AppSettingsStore store = appSettingsStore;
          if (store == null) {
            return;
          }
          store.setMuteAllPlayback(isChecked);
          logDebug("Saved mute setting: " + isChecked);
        });

    seekTtsRate.setMax(100);
    seekTtsRate.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            float speechRate = progressToSpeechRate(progress);
            tvTtsRateValue.setText(getString(R.string.settings_tts_rate_format, speechRate));
            if (!fromUser || bindingState) {
              return;
            }
            AppSettingsStore store = appSettingsStore;
            if (store != null) {
              store.setTtsSpeechRate(speechRate);
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            logDebug("Saved TTS speech rate.");
          }
        });

    spinnerTtsLocalePreset.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (bindingState || localePresetAdapter == null) {
              return;
            }
            String selected = localePresetAdapter.getItem(position);
            if (selected == null
                || selected.equals(getString(R.string.settings_tts_locale_custom_item))) {
              return;
            }
            etTtsLocaleCustom.setText(selected);
            saveLocale(selected);
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });

    etTtsLocaleCustom.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            if (bindingState) {
              return;
            }
            saveLocale(s == null ? "" : s.toString());
          }
        });
  }

  private void renderSettings() {
    AppSettingsStore store = appSettingsStore;
    if (store == null) {
      return;
    }
    AppSettings settings = store.getSettings();
    bindingState = true;
    switchMuteAll.setChecked(settings.isMuteAllPlayback());

    int speechRateProgress = speechRateToProgress(settings.getTtsSpeechRate());
    seekTtsRate.setProgress(speechRateProgress);
    tvTtsRateValue.setText(
        getString(R.string.settings_tts_rate_format, settings.getTtsSpeechRate()));

    etTtsLocaleCustom.setText(settings.getTtsLocaleTag());
    setLocaleSpinnerSelection(settings.getTtsLocaleTag());
    bindingState = false;
  }

  private void saveLocale(@Nullable String localeTag) {
    AppSettingsStore store = appSettingsStore;
    if (store == null) {
      return;
    }
    store.setTtsLocaleTag(localeTag);
    logDebug("Saved TTS locale.");
  }

  private void setLocaleSpinnerSelection(@NonNull String localeTag) {
    if (localePresetAdapter == null) {
      return;
    }
    int index = localePresetAdapter.getPosition(localeTag);
    if (index < 0) {
      index = 0;
    }
    spinnerTtsLocalePreset.setSelection(index);
  }

  private int speechRateToProgress(float speechRate) {
    float clamped = Math.max(0.5f, Math.min(speechRate, 1.5f));
    return Math.round((clamped - 0.5f) * 100f);
  }

  private float progressToSpeechRate(int progress) {
    return 0.5f + (Math.max(0, Math.min(progress, 100)) / 100f);
  }

  private void logDebug(@NonNull String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }
}
