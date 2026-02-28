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
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
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

  private RewardedAd rewardedAd;
  private boolean isRewardEarned = false;
  private boolean isWaitingForAd = false;
  private android.app.Dialog chargeCreditDialog;
  private int adRetryAttempt = 0;
  private final android.os.Handler adRetryHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());

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
    loadRewardedAd();
  }

  @Override
  public void onDestroyView() {
    clearPendingScriptSession(true);
    scriptPreparationRequestId = 0L;
    adRetryHandler.removeCallbacksAndMessages(null);
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

    adapter =
        new ScriptSelectAdapter(
            templateList,
            template -> {
              hideKeyboard();
              DialogueGenerateDialog dialog =
                  DialogueGenerateDialog.newInstance(template.getTitle());
              dialog.show(getChildFragmentManager(), "DialogueGenerateDialog");
            });

    rvScripts.setLayoutManager(new GridLayoutManager(getContext(), 2));
    rvScripts.setAdapter(adapter);

    // Apply layout animation to the RecyclerView
    android.view.animation.LayoutAnimationController controller =
        android.view.animation.AnimationUtils.loadLayoutAnimation(
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

    new DialogueLearningSettingDialog().show(fragmentManager, DIALOG_TAG_LEARNING_SETTINGS);
  }

  @Override
  public void onScriptParamsSelected(
      String level, String topic, String format, int length, DialogueGenerateDialog dialog) {
    if (dialog != null) {
      dialog.showLoading(true);
    }
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      if (dialog != null) dialog.showLoading(false);
      Toast.makeText(getContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
      return;
    }

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.getUid())
        .get()
        .addOnCompleteListener(
            task -> {
              if (!isAdded()) return;
              if (task.isSuccessful() && task.getResult() != null) {
                DocumentSnapshot doc = task.getResult();
                Long creditObj = doc.getLong("credit");
                long credit = creditObj != null ? creditObj : 0L;
                if (credit > 0) {
                  generateScriptStreaming(level, topic, format, length, dialog);
                } else {
                  if (dialog != null) dialog.dismiss();
                  Toast.makeText(getContext(), "크레딧이 부족해요", Toast.LENGTH_SHORT).show();
                  showChargeCreditDialog();
                }
              } else {
                if (dialog != null) dialog.dismiss();
                Toast.makeText(getContext(), "크레딧 정보를 불러오지 못했어요.", Toast.LENGTH_SHORT).show();
              }
            });
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
          public void onMetadata(
              @NonNull DialogueScriptStreamingSessionStore.ScriptMetadata metadata) {
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
                  dialog, requestId, level, Math.max(1, length), resolvedTopic[0], sessionId);
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
    DialogueScriptStreamingSessionStore.Snapshot snapshot =
        sessionStore.attach(sessionId, listener);
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
      logStream("start activity aborted: host unavailable, sessionId=" + shortSession(sessionId));
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

      // 크레딧 1 차감 로직
      FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
      if (user != null) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.getUid())
            .update("credit", FieldValue.increment(-1))
            .addOnFailureListener(e -> logStream("Failed to decrement credit: " + e.getMessage()));
      }

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
      InputMethodManager imm =
          (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
  }

  private void showChargeCreditDialog() {
    View dialogView =
        LayoutInflater.from(getContext()).inflate(R.layout.dialog_charge_credit, null);

    View layoutContent = dialogView.findViewById(R.id.layout_content);
    View layoutLoading = dialogView.findViewById(R.id.layout_loading);
    AppCompatButton btnCancel = dialogView.findViewById(R.id.btn_charge_cancel);
    AppCompatButton btnAd = dialogView.findViewById(R.id.btn_charge_ad);

    chargeCreditDialog = new android.app.Dialog(requireContext());
    chargeCreditDialog.setContentView(dialogView);
    if (chargeCreditDialog.getWindow() != null) {
      chargeCreditDialog
          .getWindow()
          .setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
      android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
      int width = (int) (metrics.widthPixels * 0.9f);
      chargeCreditDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    chargeCreditDialog.setOnDismissListener(
        d -> {
          isWaitingForAd = false;
          chargeCreditDialog = null;
        });

    btnCancel.setOnClickListener(v -> chargeCreditDialog.dismiss());
    btnAd.setOnClickListener(
        v -> {
          if (rewardedAd != null && isAdded()) {
            chargeCreditDialog.dismiss();
            isRewardEarned = false;
            rewardedAd.show(
                requireActivity(),
                rewardItem -> {
                  isRewardEarned = true;
                  increaseCredit();
                });
          } else {
            isWaitingForAd = true;
            layoutContent.setVisibility(View.GONE);
            layoutLoading.setVisibility(View.VISIBLE);
            chargeCreditDialog.setCancelable(false);

            if (adRetryAttempt == 0) {
              loadRewardedAd();
            }
          }
        });

    chargeCreditDialog.show();
  }

  private void loadRewardedAd() {
    AdRequest adRequest = new AdRequest.Builder().build();
    String adUnitId =
        BuildConfig.DEBUG
            ? "ca-app-pub-3940256099942544/5224354917"
            : BuildConfig.ADMOB_REWARDED_AD_UNIT_ID;
    logStream("Loading rewarded ad with Unit ID: " + adUnitId);
    RewardedAd.load(
        requireContext(),
        adUnitId,
        adRequest,
        new RewardedAdLoadCallback() {
          @Override
          public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
            logStream("Failed to load rewarded ad: " + loadAdError.getMessage());
            rewardedAd = null;
            adRetryAttempt++;
            long retryDelayMillis = (long) Math.pow(2, Math.min(6, adRetryAttempt)) * 1000L;
            if (adRetryAttempt <= 5) {
              if (isWaitingForAd) {
                logStream(
                    "Retrying ad load in "
                        + retryDelayMillis
                        + "ms (Attempt "
                        + adRetryAttempt
                        + ")");
              }
              adRetryHandler.postDelayed(() -> loadRewardedAd(), retryDelayMillis);
            } else {
              if (isWaitingForAd) {
                isWaitingForAd = false;
                if (chargeCreditDialog != null && chargeCreditDialog.isShowing()) {
                  chargeCreditDialog.dismiss();
                }
                if (isAdded()) {
                  Toast.makeText(
                          getContext(), "광고를 불러오는 데 실패했어요. 나중에 다시 시도해주세요.", Toast.LENGTH_SHORT)
                      .show();
                }
              }
            }
          }

          @Override
          public void onAdLoaded(@NonNull RewardedAd ad) {
            logStream("Rewarded ad loaded.");
            rewardedAd = ad;
            adRetryAttempt = 0;
            setFullScreenContentCallback();

            if (isWaitingForAd && isAdded()) {
              isWaitingForAd = false;
              if (chargeCreditDialog != null && chargeCreditDialog.isShowing()) {
                chargeCreditDialog.dismiss();
              }
              isRewardEarned = false;
              rewardedAd.show(
                  requireActivity(),
                  rewardItem -> {
                    isRewardEarned = true;
                    increaseCredit();
                  });
            }
          }
        });
  }

  private void setFullScreenContentCallback() {
    if (rewardedAd == null) return;
    rewardedAd.setFullScreenContentCallback(
        new FullScreenContentCallback() {
          @Override
          public void onAdShowedFullScreenContent() {
            rewardedAd = null;
          }

          @Override
          public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
            logStream("Ad failed to show: " + adError.getMessage());
          }

          @Override
          public void onAdDismissedFullScreenContent() {
            logStream("Ad dismissed");
            if (!isRewardEarned && isAdded()) {
              Toast.makeText(getContext(), "광고 시청을 완료하지 않아 크레딧이 지급되지 않았어요", Toast.LENGTH_SHORT)
                  .show();
            }
            loadRewardedAd();
          }
        });
  }

  private void increaseCredit() {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      Toast.makeText(getContext(), "로그인 정보를 확인할 수 없어요", Toast.LENGTH_SHORT).show();
      return;
    }

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.getUid())
        .update("credit", com.google.firebase.firestore.FieldValue.increment(1))
        .addOnSuccessListener(
            aVoid -> {
              if (isAdded()) {
                Toast.makeText(getContext(), "1 크레딧이 충전되었어요", Toast.LENGTH_SHORT).show();
              }
            })
        .addOnFailureListener(
            e -> {
              logStream("Failed to add credit: " + e.getMessage());
              if (isAdded()) {
                Toast.makeText(getContext(), "크레딧 충전에 실패했어요", Toast.LENGTH_SHORT).show();
              }
            });
  }
}
