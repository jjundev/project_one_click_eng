package com.jjundev.oneclickeng.fragment;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.game.refiner.RefinerConstraintValidator;
import com.jjundev.oneclickeng.game.refiner.RefinerGameViewModel;
import com.jjundev.oneclickeng.game.refiner.RefinerGameViewModelFactory;
import com.jjundev.oneclickeng.game.refiner.RefinerStatsStore;
import com.jjundev.oneclickeng.game.refiner.model.RefinerConstraints;
import com.jjundev.oneclickeng.game.refiner.model.RefinerDifficulty;
import com.jjundev.oneclickeng.game.refiner.model.RefinerEvaluation;
import com.jjundev.oneclickeng.game.refiner.model.RefinerLevelExample;
import com.jjundev.oneclickeng.game.refiner.model.RefinerQuestion;
import com.jjundev.oneclickeng.game.refiner.model.RefinerRoundResult;
import com.jjundev.oneclickeng.game.refiner.model.RefinerWordLimit;
import com.jjundev.oneclickeng.game.refiner.model.RefinerWordLimitMode;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.SpeakingFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.MicPermissionCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.SpeakingSceneCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISpeakingFeedbackManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SpeakingAnalysisResult;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.BottomSheetSceneRenderer;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningBottomSheetActionRouter;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningBottomSheetController;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningBottomSheetRenderer;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningChatRenderer;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningControlsRenderer;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.RecordingUiController;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import com.jjundev.oneclickeng.tool.AudioRecorder;
import com.jjundev.oneclickeng.view.WaveformView;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RefinerInputV1Fragment extends Fragment {
  private static final String STATE_HAS_SELECTED_DIFFICULTY = "state_refiner_has_selected_difficulty";
  private static final String STATE_SELECTED_DIFFICULTY = "state_refiner_selected_difficulty";
  private static final String STATE_CACHED_INPUT_TEXT = "state_refiner_cached_input_text";
  private static final String STATE_ACTIVE_SCENE = "state_refiner_active_scene";
  private static final int MAX_RECORDING_SECONDS = 60;

  private enum RefinerInputScene {
    DEFAULT_INPUT,
    BEFORE_SPEAKING,
    WHILE_SPEAKING,
    FEEDBACK,
    COMPLETED
  }

  @NonNull private final Handler mainHandler = new Handler(Looper.getMainLooper());
  @NonNull
  private final RecordingUiController recordingUiController =
      new RecordingUiController(MAX_RECORDING_SECONDS);

  @Nullable private RefinerGameViewModel viewModel;
  @Nullable private AppSettingsStore appSettingsStore;
  @Nullable private RefinerQuestion currentQuestion;
  @Nullable private RefinerGameViewModel.GameUiState latestState;
  @Nullable private String currentQuestionSignature;

  @Nullable private ScrollView topScrollArea;
  @Nullable private View layoutRefinerContent;
  @Nullable private View layoutRefinerLoading;
  @Nullable private View layoutRefinerError;
  @Nullable private TextView tvRefinerSourceSentence;
  @Nullable private TextView tvRefinerContext;
  @Nullable private TextView tvRefinerConstraints;
  @Nullable private TextView tvRefinerWordCount;
  @Nullable private TextView tvRefinerLoadingMessage;
  @Nullable private TextView tvRefinerErrorMessage;
  @Nullable private View btnRefinerRetry;

  @Nullable private View bottomSheet;
  @Nullable private FrameLayout bottomSheetContentContainer;
  @Nullable private LearningBottomSheetRenderer bottomSheetRenderer;
  @Nullable private LearningBottomSheetController bottomSheetController;
  @Nullable private BottomSheetSceneRenderer bottomSheetSceneRenderer;
  @Nullable private LearningControlsRenderer controlsRenderer;
  @Nullable private LearningChatRenderer chatRenderer;
  @Nullable private View.OnLayoutChangeListener bottomSheetLayoutChangeListener;

  @Nullable private EditText currentInputEditText;
  @Nullable private ImageButton currentSendButton;
  @Nullable private ImageButton currentMicButton;
  @Nullable private TextWatcher currentInputTextWatcher;
  @Nullable private RefinerConstraintValidator.ValidationResult latestValidationResult;

  @Nullable private ISpeakingFeedbackManager speakingFeedbackManager;
  @Nullable private SpeakingFlowController speakingFlowController;
  @Nullable private AudioRecorder audioRecorderManager;
  @Nullable private ByteArrayOutputStream audioAccumulator;
  @Nullable private StringBuilder transcriptBuilder;
  @Nullable private MicPermissionCoordinator micPermissionCoordinator;
  @Nullable private SpeakingSceneCoordinator speakingSceneCoordinator;
  @Nullable private ActivityResultLauncher<String> requestPermissionLauncher;
  @Nullable private String pendingSentenceForPermission;

  private boolean speaking;
  private boolean recordingActive;
  private boolean speakingCacheReady;
  private boolean speakingCacheInitializing;
  private boolean hasSelectedDifficulty;
  @Nullable private RefinerDifficulty selectedDifficulty;
  @Nullable private String pendingTranscriptionToApply;
  @Nullable private byte[] lastRecordedAudio;
  @NonNull private String cachedInputText = "";
  @NonNull private RefinerInputScene activeScene = RefinerInputScene.DEFAULT_INPUT;
  private long lastWaveformUpdateMs;
  private int baseScrollPaddingBottom;

  @Nullable private ProgressBar progressRing;
  @Nullable private ProgressBar loadingSpinner;
  @Nullable private TextView tvListeningStatus;
  @Nullable private WaveformView waveformView;
  @Nullable private View ripple1;
  @Nullable private View ripple2;
  @Nullable private View ripple3;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_refiner_input_v1, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    restoreSavedState(savedInstanceState);
    bindViews(view);
    registerMicPermissionLauncher();
    initDependenciesAndVoicePipeline(savedInstanceState);
    initViewModel();
    initBottomSheet(view);
    bindListeners();
    observeViewModel();

    if (hasSelectedDifficulty && selectedDifficulty != null && viewModel != null) {
      viewModel.initialize(selectedDifficulty);
    } else {
      showDifficultyDialog();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (micPermissionCoordinator != null) {
      micPermissionCoordinator.onResume();
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(STATE_HAS_SELECTED_DIFFICULTY, hasSelectedDifficulty);
    if (selectedDifficulty != null) {
      outState.putString(STATE_SELECTED_DIFFICULTY, selectedDifficulty.name());
    }
    outState.putString(STATE_CACHED_INPUT_TEXT, cachedInputText);
    outState.putString(STATE_ACTIVE_SCENE, activeScene.name());
    if (micPermissionCoordinator != null) {
      micPermissionCoordinator.saveTo(outState);
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    if (speakingFlowController != null) {
      speakingFlowController.invalidate();
    }
    stopSpeakingSession();
    if (speakingSceneCoordinator != null) {
      speakingSceneCoordinator.release();
      speakingSceneCoordinator = null;
    }
    if (bottomSheetController != null) {
      bottomSheetController.onDestroy();
      bottomSheetController = null;
    }
    if (controlsRenderer != null) {
      controlsRenderer.stopHandlerCallbacks();
      controlsRenderer = null;
    }
    if (bottomSheet != null && bottomSheetLayoutChangeListener != null) {
      bottomSheet.removeOnLayoutChangeListener(bottomSheetLayoutChangeListener);
    }

    clearDefaultInputBindings();
    recordingUiController.clear();
    safeStopRecording("onDestroyView");
    if (audioAccumulator != null) {
      audioAccumulator.reset();
      audioAccumulator = null;
    }

    speakingFlowController = null;
    speakingFeedbackManager = null;
    audioRecorderManager = null;
    transcriptBuilder = null;
    micPermissionCoordinator = null;
    requestPermissionLauncher = null;
    appSettingsStore = null;
    latestState = null;
    currentQuestion = null;
    currentQuestionSignature = null;
    latestValidationResult = null;
    pendingTranscriptionToApply = null;
    pendingSentenceForPermission = null;
    speaking = false;
    recordingActive = false;
    topScrollArea = null;
    layoutRefinerContent = null;
    layoutRefinerLoading = null;
    layoutRefinerError = null;
    tvRefinerSourceSentence = null;
    tvRefinerContext = null;
    tvRefinerConstraints = null;
    tvRefinerWordCount = null;
    tvRefinerLoadingMessage = null;
    tvRefinerErrorMessage = null;
    btnRefinerRetry = null;
    bottomSheet = null;
    bottomSheetContentContainer = null;
    bottomSheetRenderer = null;
    bottomSheetSceneRenderer = null;
    chatRenderer = null;
    bottomSheetLayoutChangeListener = null;
    progressRing = null;
    loadingSpinner = null;
    tvListeningStatus = null;
    waveformView = null;
    ripple1 = null;
    ripple2 = null;
    ripple3 = null;
  }

  private void restoreSavedState(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      return;
    }
    hasSelectedDifficulty = savedInstanceState.getBoolean(STATE_HAS_SELECTED_DIFFICULTY, false);
    if (savedInstanceState.containsKey(STATE_SELECTED_DIFFICULTY)) {
      selectedDifficulty =
          RefinerDifficulty.fromRaw(
              savedInstanceState.getString(STATE_SELECTED_DIFFICULTY), RefinerDifficulty.EASY);
    }
    cachedInputText = safeString(savedInstanceState.getString(STATE_CACHED_INPUT_TEXT));
    String sceneRaw = savedInstanceState.getString(STATE_ACTIVE_SCENE);
    if (!isBlank(sceneRaw)) {
      try {
        activeScene = RefinerInputScene.valueOf(sceneRaw);
      } catch (IllegalArgumentException ignored) {
        activeScene = RefinerInputScene.DEFAULT_INPUT;
      }
    }
  }

  private void bindViews(@NonNull View root) {
    topScrollArea = root.findViewById(R.id.top_scroll_area);
    layoutRefinerContent = root.findViewById(R.id.layout_refiner_content);
    layoutRefinerLoading = root.findViewById(R.id.layout_refiner_loading);
    layoutRefinerError = root.findViewById(R.id.layout_refiner_error);
    tvRefinerSourceSentence = root.findViewById(R.id.tv_refiner_source_sentence);
    tvRefinerContext = root.findViewById(R.id.tv_refiner_context);
    tvRefinerConstraints = root.findViewById(R.id.tv_refiner_constraints);
    tvRefinerWordCount = root.findViewById(R.id.tv_refiner_word_count);
    tvRefinerLoadingMessage = root.findViewById(R.id.tv_refiner_loading_message);
    tvRefinerErrorMessage = root.findViewById(R.id.tv_refiner_error_message);
    btnRefinerRetry = root.findViewById(R.id.btn_refiner_retry);
    bottomSheet = root.findViewById(R.id.bottom_sheet);
    bottomSheetContentContainer = root.findViewById(R.id.bottom_sheet_content_container);
    if (topScrollArea != null) {
      baseScrollPaddingBottom = topScrollArea.getPaddingBottom();
    }
  }

  private void registerMicPermissionLauncher() {
    requestPermissionLauncher =
        registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
              if (micPermissionCoordinator != null) {
                micPermissionCoordinator.onPermissionResult(isGranted);
              }
            });
  }

  private void initDependenciesAndVoicePipeline(@Nullable Bundle savedInstanceState) {
    Context appContext = requireContext().getApplicationContext();
    appSettingsStore = new AppSettingsStore(appContext);
    AppSettings appSettings = appSettingsStore.getSettings();
    String effectiveApiKey = appSettings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY);
    audioRecorderManager = LearningDependencyProvider.provideAudioRecorder();
    audioAccumulator = new ByteArrayOutputStream();
    transcriptBuilder = new StringBuilder();
    speakingFeedbackManager =
        LearningDependencyProvider.provideSpeakingFeedbackManager(
            appContext, effectiveApiKey, appSettings.getLlmModelSpeaking());
    if (speakingFeedbackManager != null) {
      speakingFlowController =
          new SpeakingFlowController(
              new SpeakingFlowController.ManagerSpeakingAnalyzer(speakingFeedbackManager));
    }
    micPermissionCoordinator = new MicPermissionCoordinator(buildMicPermissionCoordinatorHost());
    speakingSceneCoordinator = new SpeakingSceneCoordinator(buildSpeakingSceneCoordinatorHost());
    if (micPermissionCoordinator != null) {
      micPermissionCoordinator.restoreFrom(savedInstanceState);
    }
    warmUpSpeakingCache();
  }

  private void warmUpSpeakingCache() {
    ISpeakingFeedbackManager manager = speakingFeedbackManager;
    if (manager == null) {
      speakingCacheInitializing = false;
      speakingCacheReady = false;
      refreshBottomSheetInputControlsState();
      return;
    }
    speakingCacheInitializing = true;
    speakingCacheReady = false;
    refreshBottomSheetInputControlsState();
    manager.initializeCache(
        new ISpeakingFeedbackManager.InitCallback() {
          @Override
          public void onReady() {
            mainHandler.post(
                () -> {
                  speakingCacheInitializing = false;
                  speakingCacheReady = true;
                  refreshBottomSheetInputControlsState();
                });
          }

          @Override
          public void onError(@NonNull String error) {
            mainHandler.post(
                () -> {
                  speakingCacheInitializing = false;
                  speakingCacheReady = false;
                  refreshBottomSheetInputControlsState();
                  toastShort("음성 인식 초기화에 실패했습니다.");
                });
          }
        });
  }

  private void initViewModel() {
    Context appContext = requireContext().getApplicationContext();
    AppSettings appSettings =
        appSettingsStore == null
            ? new AppSettingsStore(appContext).getSettings()
            : appSettingsStore.getSettings();
    RefinerGameViewModelFactory factory =
        new RefinerGameViewModelFactory(
            LearningDependencyProvider.provideRefinerGenerationManager(
                appContext,
                appSettings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
                appSettings.getLlmModelRefiner()),
            new RefinerStatsStore(appContext));
    viewModel = new ViewModelProvider(this, factory).get(RefinerGameViewModel.class);
  }

  private void initBottomSheet(@NonNull View root) {
    if (bottomSheet == null || bottomSheetContentContainer == null) {
      return;
    }
    bottomSheetRenderer = new LearningBottomSheetRenderer(bottomSheet, bottomSheetContentContainer);
    chatRenderer = new LearningChatRenderer(null, null);
    controlsRenderer = new LearningControlsRenderer();
    bottomSheetController =
        new LearningBottomSheetController(bottomSheetRenderer, chatRenderer, controlsRenderer);
    LearningBottomSheetActionRouter actionRouter =
        new LearningBottomSheetActionRouter(
            buildBottomSheetActionDelegate(), buildBottomSheetLoggerDelegate());
    bottomSheetSceneRenderer = new BottomSheetSceneRenderer(actionRouter);
    bottomSheetController.setup(root, bottomSheet, bottomSheetContentContainer, this::syncTopScrollBottomPadding);
    bottomSheetController.setVisible(false);
    bottomSheetLayoutChangeListener =
        (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
            syncTopScrollBottomPadding();
    bottomSheet.addOnLayoutChangeListener(bottomSheetLayoutChangeListener);
  }

  private void bindListeners() {
    if (btnRefinerRetry != null) {
      btnRefinerRetry.setOnClickListener(
          v -> {
            if (viewModel != null) {
              viewModel.retry();
            }
          });
    }
  }

  private void observeViewModel() {
    if (viewModel != null) {
      viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
    }
  }

  private void showDifficultyDialog() {
    if (!isAdded() || hasSelectedDifficulty) {
      return;
    }
    String[] labels = {
      getString(R.string.refiner_difficulty_easy),
      getString(R.string.refiner_difficulty_normal),
      getString(R.string.refiner_difficulty_hard),
      getString(R.string.refiner_difficulty_expert)
    };
    RefinerDifficulty[] values = {
      RefinerDifficulty.EASY,
      RefinerDifficulty.NORMAL,
      RefinerDifficulty.HARD,
      RefinerDifficulty.EXPERT
    };
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.refiner_difficulty_dialog_title)
        .setCancelable(false)
        .setItems(
            labels,
            (dialog, which) -> {
              hasSelectedDifficulty = true;
              int safeIndex = Math.max(0, Math.min(which, values.length - 1));
              selectedDifficulty = values[safeIndex];
              if (viewModel != null) {
                viewModel.initialize(selectedDifficulty);
              }
            })
        .show();
  }

  private void renderState(@NonNull RefinerGameViewModel.GameUiState state) {
    latestState = state;
    RefinerQuestion stateQuestion = state.getQuestion();
    if (stateQuestion != null) {
      boolean changed = updateCurrentQuestion(stateQuestion);
      if (changed) {
        cachedInputText = "";
        pendingTranscriptionToApply = null;
        latestValidationResult = null;
      }
      bindQuestionHeader(stateQuestion);
    }

    switch (state.getStage()) {
      case LOADING:
        showOnly(layoutRefinerLoading);
        if (tvRefinerLoadingMessage != null) {
          tvRefinerLoadingMessage.setText(
              isBlank(state.getLoadingMessage())
                  ? getString(R.string.refiner_loading_default)
                  : state.getLoadingMessage());
        }
        hideBottomSheet();
        break;
      case ERROR:
        showOnly(layoutRefinerError);
        if (tvRefinerErrorMessage != null) {
          tvRefinerErrorMessage.setText(
              isBlank(state.getErrorMessage())
                  ? getString(R.string.refiner_error_default)
                  : state.getErrorMessage());
        }
        hideBottomSheet();
        break;
      case FEEDBACK:
        showOnly(layoutRefinerContent);
        presentFeedbackScene(state);
        break;
      case ROUND_COMPLETED:
        showOnly(layoutRefinerContent);
        presentCompletedScene(state.getRoundResult());
        break;
      case ANSWERING:
      case EVALUATING:
      default:
        showOnly(layoutRefinerContent);
        renderAnsweringOrEvaluatingScene(state);
        break;
    }
    syncTopScrollBottomPadding();
  }

  private void renderAnsweringOrEvaluatingScene(@NonNull RefinerGameViewModel.GameUiState state) {
    String sentence = resolveSentenceForInputScene();
    if (activeScene == RefinerInputScene.FEEDBACK || activeScene == RefinerInputScene.COMPLETED) {
      presentDefaultInputScene(sentence);
      return;
    }
    if (activeScene == RefinerInputScene.BEFORE_SPEAKING) {
      presentBeforeSpeakingScene(sentence);
      return;
    }
    if (activeScene == RefinerInputScene.WHILE_SPEAKING
        && state.getStage() == RefinerGameViewModel.Stage.ANSWERING) {
      presentWhileSpeakingScene(sentence);
      return;
    }
    presentDefaultInputScene(sentence);
  }

  private boolean updateCurrentQuestion(@NonNull RefinerQuestion question) {
    String signature = question.signature();
    boolean changed = !signature.equals(currentQuestionSignature);
    currentQuestion = question;
    currentQuestionSignature = signature;
    return changed;
  }

  private void bindQuestionHeader(@NonNull RefinerQuestion question) {
    if (tvRefinerSourceSentence != null) {
      tvRefinerSourceSentence.setText(question.getSourceSentence());
    }
    if (tvRefinerContext != null) {
      tvRefinerContext.setText(
          getString(R.string.refiner_context_format, safeString(question.getStyleContext())));
    }
    if (tvRefinerConstraints != null) {
      tvRefinerConstraints.setText(formatConstraintsText(question.getConstraints()));
    }
    updateValidationAndWordCount(cachedInputText, false);
  }

  private void presentDefaultInputScene(@Nullable String sentenceToTranslate) {
    activeScene = RefinerInputScene.DEFAULT_INPUT;
    String safeSentence = safeString(sentenceToTranslate);
    renderBottomSheetScene(
        R.layout.bottom_sheet_content_default,
        content -> {
          clearDefaultInputBindings();
          if (bottomSheetSceneRenderer != null) {
            bottomSheetSceneRenderer.renderDefaultContent(content, safeSentence, null);
          }
          bindDefaultInputViews(content);
          if (pendingTranscriptionToApply != null) {
            cachedInputText = pendingTranscriptionToApply;
            pendingTranscriptionToApply = null;
          }
          applyCachedInputToEditor();
          updateValidationAndWordCount(cachedInputText, true);
          refreshBottomSheetInputControlsState();
        });
  }

  private void bindDefaultInputViews(@NonNull View content) {
    currentInputEditText = content.findViewById(R.id.et_user_input);
    currentSendButton = content.findViewById(R.id.btn_send);
    currentMicButton = content.findViewById(R.id.btn_mic);

    if (currentInputEditText == null) {
      return;
    }
    Object existing = currentInputEditText.getTag(R.id.tv_refiner_constraints);
    if (existing instanceof TextWatcher) {
      currentInputEditText.removeTextChangedListener((TextWatcher) existing);
    }
    TextWatcher watcher =
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            cachedInputText = s == null ? "" : s.toString();
            updateValidationAndWordCount(cachedInputText, true);
          }

          @Override
          public void afterTextChanged(Editable s) {}
        };
    currentInputEditText.setTag(R.id.tv_refiner_constraints, watcher);
    currentInputEditText.addTextChangedListener(watcher);
    currentInputTextWatcher = watcher;
  }

  private void applyCachedInputToEditor() {
    if (currentInputEditText == null) {
      return;
    }
    String current = currentInputEditText.getText() == null ? "" : currentInputEditText.getText().toString();
    if (current.equals(cachedInputText)) {
      return;
    }
    currentInputEditText.setText(cachedInputText);
    if (currentInputEditText.getText() != null) {
      currentInputEditText.setSelection(currentInputEditText.getText().length());
    }
  }

  private void updateValidationAndWordCount(@Nullable String inputText, boolean applyHighlight) {
    String safeInput = safeString(inputText);
    if (currentQuestion == null) {
      latestValidationResult = null;
      if (tvRefinerWordCount != null) {
        tvRefinerWordCount.setText(getString(R.string.refiner_word_count_default));
      }
      refreshBottomSheetInputControlsState();
      return;
    }
    RefinerConstraintValidator.ValidationResult validation =
        RefinerConstraintValidator.validate(currentQuestion.getConstraints(), safeInput);
    latestValidationResult = validation;
    updateWordCountText(currentQuestion.getConstraints(), validation, safeInput);
    if (applyHighlight) {
      applyBannedWordHighlight(validation);
    }
    refreshBottomSheetInputControlsState();
  }

  private void updateWordCountText(
      @NonNull RefinerConstraints constraints,
      @NonNull RefinerConstraintValidator.ValidationResult validation,
      @NonNull String inputText) {
    if (tvRefinerWordCount == null) {
      return;
    }
    int wordCount = validation.getWordCount();
    RefinerWordLimit limit = constraints.getWordLimit();
    if (limit == null) {
      tvRefinerWordCount.setText(getString(R.string.refiner_word_count_plain_format, wordCount));
    } else if (limit.getMode() == RefinerWordLimitMode.EXACT) {
      tvRefinerWordCount.setText(
          getString(R.string.refiner_word_count_exact_format, wordCount, limit.getValue()));
    } else {
      tvRefinerWordCount.setText(
          getString(R.string.refiner_word_count_max_format, wordCount, limit.getValue()));
    }
    boolean warn = !isBlank(inputText) && !validation.isAllConstraintsSatisfied();
    tvRefinerWordCount.setTextColor(
        ContextCompat.getColor(
            requireContext(), warn ? R.color.expression_precise_accent : R.color.grey_600));
  }

  private void applyBannedWordHighlight(@NonNull RefinerConstraintValidator.ValidationResult validation) {
    if (currentInputEditText == null || currentInputEditText.getText() == null) {
      return;
    }
    Editable editable = currentInputEditText.getText();
    ForegroundColorSpan[] fg = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
    for (ForegroundColorSpan span : fg) {
      editable.removeSpan(span);
    }
    BackgroundColorSpan[] bg = editable.getSpans(0, editable.length(), BackgroundColorSpan.class);
    for (BackgroundColorSpan span : bg) {
      editable.removeSpan(span);
    }
    int fgColor = ContextCompat.getColor(requireContext(), R.color.expression_precise_accent);
    int bgColor = ContextCompat.getColor(requireContext(), R.color.expression_precise_after_bg);
    for (RefinerConstraintValidator.TokenRange range : validation.getBannedWordRanges()) {
      int start = Math.max(0, Math.min(range.getStart(), editable.length()));
      int end = Math.max(start, Math.min(range.getEnd(), editable.length()));
      if (start >= end) {
        continue;
      }
      editable.setSpan(new ForegroundColorSpan(fgColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      editable.setSpan(new BackgroundColorSpan(bgColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }

  private void refreshBottomSheetInputControlsState() {
    boolean answering =
        latestState != null && latestState.getStage() == RefinerGameViewModel.Stage.ANSWERING;
    boolean evaluating =
        latestState != null && latestState.getStage() == RefinerGameViewModel.Stage.EVALUATING;
    boolean editable = answering && activeScene == RefinerInputScene.DEFAULT_INPUT;

    if (currentInputEditText != null) {
      currentInputEditText.setEnabled(editable);
    }
    if (currentMicButton != null) {
      boolean micEnabled = editable && speakingCacheReady && !speakingCacheInitializing;
      currentMicButton.setEnabled(micEnabled);
      currentMicButton.setAlpha(micEnabled ? 1f : 0.45f);
    }
    if (currentSendButton != null) {
      String text =
          currentInputEditText == null || currentInputEditText.getText() == null
              ? cachedInputText
              : currentInputEditText.getText().toString();
      boolean sendEnabled =
          editable
              && !isBlank(text)
              && latestValidationResult != null
              && latestValidationResult.isAllConstraintsSatisfied()
              && !evaluating;
      currentSendButton.setEnabled(sendEnabled);
      currentSendButton.setBackgroundResource(
          sendEnabled ? R.drawable.send_button_background : R.drawable.send_button_background_disabled);
    }
  }

  private void presentBeforeSpeakingScene(@Nullable String sentenceToTranslate) {
    if (latestState == null || latestState.getStage() != RefinerGameViewModel.Stage.ANSWERING) {
      return;
    }
    activeScene = RefinerInputScene.BEFORE_SPEAKING;
    String safeSentence = safeString(sentenceToTranslate);
    renderBottomSheetScene(
        R.layout.bottom_sheet_content_before_speaking,
        content -> {
          clearDefaultInputBindings();
          if (bottomSheetSceneRenderer != null) {
            bottomSheetSceneRenderer.renderBeforeSpeakingContent(content, safeSentence, null);
          }
          ripple1 = content.findViewById(R.id.ripple_1);
          ripple2 = content.findViewById(R.id.ripple_2);
          ripple3 = content.findViewById(R.id.ripple_3);
          recordingUiController.bindViews(null, ripple1, ripple2, ripple3);
          recordingUiController.startRippleAnimation();
        });
  }

  private void presentWhileSpeakingScene(@Nullable String sentenceToTranslate) {
    if (speakingSceneCoordinator == null) {
      return;
    }
    activeScene = RefinerInputScene.WHILE_SPEAKING;
    speakingSceneCoordinator.presentWhileSpeakingScene(safeString(sentenceToTranslate));
  }

  private void presentFeedbackScene(@NonNull RefinerGameViewModel.GameUiState state) {
    RefinerEvaluation evaluation = state.getEvaluation();
    if (evaluation == null) {
      return;
    }
    activeScene = RefinerInputScene.FEEDBACK;
    renderBottomSheetScene(
        R.layout.bottom_sheet_refiner_feedback,
        content -> {
          clearDefaultInputBindings();
          TextView level = content.findViewById(R.id.tv_refiner_feedback_level);
          TextView sentence = content.findViewById(R.id.tv_refiner_feedback_sentence);
          TextView lexical = content.findViewById(R.id.tv_refiner_feedback_lexical);
          TextView syntax = content.findViewById(R.id.tv_refiner_feedback_syntax);
          TextView naturalness = content.findViewById(R.id.tv_refiner_feedback_naturalness);
          TextView compliance = content.findViewById(R.id.tv_refiner_feedback_compliance);
          ProgressBar lexicalBar = content.findViewById(R.id.progress_refiner_lexical);
          ProgressBar syntaxBar = content.findViewById(R.id.progress_refiner_syntax);
          ProgressBar naturalBar = content.findViewById(R.id.progress_refiner_naturalness);
          ProgressBar complianceBar = content.findViewById(R.id.progress_refiner_compliance);
          TextView questionScore = content.findViewById(R.id.tv_refiner_question_score);
          TextView bonus = content.findViewById(R.id.tv_refiner_feedback_bonus);
          LinearLayout examples = content.findViewById(R.id.layout_refiner_examples_container);
          TextView insight = content.findViewById(R.id.tv_refiner_insight);
          Button next = content.findViewById(R.id.btn_refiner_next);

          if (level != null) {
            level.setText(getString(R.string.refiner_feedback_level_format, evaluation.getLevel().name()));
          }
          if (sentence != null) {
            sentence.setText(
                getString(
                    R.string.refiner_feedback_sentence_format, safeString(state.getSubmittedSentence())));
          }
          setScoreRow(lexical, lexicalBar, evaluation.getLexicalScore());
          setScoreRow(syntax, syntaxBar, evaluation.getSyntaxScore());
          setScoreRow(naturalness, naturalBar, evaluation.getNaturalnessScore());
          setScoreRow(compliance, complianceBar, evaluation.getComplianceScore());
          if (questionScore != null) {
            questionScore.setText(
                getString(
                    R.string.refiner_feedback_question_score_format,
                    state.getLastQuestionScore(),
                    state.getTotalScore()));
          }
          if (bonus != null) {
            bonus.setText(
                getString(
                    R.string.refiner_feedback_bonus_breakdown_format,
                    state.getLastBaseScore(),
                    state.getLastHintModifier(),
                    state.getLastQuickBonus(),
                    state.getLastCreativeBonus(),
                    formatElapsedSeconds(state.getElapsedMs())));
          }
          if (examples != null) {
            inflateLevelExamples(examples, evaluation);
          }
          if (insight != null) {
            insight.setText(
                getString(R.string.refiner_feedback_insight_format, safeString(evaluation.getInsight())));
          }
          if (next != null) {
            next.setText(
                state.getCurrentQuestionNumber() >= state.getTotalQuestions()
                    ? R.string.refiner_next_show_result
                    : R.string.refiner_next_question);
            next.setOnClickListener(
                v -> {
                  if (viewModel != null) {
                    viewModel.onNextFromFeedback();
                  }
                });
          }
        });
  }

  private void presentCompletedScene(@Nullable RefinerRoundResult roundResult) {
    if (roundResult == null) {
      return;
    }
    activeScene = RefinerInputScene.COMPLETED;
    renderBottomSheetScene(
        R.layout.bottom_sheet_refiner_completed,
        content -> {
          clearDefaultInputBindings();
          TextView total = content.findViewById(R.id.tv_refiner_completed_total_score);
          TextView lexical = content.findViewById(R.id.tv_refiner_completed_lexical_avg);
          TextView syntax = content.findViewById(R.id.tv_refiner_completed_syntax_avg);
          TextView naturalness = content.findViewById(R.id.tv_refiner_completed_naturalness_avg);
          TextView compliance = content.findViewById(R.id.tv_refiner_completed_compliance_avg);
          Button finish = content.findViewById(R.id.btn_refiner_finish);
          if (total != null) {
            total.setText(
                getString(R.string.refiner_completed_total_score_format, roundResult.getTotalScore()));
          }
          if (lexical != null) {
            lexical.setText(
                getString(
                    R.string.refiner_completed_lexical_avg_format,
                    roundResult.getAverageLexicalScore()));
          }
          if (syntax != null) {
            syntax.setText(
                getString(
                    R.string.refiner_completed_syntax_avg_format, roundResult.getAverageSyntaxScore()));
          }
          if (naturalness != null) {
            naturalness.setText(
                getString(
                    R.string.refiner_completed_naturalness_avg_format,
                    roundResult.getAverageNaturalnessScore()));
          }
          if (compliance != null) {
            compliance.setText(
                getString(
                    R.string.refiner_completed_compliance_avg_format,
                    roundResult.getAverageComplianceScore()));
          }
          if (finish != null) {
            finish.setOnClickListener(v -> requireActivity().finish());
          }
        });
  }

  private void setScoreRow(
      @Nullable TextView valueView, @Nullable ProgressBar progressBar, int scoreValue) {
    if (valueView != null) {
      valueView.setText(getString(R.string.refiner_score_value_format, scoreValue));
    }
    if (progressBar != null) {
      progressBar.setProgress(Math.max(0, Math.min(100, scoreValue)));
    }
  }

  private void inflateLevelExamples(
      @NonNull LinearLayout container, @NonNull RefinerEvaluation evaluation) {
    container.removeAllViews();
    for (RefinerLevelExample example : evaluation.getLevelExamples()) {
      TextView title = new TextView(container.getContext());
      String titleText =
          getString(
              R.string.refiner_feedback_level_example_title,
              example.getLevel().name(),
              safeString(example.getSentence()));
      if (example.getLevel() == evaluation.getLevel()) {
        titleText = titleText + " · " + getString(R.string.refiner_feedback_my_level_suffix);
      }
      title.setText(titleText);
      title.setTypeface(Typeface.DEFAULT_BOLD);
      title.setTextSize(13f);
      title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));

      TextView comment = new TextView(container.getContext());
      comment.setText(
          getString(
              R.string.refiner_feedback_level_example_comment, safeString(example.getComment())));
      comment.setTextSize(13f);
      comment.setTextColor(ContextCompat.getColor(requireContext(), R.color.grey_700));

      LinearLayout.LayoutParams titleParams =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      titleParams.topMargin = dpToPx(8);
      title.setLayoutParams(titleParams);
      container.addView(title);
      container.addView(comment);
    }
  }

  private void showOnly(@Nullable View target) {
    setVisible(layoutRefinerContent, target == layoutRefinerContent);
    setVisible(layoutRefinerLoading, target == layoutRefinerLoading);
    setVisible(layoutRefinerError, target == layoutRefinerError);
  }

  private void setVisible(@Nullable View target, boolean visible) {
    if (target != null) {
      target.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
  }

  private void hideBottomSheet() {
    if (bottomSheetController != null) {
      bottomSheetController.clearContent();
      bottomSheetController.setVisible(false);
    }
    clearDefaultInputBindings();
    syncTopScrollBottomPadding();
  }

  private void clearDefaultInputBindings() {
    if (currentInputEditText != null && currentInputTextWatcher != null) {
      currentInputEditText.removeTextChangedListener(currentInputTextWatcher);
    }
    currentInputEditText = null;
    currentInputTextWatcher = null;
    currentSendButton = null;
    currentMicButton = null;
  }

  private void renderBottomSheetScene(
      @LayoutRes int layoutResId, @Nullable BottomSheetSceneRenderer.SheetBinder binder) {
    if (bottomSheetController == null || bottomSheetSceneRenderer == null) {
      return;
    }
    bottomSheetController.setVisible(true);
    boolean skipAnimation = shouldSkipBottomSheetStateAnimation(layoutResId);
    bottomSheetController.changeContent(
        () -> {
          View content =
              bottomSheetController.replaceOrReuseContent(
                  layoutResId, bottomSheetSceneRenderer, this::syncTopScrollBottomPadding);
          if (content == null) {
            return;
          }
          if (binder != null) {
            binder.bind(content);
          }
          content.setTag(layoutResId);
          syncTopScrollBottomPadding();
        },
        skipAnimation);
  }

  private boolean shouldSkipBottomSheetStateAnimation(@LayoutRes int nextLayoutResId) {
    boolean beforeToWhile =
        isCurrentBottomSheetLayout(R.layout.bottom_sheet_content_before_speaking)
            && nextLayoutResId == R.layout.bottom_sheet_content_while_speaking;
    boolean whileToBefore =
        isCurrentBottomSheetLayout(R.layout.bottom_sheet_content_while_speaking)
            && nextLayoutResId == R.layout.bottom_sheet_content_before_speaking;
    return beforeToWhile || whileToBefore;
  }

  private boolean isCurrentBottomSheetLayout(@LayoutRes int layoutResId) {
    if (bottomSheetController == null) {
      return false;
    }
    View current = bottomSheetController.getCurrentContent();
    if (current == null || !(current.getTag() instanceof Integer)) {
      return false;
    }
    return ((Integer) current.getTag()) == layoutResId;
  }

  private void syncTopScrollBottomPadding() {
    if (topScrollArea == null) {
      return;
    }
    topScrollArea.post(
        () -> {
          int visibleHeight = bottomSheetRenderer == null ? 0 : bottomSheetRenderer.getVisibleHeight();
          int paddingBottom = baseScrollPaddingBottom + visibleHeight + dpToPx(12);
          topScrollArea.setPadding(
              topScrollArea.getPaddingLeft(),
              topScrollArea.getPaddingTop(),
              topScrollArea.getPaddingRight(),
              paddingBottom);
        });
  }

  @NonNull
  private String formatConstraintsText(@NonNull RefinerConstraints constraints) {
    List<String> lines = new ArrayList<>();
    if (!constraints.getBannedWords().isEmpty()) {
      lines.add(
          getString(
              R.string.refiner_constraint_banned_format,
              TextUtils.join(", ", constraints.getBannedWords())));
    }
    RefinerWordLimit wordLimit = constraints.getWordLimit();
    if (wordLimit != null) {
      if (wordLimit.getMode() == RefinerWordLimitMode.EXACT) {
        lines.add(
            getString(R.string.refiner_constraint_word_limit_exact_format, wordLimit.getValue()));
      } else {
        lines.add(getString(R.string.refiner_constraint_word_limit_max_format, wordLimit.getValue()));
      }
    }
    if (!constraints.getRequiredWord().isEmpty()) {
      lines.add(
          getString(
              R.string.refiner_constraint_required_format, safeString(constraints.getRequiredWord())));
    }
    return lines.isEmpty() ? "-" : TextUtils.join("\n", lines);
  }

  private void submitFromDefaultInput(@Nullable String userInput) {
    if (viewModel == null || currentQuestion == null) {
      return;
    }
    String trimmed = safeString(userInput).trim();
    RefinerConstraintValidator.ValidationResult validation =
        RefinerConstraintValidator.validate(currentQuestion.getConstraints(), trimmed);
    latestValidationResult = validation;
    RefinerGameViewModel.ActionResult result =
        viewModel.onSubmitSentence(trimmed, validation.isAllConstraintsSatisfied());
    if (result == RefinerGameViewModel.ActionResult.INVALID) {
      toastShort(getString(R.string.refiner_submit_invalid));
      updateValidationAndWordCount(trimmed, true);
    }
  }

  private void handleBeforeSpeakToRecord(@Nullable String sentenceToTranslate) {
    if (speakingCacheInitializing || !speakingCacheReady) {
      toastShort(getString(R.string.refiner_mic_disabled_desc));
      return;
    }
    if (!isMicPermissionGranted()) {
      requestMicPermission(sentenceToTranslate, "before_speak");
      return;
    }
    presentWhileSpeakingScene(sentenceToTranslate);
  }

  private void requestMicPermission(@Nullable String sentenceToTranslate, @NonNull String source) {
    if (micPermissionCoordinator != null) {
      micPermissionCoordinator.request(sentenceToTranslate, source);
    }
  }

  private void stopSpeakingSession() {
    if (speakingSceneCoordinator != null) {
      speakingSceneCoordinator.stopSpeakingSession();
      return;
    }
    safeStopRecording("stopSpeakingSession");
    speaking = false;
    recordingActive = false;
    recordingUiController.stopProgressAnimation();
    recordingUiController.stopRippleAnimation();
    if (audioAccumulator != null) {
      audioAccumulator.reset();
    }
  }

  private void safeStopRecording(@NonNull String reason) {
    if (audioRecorderManager != null && audioRecorderManager.isRecording()) {
      audioRecorderManager.stopRecording();
    }
    recordingActive = false;
  }

  private void handleRecordingStopRequestedOrAnalyzeFallback(
      @NonNull String sentenceToTranslate, @NonNull byte[] audioData) {
    speaking = false;
    recordingActive = false;
    analyzeSpeakingInternal(sentenceToTranslate, audioData, "");
  }

  private void analyzeSpeakingInternal(
      @NonNull String sentenceToTranslate,
      @NonNull byte[] audioData,
      @NonNull String recognizedTextFallback) {
    if (speakingFlowController == null) {
      toastShort("음성 분석을 시작할 수 없습니다.");
      presentBeforeSpeakingScene(sentenceToTranslate);
      return;
    }
    speakingFlowController.analyzeSpeaking(
        sentenceToTranslate,
        audioData,
        recognizedTextFallback,
        new SpeakingFlowController.Callback() {
          @Override
          public void onSuccess(@NonNull SpeakingAnalysisResult result) {
            mainHandler.post(() -> onSpeakingAnalysisSuccess(result));
          }

          @Override
          public void onError(long requestId, @NonNull String error) {
            mainHandler.post(() -> onSpeakingAnalysisError(sentenceToTranslate, error));
          }
        });
  }

  private void onSpeakingAnalysisSuccess(@NonNull SpeakingAnalysisResult result) {
    if (!isAdded()) {
      return;
    }
    stopSpeakingSession();
    String transcript = safeString(result.getRecognizedText()).trim();
    if (transcript.isEmpty()) {
      toastShort("인식된 음성이 없습니다.");
      presentBeforeSpeakingScene(resolveSentenceForInputScene());
      return;
    }
    pendingTranscriptionToApply = transcript;
    cachedInputText = transcript;
    presentDefaultInputScene(resolveSentenceForInputScene());
  }

  private void onSpeakingAnalysisError(@NonNull String sentenceToTranslate, @NonNull String error) {
    if (!isAdded()) {
      return;
    }
    stopSpeakingSession();
    toastShort("음성 분석에 실패했습니다. 다시 시도해 주세요.");
    presentBeforeSpeakingScene(sentenceToTranslate);
  }

  @NonNull
  private MicPermissionCoordinator.Host buildMicPermissionCoordinatorHost() {
    return new MicPermissionCoordinator.Host() {
      @Override
      public boolean isHostActive() {
        return isAdded() && getContext() != null;
      }

      @Override
      public boolean isMicPermissionGranted() {
        return RefinerInputV1Fragment.this.isMicPermissionGranted();
      }

      @Override
      public boolean hasMicPermissionRationale() {
        return RefinerInputV1Fragment.this.hasMicPermissionRationale();
      }

      @Override
      public void requestPermissionLaunch() {
        if (requestPermissionLauncher != null) {
          requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
      }

      @Override
      public void onNeedOpenSettingsDialog(@NonNull String source) {
        showMicPermissionSettingsDialog(source);
      }

      @Override
      public void onPermissionDeniedToast() {
        toastShort("Microphone permission is required.");
      }

      @Override
      public void onStartSpeakingAfterPermission(@NonNull String sentenceToTranslate) {
        presentWhileSpeakingScene(sentenceToTranslate);
      }

      @Override
      public void onShowBeforeSpeakingAfterPermission(@NonNull String sentenceToTranslate) {
        presentBeforeSpeakingScene(sentenceToTranslate);
      }

      @Nullable
      @Override
      public String onConsumePendingSentenceFromSpeakingCoordinator() {
        String pending = pendingSentenceForPermission;
        pendingSentenceForPermission = null;
        return pending;
      }

      @Override
      public void onSetPendingSentenceToSpeakingCoordinator(@NonNull String sentenceToTranslate) {
        pendingSentenceForPermission = sentenceToTranslate;
      }

      @Override
      public void trace(@NonNull String key) {}

      @Override
      public void gate(@NonNull String key) {}

      @Override
      public void ux(@NonNull String key, @Nullable String fields) {}
    };
  }

  @NonNull
  private SpeakingSceneCoordinator.Host buildSpeakingSceneCoordinatorHost() {
    return new SpeakingSceneCoordinator.Host() {
      @Override
      public boolean isMicPermissionGranted() {
        return RefinerInputV1Fragment.this.isMicPermissionGranted();
      }

      @Override
      public boolean onWhileSpeakingPermissionCheck(
          @NonNull String sentenceToTranslate, boolean hasPermission) {
        if (hasPermission) {
          return true;
        }
        requestMicPermission(sentenceToTranslate, "while_speaking");
        return false;
      }

      @Override
      public void onRecordingStarted() {
        speaking = true;
        recordingActive = true;
      }

      @Override
      public void onRecordingStopRequestedOrAnalyzeFallback(
          @NonNull String sentenceToTranslate, @NonNull byte[] audioData) {
        handleRecordingStopRequestedOrAnalyzeFallback(sentenceToTranslate, audioData);
      }

      @Override
      public boolean isSpeaking() {
        return speaking;
      }

      @Override
      public boolean isRecordingActive() {
        return recordingActive;
      }

      @Override
      public void stopSpeakingSessionState(@NonNull String reason) {
        speaking = false;
        recordingActive = false;
      }

      @Override
      public void safeStopPlayback(@NonNull String reason) {}

      @Override
      public void safeStopRecording(@NonNull String reason) {
        RefinerInputV1Fragment.this.safeStopRecording(reason);
      }

      @Nullable
      @Override
      public AudioRecorder getAudioRecorderManager() {
        return audioRecorderManager;
      }

      @NonNull
      @Override
      public Handler getMainHandler() {
        return mainHandler;
      }

      @Nullable
      @Override
      public ByteArrayOutputStream getAudioAccumulator() {
        return audioAccumulator;
      }

      @Nullable
      @Override
      public StringBuilder getTranscriptBuilder() {
        return transcriptBuilder;
      }

      @Override
      public void onAudioChunk(@NonNull byte[] audioData) {}

      @Override
      public void setLastRecordedAudio(@NonNull byte[] audioData) {
        lastRecordedAudio = audioData.clone();
      }

      @Override
      public void renderBottomSheetScene(
          int layoutResId, @Nullable BottomSheetSceneRenderer.SheetBinder binder) {
        RefinerInputV1Fragment.this.renderBottomSheetScene(layoutResId, binder);
      }

      @Override
      public void markRenderedScene(
          @NonNull com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningScene scene,
          @Nullable String sentenceToRender) {}

      @Override
      public void renderWhileSpeakingContent(
          @NonNull View content, @NonNull String sentenceToTranslate) {
        if (bottomSheetSceneRenderer != null) {
          bottomSheetSceneRenderer.renderWhileSpeakingContent(content, sentenceToTranslate, null);
        }
      }

      @Override
      public void startProgressAnimation() {
        recordingUiController.startProgressAnimation();
      }

      @Override
      public void stopProgressAnimation() {
        recordingUiController.stopProgressAnimation();
      }

      @Override
      public void startRippleAnimation() {
        recordingUiController.startRippleAnimation();
      }

      @Override
      public void stopRippleAnimation() {
        recordingUiController.stopRippleAnimation();
      }

      @Override
      public void onSpeakingViewsBound(
          @Nullable ImageButton btnMicCircle,
          @Nullable ProgressBar progressRing,
          @Nullable ProgressBar loadingSpinner,
          @Nullable TextView tvListeningStatus,
          @Nullable WaveformView waveformView,
          @Nullable View ripple1,
          @Nullable View ripple2,
          @Nullable View ripple3) {
        RefinerInputV1Fragment.this.progressRing = progressRing;
        RefinerInputV1Fragment.this.loadingSpinner = loadingSpinner;
        RefinerInputV1Fragment.this.tvListeningStatus = tvListeningStatus;
        RefinerInputV1Fragment.this.waveformView = waveformView;
        RefinerInputV1Fragment.this.ripple1 = ripple1;
        RefinerInputV1Fragment.this.ripple2 = ripple2;
        RefinerInputV1Fragment.this.ripple3 = ripple3;
        recordingUiController.bindViews(progressRing, ripple1, ripple2, ripple3);
      }

      @Override
      public void onSpeakingViewsCleared() {
        progressRing = null;
        loadingSpinner = null;
        tvListeningStatus = null;
        waveformView = null;
        ripple1 = null;
        ripple2 = null;
        ripple3 = null;
      }

      @Override
      public long getLastWaveformUpdateMs() {
        return lastWaveformUpdateMs;
      }

      @Override
      public void setLastWaveformUpdateMs(long value) {
        lastWaveformUpdateMs = value;
      }

      @Override
      public void trace(@NonNull String key) {}

      @Override
      public void gate(@NonNull String key) {}

      @Override
      public void ux(@NonNull String key, @Nullable String fields) {}

      @Override
      public boolean tryPresentBeforeSpeakingSceneAfterNoAudio(
          @NonNull String sentenceToTranslate) {
        if (!isAdded() || getContext() == null) {
          return false;
        }
        if (latestState == null || latestState.getStage() != RefinerGameViewModel.Stage.ANSWERING) {
          return false;
        }
        toastShort("녹음된 소리가 없습니다.");
        presentBeforeSpeakingScene(sentenceToTranslate);
        return true;
      }
    };
  }

  @NonNull
  private LearningBottomSheetActionRouter.ActionDelegate buildBottomSheetActionDelegate() {
    return new LearningBottomSheetActionRouter.ActionDelegate() {
      @Override
      public void hideKeyboard() {
        RefinerInputV1Fragment.this.hideKeyboard();
      }

      @Override
      public void presentBeforeSpeakingScene(@Nullable String sentenceToTranslate) {
        RefinerInputV1Fragment.this.presentBeforeSpeakingScene(sentenceToTranslate);
      }

      @Override
      public void requestDefaultInputSceneOnceAfterDelayIfFirstTime(
          @Nullable String sentenceToTranslate) {
        presentDefaultInputScene(sentenceToTranslate);
      }

      @Override
      public void handleBeforeSpeakToRecord(@Nullable String sentenceToTranslate) {
        RefinerInputV1Fragment.this.handleBeforeSpeakToRecord(sentenceToTranslate);
      }

      @Override
      public void stopSpeakingSession() {
        RefinerInputV1Fragment.this.stopSpeakingSession();
      }

      @Override
      public void invalidatePendingSpeakingAnalysis() {
        if (speakingFlowController != null) {
          speakingFlowController.invalidate();
        }
      }

      @Override
      public void presentFeedbackSceneForDefaultInput(
          @Nullable String originalSentence, @Nullable String translatedSentence) {
        submitFromDefaultInput(translatedSentence);
      }

      @Override
      public void handleNextStepSequence(@Nullable String userMessage, @Nullable byte[] audioData) {}

      @Override
      public void clearLastRecordedAudio() {
        lastRecordedAudio = null;
      }

      @Override
      public void emitOpenSummaryOrFallback() {}

      @Override
      public void playRecordedAudio(@Nullable byte[] audio, @Nullable android.widget.ImageView speakerButton) {}

      @Override
      public void requestMicPermission(
          @Nullable String sentenceToTranslate, @NonNull String source) {
        RefinerInputV1Fragment.this.requestMicPermission(sentenceToTranslate, source);
      }

      @Override
      public void stopRippleAnimation() {
        recordingUiController.stopRippleAnimation();
      }
    };
  }

  @NonNull
  private LearningBottomSheetActionRouter.LoggerDelegate buildBottomSheetLoggerDelegate() {
    return new LearningBottomSheetActionRouter.LoggerDelegate() {
      @Override
      public void trace(@NonNull String key) {}

      @Override
      public void gate(@NonNull String key) {}

      @Override
      public void ux(@NonNull String key, @Nullable String fields) {}
    };
  }

  private boolean isMicPermissionGranted() {
    if (!isAdded() || getContext() == null) {
      return false;
    }
    return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED;
  }

  private boolean hasMicPermissionRationale() {
    if (!isAdded() || getContext() == null) {
      return false;
    }
    return shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO);
  }

  private void showMicPermissionSettingsDialog(@NonNull String source) {
    if (!isAdded() || getContext() == null) {
      return;
    }
    AlertDialog dialog =
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("마이크 권한이 필요합니다")
            .setMessage("앱 설정에서 마이크 권한을 허용하면 음성 입력을 사용할 수 있습니다.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("설정으로 이동", (d, w) -> openAppSettingsForMicrophonePermission())
            .create();
    dialog.setOnCancelListener(
        d -> {
          if (micPermissionCoordinator != null) {
            micPermissionCoordinator.onSettingsDialogCancelled();
          }
        });
    dialog.show();
  }

  private void openAppSettingsForMicrophonePermission() {
    if (getContext() == null) {
      return;
    }
    try {
      Intent intent =
          new Intent(
              Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
              Uri.fromParts("package", requireContext().getPackageName(), null));
      startActivity(intent);
      if (micPermissionCoordinator != null) {
        micPermissionCoordinator.markSettingsOpened();
      }
    } catch (ActivityNotFoundException e) {
      toastShort("설정 화면을 열 수 없습니다.");
    }
  }

  private void hideKeyboard() {
    if (!isAdded() || getActivity() == null || getView() == null) {
      return;
    }
    InputMethodManager imm =
        (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
    }
  }

  private void toastShort(@NonNull String message) {
    if (!isAdded() || getContext() == null) {
      return;
    }
    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
  }

  @NonNull
  private String resolveSentenceForInputScene() {
    if (currentQuestion != null && !isBlank(currentQuestion.getSourceSentence())) {
      return currentQuestion.getSourceSentence();
    }
    return "";
  }

  @NonNull
  private String formatElapsedSeconds(long elapsedMs) {
    return String.format(Locale.US, "%.1f", Math.max(0L, elapsedMs) / 1000f);
  }

  private int dpToPx(int dp) {
    return Math.round(dp * getResources().getDisplayMetrics().density);
  }

  private boolean isBlank(@Nullable String text) {
    return text == null || text.trim().isEmpty();
  }

  @NonNull
  private String safeString(@Nullable String text) {
    return text == null ? "" : text;
  }
}
