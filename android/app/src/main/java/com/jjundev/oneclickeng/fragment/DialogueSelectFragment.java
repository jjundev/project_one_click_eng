package com.jjundev.oneclickeng.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.activity.DialogueLearningActivity;
import com.jjundev.oneclickeng.dialog.DialogueGenerateDialog;
import com.jjundev.oneclickeng.dialog.DialogueLearningSettingDialog;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IDialogueGenerateManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.session.DialogueScriptStreamingSessionStore;
import com.jjundev.oneclickeng.others.ScriptSelectAdapter;
import com.jjundev.oneclickeng.others.ScriptTemplate;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import java.util.ArrayList;
import java.util.List;

public class DialogueSelectFragment extends Fragment
    implements DialogueGenerateDialog.OnScriptParamsSelectedListener {
  private static final String TAG = "DialogueSelectFragment";
  private static final String DIALOG_TAG_LEARNING_SETTINGS = "DialogueLearningSettingDialog";
  private static final int MIN_TURN_COUNT_TO_START = 2;

  private ImageButton btnBack;
  private ImageButton btnSettings;
  private RecyclerView rvScripts;
  private View layoutEmptyState;
  private AppCompatButton btnGenerate;
  private ScriptSelectAdapter adapter;
  private List<ScriptTemplate> templateList;
  private IDialogueGenerateManager scriptGenerator;
  @Nullable private String pendingScriptSessionId;
  @Nullable private DialogueScriptStreamingSessionStore.Listener pendingScriptSessionListener;
  private long scriptPreparationRequestId = 0L;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_dialogue_select, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    scriptGenerator = resolveScriptGenerator();
    scriptGenerator.initializeCache(
        new IDialogueGenerateManager.InitCallback() {
          @Override
          public void onReady() {
            logStream("generator cache ready");
          }

          @Override
          public void onError(String error) {
            Log.e(TAG, "[DL_STREAM] generator cache init error: " + error);
          }
        });

    btnBack = view.findViewById(R.id.btn_back);
    btnSettings = view.findViewById(R.id.btn_settings);
    rvScripts = view.findViewById(R.id.rv_scripts);
    layoutEmptyState = view.findViewById(R.id.layout_empty_state);
    btnGenerate = view.findViewById(R.id.btn_generate_script);

    setupRecyclerView();
    setupListeners();
  }

  @Override
  public void onDestroyView() {
    clearPendingScriptSession(true);
    scriptPreparationRequestId = 0L;
    super.onDestroyView();
  }

  private void setupRecyclerView() {
    templateList = new ArrayList<>();
    // Sample data
    templateList.add(
        new ScriptTemplate("☕", "카페에서 주문하기", "자연스러운 영어 회화", "안녕하세요! 따뜻한 아메리카노 한 잔 부탁합니다."));
    templateList.add(
        new ScriptTemplate("🏢", "회사에서 자기소개", "전문적인 비즈니스 표현", "만나서 반갑습니다. 저는 마케팅 팀의 김현준입니다."));
    templateList.add(
        new ScriptTemplate("✈️", "공항 입국 심사", "필수 여행 영어", "방문 목적은 관광입니다. 일주일 동안 머무를 예정이에요."));
    templateList.add(
        new ScriptTemplate("🚕", "택시 목적지 말하기", "실전 생활 표현", "기사님, 강남역으로 가주세요. 얼마나 걸릴까요?"));

    adapter = new ScriptSelectAdapter(
        templateList,
        template -> {
          hideKeyboard();
          DialogueGenerateDialog dialog = DialogueGenerateDialog.newInstance(template.getTitle());
          dialog.show(getChildFragmentManager(), "DialogueGenerateDialog");
        });

    rvScripts.setLayoutManager(new GridLayoutManager(getContext(), 2));
    rvScripts.setAdapter(adapter);

    // Apply layout animation to the RecyclerView
    android.view.animation.LayoutAnimationController controller = android.view.animation.AnimationUtils
        .loadLayoutAnimation(
            rvScripts.getContext(), R.anim.layout_anim_slide_fade_in);
    rvScripts.setLayoutAnimation(controller);

    updateEmptyState();
  }

  private IDialogueGenerateManager resolveScriptGenerator() {
    Context appContext = requireContext().getApplicationContext();
    AppSettings settings = new AppSettingsStore(appContext).getSettings();
    return LearningDependencyProvider.provideDialogueGenerateManager(
        appContext,
        settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
        settings.getLlmModelScript());
  }

  private void setupListeners() {
    btnBack.setOnClickListener(
        v -> {
          Navigation.findNavController(v).popBackStack();
        });

    btnSettings.setOnClickListener(v -> showDialogueLearningSettingDialog());

    btnGenerate.setOnClickListener(
        v -> {
          hideKeyboard(); // Ensure keyboard is hidden

          DialogueGenerateDialog dialogueGenerateDialog = new DialogueGenerateDialog();
          dialogueGenerateDialog.show(getChildFragmentManager(), "DialogueGenerateDialog");
        });
  }

  private void showDialogueLearningSettingDialog() {
    if (!isAdded()) {
      return;
    }

    FragmentManager fragmentManager = getChildFragmentManager();
    if (fragmentManager.isStateSaved()) {
      return;
    }

    Fragment existingDialog = fragmentManager.findFragmentByTag(DIALOG_TAG_LEARNING_SETTINGS);
    if (existingDialog != null && existingDialog.isAdded()) {
      return;
    }

    new DialogueLearningSettingDialog()
        .show(fragmentManager, DIALOG_TAG_LEARNING_SETTINGS);
  }

  @Override
  public void onScriptParamsSelected(
      String level, String topic, String format, int length, DialogueGenerateDialog dialog) {
    generateScriptStreaming(level, topic, format, length, dialog);
  }

  private void generateScriptStreaming(
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length,
      @Nullable DialogueGenerateDialog dialog) {
    logStream(
        "prepare start: level="
            + level
            + ", topic="
            + safeText(topic)
            + ", format="
            + format
            + ", requestedLength="
            + Math.max(1, length));
    clearPendingScriptSession(true);
    long requestId = ++scriptPreparationRequestId;
    DialogueScriptStreamingSessionStore sessionStore =
        LearningDependencyProvider.provideDialogueScriptStreamingSessionStore();

    String sessionId = sessionStore.startSession(scriptGenerator, level, topic, format, length);
    logStream("session started: requestId=" + requestId + ", sessionId=" + shortSession(sessionId));
    final boolean[] started = {false};
    final String[] resolvedTopic = {topic};
    final int[] validTurnCount = {0};

    DialogueScriptStreamingSessionStore.Listener listener =
        new DialogueScriptStreamingSessionStore.Listener() {
          @Override
          public void onMetadata(@NonNull DialogueScriptStreamingSessionStore.ScriptMetadata metadata) {
            if (requestId != scriptPreparationRequestId || started[0]) {
              return;
            }
            String metadataTopic = trimToNull(metadata.getTopic());
            if (metadataTopic != null) {
              resolvedTopic[0] = metadataTopic;
            }
            logStream(
                "prepare metadata: requestId="
                    + requestId
                    + ", topic="
                    + safeText(resolvedTopic[0]));
          }

          @Override
          public void onTurn(@NonNull IDialogueGenerateManager.ScriptTurnChunk turn) {
            if (requestId != scriptPreparationRequestId || started[0]) {
              return;
            }
            if (!isValidTurn(turn)) {
              return;
            }
            validTurnCount[0]++;
            logStream(
                "prepare turn: requestId="
                    + requestId
                    + ", validTurnCount="
                    + validTurnCount[0]
                    + "/"
                    + MIN_TURN_COUNT_TO_START
                    + ", role="
                    + safeText(turn.getRole()));
            if (validTurnCount[0] >= MIN_TURN_COUNT_TO_START) {
              started[0] = true;
              logStream(
                  "prepare threshold reached: requestId="
                      + requestId
                      + ", sessionId="
                      + shortSession(sessionId));
              startPreparedScriptStudy(
                  dialog,
                  requestId,
                  level,
                  Math.max(1, length),
                  resolvedTopic[0],
                  sessionId);
            }
          }

          @Override
          public void onComplete(@Nullable String warningMessage) {
            if (requestId != scriptPreparationRequestId || started[0]) {
              return;
            }
            logStream(
                "prepare complete before start: requestId="
                    + requestId
                    + ", warning="
                    + safeText(warningMessage));
            showScriptPreparationError(dialog, requestId);
          }

          @Override
          public void onFailure(@NonNull String error) {
            if (requestId != scriptPreparationRequestId || started[0]) {
              return;
            }
            logStream(
                "prepare failure before start: requestId="
                    + requestId
                    + ", error="
                    + safeText(error));
            showScriptPreparationError(dialog, requestId);
          }
        };

    pendingScriptSessionId = sessionId;
    pendingScriptSessionListener = listener;
    DialogueScriptStreamingSessionStore.Snapshot snapshot = sessionStore.attach(sessionId, listener);
    if (snapshot == null) {
      logStream(
          "prepare attach failed: requestId="
              + requestId
              + ", sessionId="
              + shortSession(sessionId));
      showScriptPreparationError(dialog, requestId);
      return;
    }
    logStream(
        "prepare snapshot: requestId="
            + requestId
            + ", bufferedTurns="
            + snapshot.getBufferedTurns().size()
            + ", completed="
            + snapshot.isCompleted()
            + ", failure="
            + (trimToNull(snapshot.getFailureMessage()) != null));
    if (snapshot.getMetadata() != null) {
      String metadataTopic = trimToNull(snapshot.getMetadata().getTopic());
      if (metadataTopic != null) {
        resolvedTopic[0] = metadataTopic;
      }
    }
    validTurnCount[0] = countValidTurns(snapshot.getBufferedTurns());
    if (hasStartableTurns(snapshot.getBufferedTurns())) {
      started[0] = true;
      logStream(
          "prepare threshold reached from snapshot: requestId="
              + requestId
              + ", sessionId="
              + shortSession(sessionId));
      startPreparedScriptStudy(
          dialog, requestId, level, Math.max(1, length), resolvedTopic[0], sessionId);
      return;
    }
    if (trimToNull(snapshot.getFailureMessage()) != null || snapshot.isCompleted()) {
      logStream(
          "prepare cannot start from snapshot: requestId="
              + requestId
              + ", completed="
              + snapshot.isCompleted()
              + ", failure="
              + safeText(snapshot.getFailureMessage()));
      showScriptPreparationError(dialog, requestId);
    }
  }

  private void startPreparedScriptStudy(
      @Nullable DialogueGenerateDialog dialog,
      long requestId,
      @NonNull String level,
      int requestedLength,
      @NonNull String requestedTopic,
      @NonNull String sessionId) {
    logStream(
        "start learning activity: requestId="
            + requestId
            + ", level="
            + level
            + ", requestedLength="
            + requestedLength
            + ", topic="
            + safeText(requestedTopic)
            + ", sessionId="
            + shortSession(sessionId));
    clearPendingScriptSession(false);
    finishScriptPreparation(dialog, requestId);
    if (dialog != null && dialog.isAdded()) {
      dialog.dismiss();
    }
    startScriptStudyStreaming(level, requestedLength, requestedTopic, sessionId);
  }

  private void showScriptPreparationError(@Nullable DialogueGenerateDialog dialog, long requestId) {
    logStream("prepare error: requestId=" + requestId);
    clearPendingScriptSession(true);
    finishScriptPreparation(dialog, requestId);
    if (!isAdded()) {
      return;
    }
    Toast.makeText(getContext(), "대본 생성 중 오류가 발생했어요", Toast.LENGTH_SHORT).show();
  }

  private void finishScriptPreparation(@Nullable DialogueGenerateDialog dialog, long requestId) {
    if (dialog != null) {
      dialog.showLoading(false);
    }
    if (requestId == scriptPreparationRequestId) {
      scriptPreparationRequestId = 0L;
    }
  }

  private void clearPendingScriptSession(boolean releaseSession) {
    String sessionId = pendingScriptSessionId;
    DialogueScriptStreamingSessionStore.Listener listener = pendingScriptSessionListener;
    pendingScriptSessionId = null;
    pendingScriptSessionListener = null;

    if (sessionId == null) {
      return;
    }
    DialogueScriptStreamingSessionStore sessionStore =
        LearningDependencyProvider.provideDialogueScriptStreamingSessionStore();
    if (listener != null) {
      sessionStore.detach(sessionId, listener);
    }
    if (releaseSession) {
      sessionStore.release(sessionId);
    }
    logStream(
        "clear pending session: sessionId="
            + shortSession(sessionId)
            + ", release="
            + releaseSession);
  }

  private void startScriptStudyStreaming(
      @NonNull String level,
      int requestedLength,
      @NonNull String requestedTopic,
      @NonNull String sessionId) {
    if (!isAdded() || getActivity() == null) {
      logStream(
          "start activity aborted: host unavailable, sessionId=" + shortSession(sessionId));
      LearningDependencyProvider.provideDialogueScriptStreamingSessionStore().release(sessionId);
      return;
    }

    Intent intent = new Intent(getActivity(), DialogueLearningActivity.class);
    intent.putExtra(DialogueLearningActivity.EXTRA_SCRIPT_LEVEL, level);
    intent.putExtra(DialogueLearningActivity.EXTRA_SCRIPT_STREAM_SESSION_ID, sessionId);
    intent.putExtra(DialogueLearningActivity.EXTRA_REQUESTED_SCRIPT_LENGTH, requestedLength);
    intent.putExtra(DialogueLearningActivity.EXTRA_SCRIPT_TOPIC, requestedTopic);
    try {
      startActivity(intent);
    } catch (Exception e) {
      logStream(
          "start activity failed: sessionId="
              + shortSession(sessionId)
              + ", error="
              + safeText(e.getMessage()));
      LearningDependencyProvider.provideDialogueScriptStreamingSessionStore().release(sessionId);
      if (isAdded()) {
        Toast.makeText(getContext(), "대본 화면 이동 중 오류가 발생했어요", Toast.LENGTH_SHORT).show();
      }
      return;
    }
    logStream("start activity success: sessionId=" + shortSession(sessionId));
    hideKeyboard();
  }

  private boolean hasStartableTurns(
      @Nullable List<IDialogueGenerateManager.ScriptTurnChunk> turns) {
    if (turns == null || turns.size() < MIN_TURN_COUNT_TO_START) {
      return false;
    }
    int validCount = 0;
    for (IDialogueGenerateManager.ScriptTurnChunk turn : turns) {
      if (isValidTurn(turn)) {
        validCount++;
      }
      if (validCount >= MIN_TURN_COUNT_TO_START) {
        return true;
      }
    }
    return false;
  }

  private boolean isValidTurn(@Nullable IDialogueGenerateManager.ScriptTurnChunk turn) {
    if (turn == null) {
      return false;
    }
    return trimToNull(turn.getKorean()) != null && trimToNull(turn.getEnglish()) != null;
  }

  private int countValidTurns(@Nullable List<IDialogueGenerateManager.ScriptTurnChunk> turns) {
    if (turns == null || turns.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (IDialogueGenerateManager.ScriptTurnChunk turn : turns) {
      if (isValidTurn(turn)) {
        count++;
      }
    }
    return count;
  }

  @Nullable
  private static String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private void logStream(@NonNull String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, "[DL_STREAM] " + message);
    }
  }

  @NonNull
  private static String shortSession(@Nullable String sessionId) {
    String value = trimToNull(sessionId);
    if (value == null) {
      return "-";
    }
    if (value.length() <= 8) {
      return value;
    }
    return value.substring(0, 8);
  }

  @NonNull
  private static String safeText(@Nullable String value) {
    String text = trimToNull(value);
    if (text == null) {
      return "-";
    }
    if (text.length() <= 32) {
      return text;
    }
    return text.substring(0, 32) + "...";
  }


  private void updateEmptyState() {
    if (templateList.isEmpty()) {
      layoutEmptyState.setVisibility(View.VISIBLE);
      rvScripts.setVisibility(View.GONE);
    } else {
      layoutEmptyState.setVisibility(View.GONE);
      rvScripts.setVisibility(View.VISIBLE);
      rvScripts.scheduleLayoutAnimation();
    }
  }

  private void hideKeyboard() {
    View view = getActivity().getCurrentFocus();
    if (view != null) {
      InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
  }
}
