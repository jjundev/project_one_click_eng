package com.example.test.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.test.BuildConfig;
import com.example.test.R;
import com.example.test.settings.AppSettings;
import com.example.test.settings.AppSettingsStore;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SettingFragment extends Fragment {
  private static final String TAG = "JOB_J-20260216-003";
  private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
  private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

  @Nullable
  private AppSettingsStore appSettingsStore;
  @NonNull
  private final OkHttpClient apiTestClient = new OkHttpClient();
  @NonNull
  private final Gson gson = new Gson();

  private boolean bindingState;

  private EditText etApiKeyOverride;
  private Spinner spinnerModelSentence;
  private Spinner spinnerModelSpeaking;
  private Spinner spinnerModelScript;
  private Spinner spinnerModelSummary;
  private Spinner spinnerModelExtra;
  private Button btnApiTest;
  private TextView tvApiTestResult;
  private TextView tvAppVersion;
  private TextView tvModelReset;

  private TextView tvLabelSentence;
  private TextView tvLabelSpeaking;
  private TextView tvLabelScript;
  private TextView tvLabelSummary;
  private TextView tvLabelExtra;

  @Nullable
  private ArrayAdapter<String> modelAdapter;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_setting, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    appSettingsStore = new AppSettingsStore(requireContext().getApplicationContext());
    bindViews(view);
    setupAdapters();
    setupListeners();
    renderSettings();
    showApiTestIdle();
  }

  private void bindViews(@NonNull View view) {
    etApiKeyOverride = view.findViewById(R.id.et_api_key_override);
    spinnerModelSentence = view.findViewById(R.id.spinner_model_sentence);
    spinnerModelSpeaking = view.findViewById(R.id.spinner_model_speaking);
    spinnerModelScript = view.findViewById(R.id.spinner_model_script);
    spinnerModelSummary = view.findViewById(R.id.spinner_model_summary);
    spinnerModelExtra = view.findViewById(R.id.spinner_model_extra);
    btnApiTest = view.findViewById(R.id.btn_api_test);
    tvApiTestResult = view.findViewById(R.id.tv_api_test_result);
    tvAppVersion = view.findViewById(R.id.tv_app_version);
    tvModelReset = view.findViewById(R.id.tv_model_reset);

    tvLabelSentence = view.findViewById(R.id.tv_label_sentence);
    tvLabelSpeaking = view.findViewById(R.id.tv_label_speaking);
    tvLabelScript = view.findViewById(R.id.tv_label_script);
    tvLabelSummary = view.findViewById(R.id.tv_label_summary);
    tvLabelExtra = view.findViewById(R.id.tv_label_extra);

    if (tvAppVersion != null) {
      tvAppVersion.setText(BuildConfig.VERSION_NAME);
    }
  }

  private void setupAdapters() {
    modelAdapter = new ArrayAdapter<>(
        requireContext(),
        android.R.layout.simple_spinner_item,
        getResources().getStringArray(R.array.settings_llm_model_presets));
    modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinnerModelSentence.setAdapter(modelAdapter);
    spinnerModelSpeaking.setAdapter(modelAdapter);
    spinnerModelScript.setAdapter(modelAdapter);
    spinnerModelSummary.setAdapter(modelAdapter);
    spinnerModelExtra.setAdapter(modelAdapter);
  }

  private void setupListeners() {
    etApiKeyOverride.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {
          }

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
          }

          @Override
          public void afterTextChanged(Editable s) {
            if (bindingState) {
              return;
            }
            AppSettingsStore store = appSettingsStore;
            if (store == null) {
              return;
            }
            store.setLlmApiKeyOverride(s == null ? "" : s.toString());
            logDebug("Saved API key override (masked).");
          }
        });

    bindModelSpinner(
        spinnerModelSentence, selected -> saveModelSelection(selected, ModelTarget.SENTENCE));
    bindModelSpinner(
        spinnerModelSpeaking, selected -> saveModelSelection(selected, ModelTarget.SPEAKING));
    bindModelSpinner(
        spinnerModelScript, selected -> saveModelSelection(selected, ModelTarget.SCRIPT));
    bindModelSpinner(
        spinnerModelSummary, selected -> saveModelSelection(selected, ModelTarget.SUMMARY));
    bindModelSpinner(
        spinnerModelExtra, selected -> saveModelSelection(selected, ModelTarget.EXTRA));

    if (tvModelReset != null) {
      tvModelReset.setOnClickListener(v -> resetToDefaults());
    }

    btnApiTest.setOnClickListener(v -> runApiConnectionTest());
  }

  private void bindModelSpinner(
      @NonNull Spinner spinner, @NonNull OnModelSelected onModelSelected) {
    spinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (bindingState || modelAdapter == null) {
              return;
            }
            String selected = modelAdapter.getItem(position);
            onModelSelected.onSelected(selected == null ? "" : selected);
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {
          }
        });
  }

  private void saveModelSelection(@Nullable String selectedModel, @NonNull ModelTarget target) {
    AppSettingsStore store = appSettingsStore;
    if (store == null) {
      return;
    }
    switch (target) {
      case SENTENCE:
        store.setLlmModelSentence(selectedModel);
        updateModelIndicatorHelper(
            selectedModel, AppSettings.DEFAULT_MODEL_SENTENCE, tvLabelSentence);
        break;
      case SPEAKING:
        store.setLlmModelSpeaking(selectedModel);
        updateModelIndicatorHelper(
            selectedModel, AppSettings.DEFAULT_MODEL_SPEAKING, tvLabelSpeaking);
        break;
      case SCRIPT:
        store.setLlmModelScript(selectedModel);
        updateModelIndicatorHelper(selectedModel, AppSettings.DEFAULT_MODEL_SCRIPT, tvLabelScript);
        break;
      case SUMMARY:
        store.setLlmModelSummary(selectedModel);
        updateModelIndicatorHelper(
            selectedModel, AppSettings.DEFAULT_MODEL_SUMMARY, tvLabelSummary);
        break;
      case EXTRA:
        store.setLlmModelExtra(selectedModel);
        updateModelIndicatorHelper(selectedModel, AppSettings.DEFAULT_MODEL_EXTRA, tvLabelExtra);
        break;
      default:
        return;
    }
    logDebug("Saved model selection target=" + target.name());
  }

  private void updateModelIndicatorHelper(
      @Nullable String currentModel,
      @NonNull String defaultModel,
      @Nullable TextView labelTextView) {
    if (labelTextView == null) {
      return;
    }
    boolean isChanged = currentModel != null && !currentModel.equals(defaultModel);
    if (isChanged) {
      labelTextView.setCompoundDrawablesWithIntrinsicBounds(
          0, 0, R.drawable.ic_indicator_changed, 0);
    } else {
      labelTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    }
  }

  private void resetToDefaults() {
    AppSettingsStore store = appSettingsStore;
    if (store == null) {
      return;
    }

    // 1. Reset values in store
    store.setLlmModelSentence(AppSettings.DEFAULT_MODEL_SENTENCE);
    store.setLlmModelSpeaking(AppSettings.DEFAULT_MODEL_SPEAKING);
    store.setLlmModelScript(AppSettings.DEFAULT_MODEL_SCRIPT);
    store.setLlmModelSummary(AppSettings.DEFAULT_MODEL_SUMMARY);
    store.setLlmModelExtra(AppSettings.DEFAULT_MODEL_EXTRA);

    // 2. Update UI
    if (modelAdapter != null) {
      setSpinnerSelection(spinnerModelSentence, AppSettings.DEFAULT_MODEL_SENTENCE, modelAdapter);
      setSpinnerSelection(spinnerModelSpeaking, AppSettings.DEFAULT_MODEL_SPEAKING, modelAdapter);
      setSpinnerSelection(spinnerModelScript, AppSettings.DEFAULT_MODEL_SCRIPT, modelAdapter);
      setSpinnerSelection(spinnerModelSummary, AppSettings.DEFAULT_MODEL_SUMMARY, modelAdapter);
      setSpinnerSelection(spinnerModelExtra, AppSettings.DEFAULT_MODEL_EXTRA, modelAdapter);

      // Reset indicators
      updateModelIndicatorHelper(
          AppSettings.DEFAULT_MODEL_SENTENCE, AppSettings.DEFAULT_MODEL_SENTENCE, tvLabelSentence);
      updateModelIndicatorHelper(
          AppSettings.DEFAULT_MODEL_SPEAKING, AppSettings.DEFAULT_MODEL_SPEAKING, tvLabelSpeaking);
      updateModelIndicatorHelper(
          AppSettings.DEFAULT_MODEL_SCRIPT, AppSettings.DEFAULT_MODEL_SCRIPT, tvLabelScript);
      updateModelIndicatorHelper(
          AppSettings.DEFAULT_MODEL_SUMMARY, AppSettings.DEFAULT_MODEL_SUMMARY, tvLabelSummary);
      updateModelIndicatorHelper(
          AppSettings.DEFAULT_MODEL_EXTRA, AppSettings.DEFAULT_MODEL_EXTRA, tvLabelExtra);
    }

    // 3. Show feedback
    android.widget.Toast.makeText(
        requireContext(), "모든 모델 설정이 초기화되었습니다.", android.widget.Toast.LENGTH_SHORT)
        .show();
  }

  private void renderSettings() {
    AppSettingsStore store = appSettingsStore;
    if (store == null) {
      return;
    }
    AppSettings settings = store.getSettings();
    bindingState = true;
    etApiKeyOverride.setText(settings.getLlmApiKeyOverride());

    setSpinnerSelection(spinnerModelSentence, settings.getLlmModelSentence(), modelAdapter);
    setSpinnerSelection(spinnerModelSpeaking, settings.getLlmModelSpeaking(), modelAdapter);
    setSpinnerSelection(spinnerModelScript, settings.getLlmModelScript(), modelAdapter);
    setSpinnerSelection(spinnerModelSummary, settings.getLlmModelSummary(), modelAdapter);
    setSpinnerSelection(spinnerModelExtra, settings.getLlmModelExtra(), modelAdapter);

    updateModelIndicatorHelper(
        settings.getLlmModelSentence(), AppSettings.DEFAULT_MODEL_SENTENCE, tvLabelSentence);
    updateModelIndicatorHelper(
        settings.getLlmModelSpeaking(), AppSettings.DEFAULT_MODEL_SPEAKING, tvLabelSpeaking);
    updateModelIndicatorHelper(
        settings.getLlmModelScript(), AppSettings.DEFAULT_MODEL_SCRIPT, tvLabelScript);
    updateModelIndicatorHelper(
        settings.getLlmModelSummary(), AppSettings.DEFAULT_MODEL_SUMMARY, tvLabelSummary);
    updateModelIndicatorHelper(
        settings.getLlmModelExtra(), AppSettings.DEFAULT_MODEL_EXTRA, tvLabelExtra);

    bindingState = false;
  }

  private void setSpinnerSelection(
      @NonNull Spinner spinner, @NonNull String value, @Nullable ArrayAdapter<String> adapter) {
    if (adapter == null) {
      return;
    }
    int index = adapter.getPosition(value);
    if (index < 0) {
      index = 0;
    }
    spinner.setSelection(index);
  }

  private void runApiConnectionTest() {
    AppSettingsStore store = appSettingsStore;
    if (store == null) {
      return;
    }
    AppSettings settings = store.getSettings();
    String apiKey = settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY);
    if (apiKey.isEmpty()) {
      showApiTestResult(false, getString(R.string.settings_api_test_missing_key));
      return;
    }
    String modelName = settings.getLlmModelSentence();
    setApiTestInProgress(true);
    new Thread(
        () -> {
          boolean success;
          String resultMessage;
          try {
            JsonObject requestBody = new JsonObject();
            JsonArray contents = new JsonArray();
            JsonObject userContent = new JsonObject();
            userContent.addProperty("role", "user");
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", "ping");
            parts.add(part);
            userContent.add("parts", parts);
            contents.add(userContent);
            requestBody.add("contents", contents);
            JsonObject generationConfig = new JsonObject();
            generationConfig.addProperty("maxOutputTokens", 8);
            requestBody.add("generationConfig", generationConfig);

            String requestUrl = GEMINI_BASE_URL + modelName + ":generateContent?key=" + apiKey;
            Request request = new Request.Builder()
                .url(requestUrl)
                .post(RequestBody.create(gson.toJson(requestBody), JSON_MEDIA_TYPE))
                .build();

            try (Response response = apiTestClient.newCall(request).execute()) {
              success = response.isSuccessful();
              if (success) {
                resultMessage = getString(R.string.settings_api_test_success);
              } else {
                resultMessage = getString(R.string.settings_api_test_fail_code, response.code());
              }
            }
          } catch (Exception e) {
            success = false;
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
              message = "unknown";
            }
            resultMessage = getString(R.string.settings_api_test_fail_message, message);
          }

          boolean finalSuccess = success;
          String finalMessage = resultMessage;
          if (isAdded()) {
            requireActivity()
                .runOnUiThread(
                    () -> {
                      setApiTestInProgress(false);
                      showApiTestResult(finalSuccess, finalMessage);
                    });
          }
        })
        .start();
  }

  private void showApiTestIdle() {
    tvApiTestResult.setText(R.string.settings_api_test_idle);
    tvApiTestResult.setTextColor(Color.parseColor("#666666"));
  }

  private void setApiTestInProgress(boolean running) {
    btnApiTest.setEnabled(!running);
    if (running) {
      tvApiTestResult.setText(R.string.settings_api_test_running);
      tvApiTestResult.setTextColor(Color.parseColor("#444444"));
    }
  }

  private void showApiTestResult(boolean success, @NonNull String message) {
    tvApiTestResult.setText(message);
    tvApiTestResult.setTextColor(
        success ? Color.parseColor("#1B5E20") : Color.parseColor("#B71C1C"));
    logDebug("API test result: " + (success ? "success" : "failure"));
  }

  private void logDebug(@NonNull String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }

  private interface OnModelSelected {
    void onSelected(@Nullable String selectedModel);
  }

  private enum ModelTarget {
    SENTENCE,
    SPEAKING,
    SCRIPT,
    SUMMARY,
    EXTRA
  }
}
