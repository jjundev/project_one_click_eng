package com.jjundev.oneclickeng.fragment;

import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.activity.LoginActivity;
import com.jjundev.oneclickeng.dialog.LearningDataResetDialog;
import com.jjundev.oneclickeng.dialog.LearningMetricsResetDialog;
import com.jjundev.oneclickeng.dialog.LogoutConfirmDialog;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import com.jjundev.oneclickeng.settings.LearningDataRetentionPolicy;
import com.jjundev.oneclickeng.settings.LearningPointAwardSpec;
import com.jjundev.oneclickeng.settings.LearningPointCloudRepository;
import com.jjundev.oneclickeng.settings.LearningPointStore;
import com.jjundev.oneclickeng.settings.LearningStudyTimeCloudRepository;
import com.jjundev.oneclickeng.settings.LearningStudyTimeStore;
import com.jjundev.oneclickeng.settings.SharedPreferencesCleaner;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SettingFragment extends Fragment
    implements
        LearningDataResetDialog.OnLearningDataResetListener,
        LearningMetricsResetDialog.OnLearningMetricsResetListener,
        LogoutConfirmDialog.OnLogoutConfirmListener {
  private static final String TAG = "SettingFragment";
  private static final String TAG_LEARNING_DATA_RESET_DIALOG = "LearningDataResetDialog";
  private static final String TAG_LEARNING_METRICS_RESET_DIALOG = "LearningMetricsResetDialog";
  private static final String TAG_LOGOUT_CONFIRM_DIALOG = "LogoutConfirmDialog";
  private static final int FIRESTORE_BATCH_DELETE_LIMIT = 450;
  private static final int CREATOR_BONUS_TAP_TARGET = 5;
  private static final long CREATOR_BONUS_WINDOW_MS = 2_000L;
  private static final long CREATOR_DEVELOPER_BONUS_STUDY_MILLIS = 600_000L;
  private static final int CREATOR_DEVELOPER_BONUS_POINTS = 40;
  private static final String CREATOR_DEVELOPER_BONUS_MODE_ID = "settings_creator_bonus";
  private static final String CREATOR_DEVELOPER_BONUS_DIFFICULTY = "intermediate";
  private static final String CREATOR_DEVELOPER_BONUS_SESSION_PREFIX = "creator_bonus_";
  private static final long CREATOR_PLANNER_BONUS_STUDY_MILLIS = 1_200_000L;
  private static final int CREATOR_PLANNER_BONUS_POINTS = 80;
  private static final String CREATOR_PLANNER_BONUS_MODE_ID = "settings_planner_bonus";
  private static final String CREATOR_PLANNER_BONUS_DIFFICULTY = "intermediate";
  private static final String CREATOR_PLANNER_BONUS_SESSION_PREFIX = "planner_bonus_";
  private static final String CREATOR_PLANNER_BONUS_DAY_KEY_PREFIX = "planner_bonus_day_";
  private static final long TAP_WINDOW_UNSET = -1L;

  @Nullable private AppSettingsStore appSettingsStore;
  @Nullable private LearningStudyTimeStore learningStudyTimeStore;
  @Nullable private LearningStudyTimeCloudRepository learningStudyTimeCloudRepository;
  @Nullable private LearningPointStore learningPointStore;
  @Nullable private LearningPointCloudRepository learningPointCloudRepository;

  private boolean bindingState;
  private boolean isLearningDataResetInProgress;
  private boolean isLearningMetricsResetInProgress;

  private LinearLayout layoutProfileNickname;
  private View cardProfileEmail;
  private LinearLayout layoutInitLearningData;
  private LinearLayout layoutInitLearningStreak;
  private LinearLayout layoutCreatorPlanner;
  private LinearLayout layoutCreatorDeveloper;
  private TextView tvProfileNicknameValue;
  private TextView tvProfileEmailValue;

  private TextView tvAppVersion;
  private TextView tvLogout;
  private int creatorPlannerTapCount;
  private long creatorPlannerWindowStartElapsedMs = TAP_WINDOW_UNSET;
  private int creatorDeveloperTapCount;
  private long creatorDeveloperWindowStartElapsedMs = TAP_WINDOW_UNSET;

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
    android.content.Context appContext = requireContext().getApplicationContext();
    appSettingsStore = new AppSettingsStore(appContext);
    learningStudyTimeStore = new LearningStudyTimeStore(appContext);
    learningStudyTimeCloudRepository = new LearningStudyTimeCloudRepository(appContext);
    learningPointStore = new LearningPointStore(appContext);
    learningPointCloudRepository = new LearningPointCloudRepository(appContext, learningPointStore);
    bindViews(view);
    setupListeners();
    renderSettings();
  }

  @Override
  public void onResume() {
    super.onResume();
    renderSettings();
  }

  private void bindViews(@NonNull View view) {
    layoutProfileNickname = view.findViewById(R.id.layout_profile_nickname);
    cardProfileEmail = view.findViewById(R.id.card_profile_email);
    layoutInitLearningData = view.findViewById(R.id.layout_init_learning_data);
    layoutInitLearningStreak = view.findViewById(R.id.layout_init_learning_streak);
    layoutCreatorPlanner = view.findViewById(R.id.layout_creator_planner);
    layoutCreatorDeveloper = view.findViewById(R.id.layout_creator_developer);
    tvProfileNicknameValue = view.findViewById(R.id.tv_profile_nickname_value);
    tvProfileEmailValue = view.findViewById(R.id.tv_profile_email_value);
    tvAppVersion = view.findViewById(R.id.tv_app_version);
    tvLogout = view.findViewById(R.id.tv_logout);

    if (tvAppVersion != null) {
      tvAppVersion.setText(BuildConfig.VERSION_NAME);
    }
  }

  private void setupListeners() {
    if (layoutProfileNickname != null) {
      layoutProfileNickname.setOnClickListener(v -> showNicknameEditDialog());
    }

    if (layoutInitLearningData != null) {
      layoutInitLearningData.setOnClickListener(v -> showLearningDataResetDialog());
    }

    if (layoutInitLearningStreak != null) {
      layoutInitLearningStreak.setOnClickListener(v -> showLearningMetricsResetDialog());
    }

    if (layoutCreatorPlanner != null) {
      layoutCreatorPlanner.setOnClickListener(v -> onCreatorPlannerCardTapped());
      logDebug("Bound planner card bonus listener");
    } else {
      logDebug("Planner card view not found; bonus listener not bound");
    }

    if (layoutCreatorDeveloper != null) {
      layoutCreatorDeveloper.setOnClickListener(v -> onCreatorDeveloperCardTapped());
      logDebug("Bound developer card bonus listener");
    } else {
      logDebug("Developer card view not found; bonus listener not bound");
    }

    if (tvLogout != null) {
      tvLogout.setOnClickListener(v -> showLogoutConfirmDialog());
    }
  }

  private void onCreatorPlannerCardTapped() {
    long nowElapsedMs = SystemClock.elapsedRealtime();
    boolean startsNewWindow =
        creatorPlannerWindowStartElapsedMs == TAP_WINDOW_UNSET
            || nowElapsedMs < creatorPlannerWindowStartElapsedMs
            || nowElapsedMs - creatorPlannerWindowStartElapsedMs > CREATOR_BONUS_WINDOW_MS;
    if (startsNewWindow) {
      creatorPlannerTapCount = 1;
      creatorPlannerWindowStartElapsedMs = nowElapsedMs;
      logDebug("Planner card tap window reset");
    } else {
      creatorPlannerTapCount++;
    }
    logDebug("Planner card tap progress=" + creatorPlannerTapCount + "/" + CREATOR_BONUS_TAP_TARGET);

    if (creatorPlannerTapCount >= CREATOR_BONUS_TAP_TARGET) {
      logDebug("Planner card bonus triggered");
      resetCreatorPlannerTapState();
      grantCreatorPlannerBonus();
    }
  }

  private void resetCreatorPlannerTapState() {
    creatorPlannerTapCount = 0;
    creatorPlannerWindowStartElapsedMs = TAP_WINDOW_UNSET;
  }

  private void onCreatorDeveloperCardTapped() {
    long nowElapsedMs = SystemClock.elapsedRealtime();
    boolean startsNewWindow =
        creatorDeveloperWindowStartElapsedMs == TAP_WINDOW_UNSET
            || nowElapsedMs < creatorDeveloperWindowStartElapsedMs
            || nowElapsedMs - creatorDeveloperWindowStartElapsedMs > CREATOR_BONUS_WINDOW_MS;
    if (startsNewWindow) {
      creatorDeveloperTapCount = 1;
      creatorDeveloperWindowStartElapsedMs = nowElapsedMs;
      logDebug("Developer card tap window reset");
    } else {
      creatorDeveloperTapCount++;
    }
    logDebug(
        "Developer card tap progress="
            + creatorDeveloperTapCount
            + "/"
            + CREATOR_BONUS_TAP_TARGET);

    if (creatorDeveloperTapCount >= CREATOR_BONUS_TAP_TARGET) {
      logDebug("Developer card bonus triggered");
      resetCreatorDeveloperTapState();
      grantCreatorDeveloperBonus();
      return;
    }
  }

  private void resetCreatorDeveloperTapState() {
    creatorDeveloperTapCount = 0;
    creatorDeveloperWindowStartElapsedMs = TAP_WINDOW_UNSET;
  }

  private void grantCreatorPlannerBonus() {
    LearningStudyTimeStore studyTimeStore = learningStudyTimeStore;
    LearningPointStore pointStore = learningPointStore;
    if (studyTimeStore == null || pointStore == null) {
      logDebug("Planner card bonus skipped due to null stores");
      return;
    }

    long nowEpochMs = System.currentTimeMillis();
    String bonusDayKey =
        CREATOR_PLANNER_BONUS_DAY_KEY_PREFIX + nowEpochMs + "_" + UUID.randomUUID();
    studyTimeStore.applyManualBonus(CREATOR_PLANNER_BONUS_STUDY_MILLIS, bonusDayKey);

    LearningStudyTimeCloudRepository studyTimeCloudRepository = learningStudyTimeCloudRepository;
    if (studyTimeCloudRepository != null) {
      studyTimeCloudRepository.applyManualBonusForCurrentUser(
          CREATOR_PLANNER_BONUS_STUDY_MILLIS, bonusDayKey);
    }

    pointStore.awardSessionIfNeeded(
        CREATOR_PLANNER_BONUS_SESSION_PREFIX + UUID.randomUUID(),
        new LearningPointAwardSpec(
            CREATOR_PLANNER_BONUS_MODE_ID,
            CREATOR_PLANNER_BONUS_DIFFICULTY,
            CREATOR_PLANNER_BONUS_POINTS,
            nowEpochMs));

    LearningPointCloudRepository pointCloudRepository = learningPointCloudRepository;
    if (pointCloudRepository != null) {
      pointCloudRepository.flushPendingForCurrentUser();
    }

    logDebug(
        "Planner card bonus granted: studyMillis="
            + CREATOR_PLANNER_BONUS_STUDY_MILLIS
            + ", points="
            + CREATOR_PLANNER_BONUS_POINTS
            + ", bonusDayKey="
            + bonusDayKey);
    showToastSafe(R.string.settings_creator_planner_bonus_toast);
  }

  private void grantCreatorDeveloperBonus() {
    LearningStudyTimeStore studyTimeStore = learningStudyTimeStore;
    LearningPointStore pointStore = learningPointStore;
    if (studyTimeStore == null || pointStore == null) {
      logDebug("Developer card bonus skipped due to null stores");
      return;
    }

    long nowEpochMs = System.currentTimeMillis();
    studyTimeStore.applyTimeBonus(CREATOR_DEVELOPER_BONUS_STUDY_MILLIS);

    LearningStudyTimeCloudRepository studyTimeCloudRepository = learningStudyTimeCloudRepository;
    if (studyTimeCloudRepository != null) {
      studyTimeCloudRepository.applyTimeBonusForCurrentUser(CREATOR_DEVELOPER_BONUS_STUDY_MILLIS);
    }

    pointStore.awardSessionIfNeeded(
        CREATOR_DEVELOPER_BONUS_SESSION_PREFIX + UUID.randomUUID(),
        new LearningPointAwardSpec(
            CREATOR_DEVELOPER_BONUS_MODE_ID,
            CREATOR_DEVELOPER_BONUS_DIFFICULTY,
            CREATOR_DEVELOPER_BONUS_POINTS,
            nowEpochMs));

    LearningPointCloudRepository pointCloudRepository = learningPointCloudRepository;
    if (pointCloudRepository != null) {
      pointCloudRepository.flushPendingForCurrentUser();
    }

    logDebug(
        "Developer card bonus granted: studyMillis="
            + CREATOR_DEVELOPER_BONUS_STUDY_MILLIS
            + ", points="
            + CREATOR_DEVELOPER_BONUS_POINTS);
    showToastSafe(R.string.settings_creator_bonus_toast);
  }

  private void showLogoutConfirmDialog() {
    if (!isAdded()) {
      return;
    }
    if (getChildFragmentManager().findFragmentByTag(TAG_LOGOUT_CONFIRM_DIALOG) != null) {
      return;
    }
    new LogoutConfirmDialog().show(getChildFragmentManager(), TAG_LOGOUT_CONFIRM_DIALOG);
  }

  @Override
  public void onLogoutConfirmed() {
    performLogout();
  }

  private void performLogout() {
    FirebaseAuth.getInstance().signOut();
    int clearedPreferenceCount =
        SharedPreferencesCleaner.clearAll(requireContext().getApplicationContext());
    logDebug("Cleared shared preferences files: " + clearedPreferenceCount);
    android.content.Intent intent =
        new android.content.Intent(requireContext(), LoginActivity.class);
    intent.setFlags(
        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
    startActivity(intent);
    requireActivity().finish();
  }

  private String getEffectiveNickname() {
    String storedNickname = appSettingsStore.getSettings().getUserNickname();
    if (!storedNickname.isEmpty()) {
      return storedNickname;
    }
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
      return user.getDisplayName();
    }
    return "학습자";
  }

  @Nullable
  private String getCurrentUserEmailOrNull() {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      return null;
    }
    String email = user.getEmail();
    if (email == null) {
      return null;
    }
    String trimmedEmail = email.trim();
    if (trimmedEmail.isEmpty()) {
      return null;
    }
    return trimmedEmail;
  }

  private void renderProfileEmail() {
    if (cardProfileEmail == null || tvProfileEmailValue == null) {
      return;
    }
    String userEmail = getCurrentUserEmailOrNull();
    if (userEmail == null) {
      cardProfileEmail.setVisibility(View.GONE);
      tvProfileEmailValue.setText("");
      return;
    }
    cardProfileEmail.setVisibility(View.VISIBLE);
    tvProfileEmailValue.setText(userEmail);
  }

  private void showNicknameEditDialog() {
    View dialogView =
        LayoutInflater.from(getContext()).inflate(R.layout.dialog_profile_nickname, null);

    EditText etNicknameInput = dialogView.findViewById(R.id.et_nickname_input);
    AppCompatButton btnCancel = dialogView.findViewById(R.id.btn_nickname_cancel);
    AppCompatButton btnSave = dialogView.findViewById(R.id.btn_nickname_save);

    String currentNickname = appSettingsStore.getSettings().getUserNickname();
    if (!currentNickname.isEmpty()) {
      etNicknameInput.setText(currentNickname);
      etNicknameInput.setSelection(currentNickname.length());
    }

    android.app.Dialog dialog = new android.app.Dialog(requireContext());
    dialog.setContentView(dialogView);
    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));

      // Set width to 90% of screen width
      android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
      int width = (int) (metrics.widthPixels * 0.9f);
      dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    btnCancel.setOnClickListener(v -> dialog.dismiss());

    btnSave.setOnClickListener(
        v -> {
          String newNickname = etNicknameInput.getText().toString().trim();
          appSettingsStore.setUserNickname(newNickname);
          if (tvProfileNicknameValue != null) {
            tvProfileNicknameValue.setText(getEffectiveNickname());
          }
          Toast.makeText(getContext(), "닉네임이 저장되었습니다.", Toast.LENGTH_SHORT).show();
          dialog.dismiss();
        });

    dialog.show();
  }

  private void renderSettings() {
    AppSettingsStore store = appSettingsStore;
    if (store == null) {
      return;
    }
    bindingState = true;

    if (tvProfileNicknameValue != null) {
      tvProfileNicknameValue.setText(getEffectiveNickname());
    }
    renderProfileEmail();

    bindingState = false;
  }

  private void showLearningDataResetDialog() {
    if (isLearningDataResetInProgress) {
      return;
    }
    if (getChildFragmentManager().findFragmentByTag(TAG_LEARNING_DATA_RESET_DIALOG) != null) {
      return;
    }
    LearningDataResetDialog dialog = new LearningDataResetDialog();
    dialog.show(getChildFragmentManager(), TAG_LEARNING_DATA_RESET_DIALOG);
  }

  private void showLearningMetricsResetDialog() {
    if (isLearningMetricsResetInProgress) {
      return;
    }
    if (getChildFragmentManager().findFragmentByTag(TAG_LEARNING_METRICS_RESET_DIALOG) != null) {
      return;
    }
    LearningMetricsResetDialog dialog = new LearningMetricsResetDialog();
    dialog.show(getChildFragmentManager(), TAG_LEARNING_METRICS_RESET_DIALOG);
  }

  @Override
  public void onLearningMetricsResetRequested(@NonNull LearningMetricsResetDialog dialog) {
    if (isLearningMetricsResetInProgress) {
      dialog.showLoading(false);
      return;
    }

    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      dialog.showLoading(false);
      showToastSafe(R.string.settings_learning_metrics_reset_login_required);
      return;
    }

    LearningStudyTimeStore studyTimeStore = learningStudyTimeStore;
    LearningPointStore pointStore = learningPointStore;
    LearningStudyTimeCloudRepository studyTimeCloudRepository = learningStudyTimeCloudRepository;
    LearningPointCloudRepository pointCloudRepository = learningPointCloudRepository;
    if (studyTimeStore == null
        || pointStore == null
        || studyTimeCloudRepository == null
        || pointCloudRepository == null) {
      dialog.showLoading(false);
      showToastSafe(R.string.settings_learning_metrics_reset_failed);
      return;
    }

    setLearningMetricsResetInProgress(true);
    boolean[] taskResults = new boolean[2];
    int[] completedCount = new int[1];
    studyTimeCloudRepository.resetMetricsForCurrentUser(
        success ->
            onLearningMetricsResetTaskCompleted(
                dialog, 0, success, taskResults, completedCount, studyTimeStore, pointStore));
    pointCloudRepository.resetTotalPointsForCurrentUser(
        success ->
            onLearningMetricsResetTaskCompleted(
                dialog, 1, success, taskResults, completedCount, studyTimeStore, pointStore));
  }

  private void onLearningMetricsResetTaskCompleted(
      @NonNull LearningMetricsResetDialog dialog,
      int taskIndex,
      boolean success,
      @NonNull boolean[] taskResults,
      @NonNull int[] completedCount,
      @NonNull LearningStudyTimeStore studyTimeStore,
      @NonNull LearningPointStore pointStore) {
    taskResults[taskIndex] = success;
    completedCount[0]++;
    if (completedCount[0] < taskResults.length) {
      return;
    }

    setLearningMetricsResetInProgress(false);
    dialog.showLoading(false);

    if (taskResults[0] && taskResults[1]) {
      studyTimeStore.resetAllMetrics();
      pointStore.resetAllPoints();
      if (dialog.isAdded()) {
        dialog.dismiss();
      }
      showToastSafe(R.string.settings_learning_metrics_reset_success);
      return;
    }

    showToastSafe(R.string.settings_learning_metrics_reset_failed);
  }

  @Override
  public void onLearningDataResetRequested(
      @NonNull LearningDataRetentionPolicy.Preset preset, @NonNull LearningDataResetDialog dialog) {
    if (isLearningDataResetInProgress) {
      dialog.showLoading(false);
      return;
    }

    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      dialog.showLoading(false);
      showToastSafe(R.string.settings_learning_reset_login_required);
      return;
    }

    setLearningDataResetInProgress(true);
    long nowEpochMs = System.currentTimeMillis();

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.getUid())
        .collection("saved_cards")
        .get()
        .addOnSuccessListener(
            querySnapshot -> {
              List<DocumentSnapshot> docsToDelete = new ArrayList<>();
              for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                if (shouldDeleteDocument(document, preset, nowEpochMs)) {
                  docsToDelete.add(document);
                }
              }

              if (docsToDelete.isEmpty()) {
                setLearningDataResetInProgress(false);
                dialog.showLoading(false);
                if (dialog.isAdded()) {
                  dialog.dismiss();
                }
                showToastSafe(R.string.settings_learning_reset_nothing_to_delete);
                return;
              }

              deleteSavedCardsInChunks(docsToDelete, 0, 0, dialog);
            })
        .addOnFailureListener(
            e -> {
              setLearningDataResetInProgress(false);
              dialog.showLoading(false);
              logDebug("Failed to query learning data for reset: " + e.getMessage());
              showToastSafe(R.string.settings_learning_reset_failed);
            });
  }

  private boolean shouldDeleteDocument(
      @NonNull DocumentSnapshot document,
      @NonNull LearningDataRetentionPolicy.Preset preset,
      long nowEpochMs) {
    if (preset == LearningDataRetentionPolicy.Preset.DELETE_ALL) {
      return true;
    }
    Long timestamp = document.getLong("timestamp");
    return LearningDataRetentionPolicy.shouldDelete(timestamp, preset, nowEpochMs);
  }

  private void deleteSavedCardsInChunks(
      @NonNull List<DocumentSnapshot> docsToDelete,
      int startIndex,
      int deletedCount,
      @NonNull LearningDataResetDialog dialog) {
    int endIndex = Math.min(startIndex + FIRESTORE_BATCH_DELETE_LIMIT, docsToDelete.size());
    WriteBatch batch = FirebaseFirestore.getInstance().batch();

    for (int i = startIndex; i < endIndex; i++) {
      batch.delete(docsToDelete.get(i).getReference());
    }

    batch
        .commit()
        .addOnSuccessListener(
            unused -> {
              int nextDeletedCount = deletedCount + (endIndex - startIndex);
              if (endIndex >= docsToDelete.size()) {
                onLearningDataResetCompleted(dialog, nextDeletedCount);
                return;
              }
              deleteSavedCardsInChunks(docsToDelete, endIndex, nextDeletedCount, dialog);
            })
        .addOnFailureListener(
            e -> {
              setLearningDataResetInProgress(false);
              dialog.showLoading(false);
              logDebug("Failed to delete learning data batch: " + e.getMessage());
              if (deletedCount > 0) {
                showToastSafe(
                    getString(R.string.settings_learning_reset_partial_failed, deletedCount));
                return;
              }
              showToastSafe(R.string.settings_learning_reset_failed);
            });
  }

  private void onLearningDataResetCompleted(
      @NonNull LearningDataResetDialog dialog, int deletedCount) {
    setLearningDataResetInProgress(false);
    dialog.showLoading(false);
    if (dialog.isAdded()) {
      dialog.dismiss();
    }
    showToastSafe(getString(R.string.settings_learning_reset_success_count, deletedCount));
  }

  private void setLearningDataResetInProgress(boolean inProgress) {
    isLearningDataResetInProgress = inProgress;
    if (layoutInitLearningData != null) {
      layoutInitLearningData.setEnabled(!inProgress);
      layoutInitLearningData.setAlpha(inProgress ? 0.6f : 1f);
    }
  }

  private void setLearningMetricsResetInProgress(boolean inProgress) {
    isLearningMetricsResetInProgress = inProgress;
    if (layoutInitLearningStreak != null) {
      layoutInitLearningStreak.setEnabled(!inProgress);
      layoutInitLearningStreak.setAlpha(inProgress ? 0.6f : 1f);
    }
  }

  private void showToastSafe(int messageResId) {
    if (!isAdded()) {
      return;
    }
    Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show();
  }

  private void showToastSafe(@NonNull String message) {
    if (!isAdded()) {
      return;
    }
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
  }

  private void logDebug(@NonNull String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }
}
