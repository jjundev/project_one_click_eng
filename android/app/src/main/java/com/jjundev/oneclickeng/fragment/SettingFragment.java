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
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
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

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

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

  @Nullable
  private AppSettingsStore appSettingsStore;
  @Nullable
  private LearningStudyTimeStore learningStudyTimeStore;
  @Nullable
  private LearningStudyTimeCloudRepository learningStudyTimeCloudRepository;
  @Nullable
  private LearningPointStore learningPointStore;
  @Nullable
  private LearningPointCloudRepository learningPointCloudRepository;

  private boolean bindingState;
  private boolean isLearningDataResetInProgress;
  private boolean isLearningMetricsResetInProgress;

  private LinearLayout layoutProfileNickname;
  private View cardProfileEmail;
  private View cardChangePassword;
  private LinearLayout layoutChangePassword;
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

  private TextView tvCreditRemainingValue;
  private LinearLayout layoutChargeCredit;
  private LinearLayout layoutCouponInput;

  private RewardedAd rewardedAd;
  private boolean isRewardEarned = false;
  private boolean isWaitingForAd = false;
  private android.app.Dialog chargeCreditDialog; // 기존 다이얼로그를 멤버 변수로 참조
  private int adRetryAttempt = 0;
  private final android.os.Handler adRetryHandler = new android.os.Handler(android.os.Looper.getMainLooper());

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
    loadRewardedAd();
  }

  @Override
  public void onResume() {
    super.onResume();
    renderSettings();
  }

  private void bindViews(@NonNull View view) {
    layoutProfileNickname = view.findViewById(R.id.layout_profile_nickname);
    cardProfileEmail = view.findViewById(R.id.card_profile_email);
    cardChangePassword = view.findViewById(R.id.card_change_password);
    layoutChangePassword = view.findViewById(R.id.layout_change_password);
    layoutInitLearningData = view.findViewById(R.id.layout_init_learning_data);
    layoutInitLearningStreak = view.findViewById(R.id.layout_init_learning_streak);
    layoutCreatorPlanner = view.findViewById(R.id.layout_creator_planner);
    layoutCreatorDeveloper = view.findViewById(R.id.layout_creator_developer);
    tvProfileNicknameValue = view.findViewById(R.id.tv_profile_nickname_value);
    tvProfileEmailValue = view.findViewById(R.id.tv_profile_email_value);
    tvAppVersion = view.findViewById(R.id.tv_app_version);
    tvLogout = view.findViewById(R.id.tv_logout);

    tvCreditRemainingValue = view.findViewById(R.id.tv_credit_remaining_value);
    layoutChargeCredit = view.findViewById(R.id.layout_charge_credit);
    layoutCouponInput = view.findViewById(R.id.layout_coupon_input);

    if (tvAppVersion != null) {
      tvAppVersion.setText(BuildConfig.VERSION_NAME);
    }
  }

  private void setupListeners() {
    if (layoutProfileNickname != null) {
      layoutProfileNickname.setOnClickListener(v -> showNicknameEditDialog());
    }

    if (layoutChangePassword != null) {
      layoutChangePassword.setOnClickListener(v -> showChangePasswordDialog());
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

    if (layoutChargeCredit != null) {
      layoutChargeCredit.setOnClickListener(v -> showChargeCreditDialog());
    }

    if (layoutCouponInput != null) {
      layoutCouponInput.setOnClickListener(v -> showCouponInputDialog());
    }

    if (tvLogout != null) {
      tvLogout.setOnClickListener(v -> showLogoutConfirmDialog());
    }
  }

  private void onCreatorPlannerCardTapped() {
    long nowElapsedMs = SystemClock.elapsedRealtime();
    boolean startsNewWindow = creatorPlannerWindowStartElapsedMs == TAP_WINDOW_UNSET
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
    boolean startsNewWindow = creatorDeveloperWindowStartElapsedMs == TAP_WINDOW_UNSET
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
    String bonusDayKey = CREATOR_PLANNER_BONUS_DAY_KEY_PREFIX + nowEpochMs + "_" + UUID.randomUUID();
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
    int clearedPreferenceCount = SharedPreferencesCleaner.clearAll(requireContext().getApplicationContext());
    logDebug("Cleared shared preferences files: " + clearedPreferenceCount);
    android.content.Intent intent = new android.content.Intent(requireContext(), LoginActivity.class);
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
      if (cardChangePassword != null)
        cardChangePassword.setVisibility(View.GONE);
      tvProfileEmailValue.setText("");
      return;
    }

    boolean isEmailProvider = false;
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user != null) {
      for (com.google.firebase.auth.UserInfo userInfo : user.getProviderData()) {
        if ("password".equals(userInfo.getProviderId())) {
          isEmailProvider = true;
          break;
        }
      }
    }

    cardProfileEmail.setVisibility(View.VISIBLE);
    tvProfileEmailValue.setText(userEmail);

    if (cardChangePassword != null) {
      cardChangePassword.setVisibility(isEmailProvider ? View.VISIBLE : View.GONE);
    }
  }

  private void showChangePasswordDialog() {
    String userEmail = getCurrentUserEmailOrNull();
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (userEmail == null || user == null) {
      showToastSafe("이메일로 로그인된 사용자 정보를 찾을 수 없어요");
      return;
    }

    View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_change_password, null);

    EditText etCurrentPassword = dialogView.findViewById(R.id.et_current_password);
    View cardCurrentPassword = dialogView.findViewById(R.id.card_current_password);
    View cardConfirmedPassword = dialogView.findViewById(R.id.card_confirmed_password);
    TextView tvConfirmedPassword = dialogView.findViewById(R.id.tv_confirmed_password);
    EditText etNewPassword = dialogView.findViewById(R.id.et_new_password);
    EditText etConfirmPassword = dialogView.findViewById(R.id.et_confirm_password);
    View layoutNewPasswordContainer = dialogView.findViewById(R.id.layout_new_password_container);
    TextView tvError = dialogView.findViewById(R.id.tv_password_error);
    AppCompatButton btnCancel = dialogView.findViewById(R.id.btn_change_password_cancel);
    AppCompatButton btnSave = dialogView.findViewById(R.id.btn_change_password_save);

    android.app.Dialog dialog = new android.app.Dialog(requireContext());
    dialog.setContentView(dialogView);
    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
      android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
      int width = (int) (metrics.widthPixels * 0.9f);
      dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    btnCancel.setOnClickListener(v -> dialog.dismiss());

    btnSave.setOnClickListener(v -> {
      // 키보드 숨기기
      android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext()
          .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
      if (imm != null) {
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
      }

      tvError.setVisibility(View.GONE);

      boolean isCurrentPasswordVerified = layoutNewPasswordContainer.getVisibility() == View.VISIBLE;

      if (!isCurrentPasswordVerified) {
        // Step 1: 현재 비밀번호 확인
        String currentPassword = etCurrentPassword.getText().toString();
        if (currentPassword.isEmpty()) {
          tvError.setText("현재 비밀번호를 입력해주세요.");
          tvError.setVisibility(View.VISIBLE);
          return;
        }

        btnSave.setEnabled(false);
        btnSave.setText("확인중...");

        AuthCredential credential = EmailAuthProvider.getCredential(userEmail, currentPassword);
        user.reauthenticate(credential).addOnCompleteListener(reauthTask -> {
          if (reauthTask.isSuccessful()) {
            // 확인 완료: 현재 비밀번호 입력창 숨기고 일반 텍스트로 표시, 새 비밀번호 입력창 표시
            cardCurrentPassword.setVisibility(View.GONE);
            tvConfirmedPassword.setText(currentPassword);
            cardConfirmedPassword.setVisibility(View.VISIBLE);

            layoutNewPasswordContainer.setVisibility(View.VISIBLE);
            btnSave.setText("변경");
            btnSave.setEnabled(true);
            tvError.setVisibility(View.GONE);
          } else {
            btnSave.setEnabled(true);
            btnSave.setText("확인");
            tvError.setText("현재 비밀번호가 틀렸어요.");
            tvError.setVisibility(View.VISIBLE);
          }
        });

      } else {
        // Step 2: 새 비밀번호 변경
        String newPassword = etNewPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();
        String currentPasswordText = tvConfirmedPassword.getText().toString();

        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
          tvError.setText("새 비밀번호를 모두 입력해주세요.");
          tvError.setVisibility(View.VISIBLE);
          return;
        }

        if (newPassword.equals(currentPasswordText)) {
          tvError.setText("이전 비밀번호와 동일한 비밀번호에요.");
          tvError.setVisibility(View.VISIBLE);
          return;
        }

        if (newPassword.length() < 6) {
          tvError.setText("새 비밀번호는 6자 이상이어야 합니다.");
          tvError.setVisibility(View.VISIBLE);
          return;
        }

        if (!newPassword.equals(confirmPassword)) {
          tvError.setText("새 비밀번호가 일치하지 않아요.");
          tvError.setVisibility(View.VISIBLE);
          return;
        }

        btnSave.setEnabled(false);
        btnSave.setText("처리중...");

        user.updatePassword(newPassword).addOnCompleteListener(updateTask -> {
          if (updateTask.isSuccessful()) {
            showToastSafe("비밀번호가 성공적으로 변경되었어요");
            dialog.dismiss();
          } else {
            btnSave.setEnabled(true);
            btnSave.setText("변경");
            String errorMsg = updateTask.getException() != null ? updateTask.getException().getMessage()
                : "알 수 없는 오류";
            tvError.setText("비밀번호 변경 실패: " + errorMsg);
            tvError.setVisibility(View.VISIBLE);
          }
        });
      }
    });

    dialog.show();
  }

  private void showCouponInputDialog() {
    View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_input_coupon, null);

    EditText etCouponInput = dialogView.findViewById(R.id.et_coupon_input);
    TextView tvError = dialogView.findViewById(R.id.tv_coupon_error);
    AppCompatButton btnCancel = dialogView.findViewById(R.id.btn_coupon_cancel);
    AppCompatButton btnApply = dialogView.findViewById(R.id.btn_coupon_apply);

    android.app.Dialog dialog = new android.app.Dialog(requireContext());
    dialog.setContentView(dialogView);
    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
      android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
      int width = (int) (metrics.widthPixels * 0.9f);
      dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    btnCancel.setOnClickListener(v -> dialog.dismiss());

    btnApply.setOnClickListener(v -> {
      String code = etCouponInput.getText().toString().trim();
      if (code.isEmpty()) {
        tvError.setText("쿠폰 코드를 입력해주세요.");
        tvError.setVisibility(View.VISIBLE);
        return;
      }

      // Hide keyboard
      android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext()
          .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
      if (imm != null) {
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
      }

      tvError.setVisibility(View.GONE);
      btnApply.setEnabled(false);
      btnApply.setText("확인중...");

      FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
      if (user == null) {
        tvError.setText("로그인 정보를 확인할 수 없어요.");
        tvError.setVisibility(View.VISIBLE);
        btnApply.setEnabled(true);
        btnApply.setText("적용");
        return;
      }

      FirebaseFirestore db = FirebaseFirestore.getInstance();
      db.collection("users").document(user.getUid()).get().addOnCompleteListener(task -> {
        if (task.isSuccessful() && task.getResult() != null) {
          DocumentSnapshot userDoc = task.getResult();
          List<String> usedCodes = (List<String>) userDoc.get("used_code");
          if (usedCodes != null && usedCodes.contains(code)) {
            tvError.setText("이미 사용된 쿠폰입니다.");
            tvError.setVisibility(View.VISIBLE);
            btnApply.setEnabled(true);
            btnApply.setText("적용");
            return;
          }

          db.collection("coupons").whereEqualTo("code", code).get().addOnCompleteListener(couponTask -> {
            if (couponTask.isSuccessful() && couponTask.getResult() != null && !couponTask.getResult().isEmpty()) {
              DocumentSnapshot couponDoc = couponTask.getResult().getDocuments().get(0);
              Long rewardCreditObj = couponDoc.getLong("reward_credit");
              long rewardCredit = rewardCreditObj != null ? rewardCreditObj : 0L;

              WriteBatch batch = db.batch();
              com.google.firebase.firestore.DocumentReference userRef = db.collection("users").document(user.getUid());

              batch.update(userRef, "credit", com.google.firebase.firestore.FieldValue.increment(rewardCredit));
              batch.update(userRef, "used_code", com.google.firebase.firestore.FieldValue.arrayUnion(code));

              batch.commit().addOnCompleteListener(updateTask -> {
                if (updateTask.isSuccessful()) {
                  showToastSafe(rewardCredit + " 크레딧이 지급되었어요");
                  dialog.dismiss();
                  fetchUserCredit(); // Refresh UI
                } else {
                  tvError.setText("보상 지급에 실패했어요.");
                  tvError.setVisibility(View.VISIBLE);
                  btnApply.setEnabled(true);
                  btnApply.setText("적용");
                }
              });
            } else {
              tvError.setText("존재하지 않거나 유효하지 않은 쿠폰입니다.");
              tvError.setVisibility(View.VISIBLE);
              btnApply.setEnabled(true);
              btnApply.setText("적용");
            }
          });
        } else {
          tvError.setText("사용자 정보를 확인하는 중 오류가 발생했습니다.");
          tvError.setVisibility(View.VISIBLE);
          btnApply.setEnabled(true);
          btnApply.setText("적용");
        }
      });
    });

    dialog.show();
  }

  private void showChargeCreditDialog() {
    View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_charge_credit, null);

    View layoutContent = dialogView.findViewById(R.id.layout_content);
    View layoutLoading = dialogView.findViewById(R.id.layout_loading);
    AppCompatButton btnCancel = dialogView.findViewById(R.id.btn_charge_cancel);
    AppCompatButton btnAd = dialogView.findViewById(R.id.btn_charge_ad);

    chargeCreditDialog = new android.app.Dialog(requireContext());
    chargeCreditDialog.setContentView(dialogView);
    if (chargeCreditDialog.getWindow() != null) {
      chargeCreditDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
      android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
      int width = (int) (metrics.widthPixels * 0.9f);
      chargeCreditDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // 다이얼로그 닫힐 때 상태 리셋
    chargeCreditDialog.setOnDismissListener(d -> {
      isWaitingForAd = false;
      chargeCreditDialog = null;
    });

    btnCancel.setOnClickListener(v -> chargeCreditDialog.dismiss());
    btnAd.setOnClickListener(v -> {
      if (rewardedAd != null && isAdded()) {
        chargeCreditDialog.dismiss();
        isRewardEarned = false; // 보상을 받기 전 초기화
        rewardedAd.show(requireActivity(), rewardItem -> {
          // 보상 콜백
          isRewardEarned = true;
          increaseCredit();
        });
      } else {
        // 광고 준비 안 됨: 기존 다이얼로그 내부에서 UI 전환 (로딩 표시)
        isWaitingForAd = true;
        layoutContent.setVisibility(View.GONE);
        layoutLoading.setVisibility(View.VISIBLE);
        chargeCreditDialog.setCancelable(false); // 로딩 중에는 백버튼 등으로 안 닫히게 처리

        if (adRetryAttempt == 0) { // 재시도 중이 아니라면 새로 로드 요청
          loadRewardedAd();
        }
      }
    });

    chargeCreditDialog.show();
  }

  private void loadRewardedAd() {
    AdRequest adRequest = new AdRequest.Builder().build();
    String adUnitId = BuildConfig.DEBUG ? "ca-app-pub-3940256099942544/5224354917"
        : BuildConfig.ADMOB_REWARDED_AD_UNIT_ID;
    logDebug("Loading rewarded ad with Unit ID: " + adUnitId);
    RewardedAd.load(requireContext(), adUnitId,
        adRequest, new RewardedAdLoadCallback() {
          @Override
          public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
            logDebug("Failed to load rewarded ad: " + loadAdError.getMessage());
            rewardedAd = null;

            // Exponential Backoff 재시도 로직
            adRetryAttempt++;
            long retryDelayMillis = (long) Math.pow(2, Math.min(6, adRetryAttempt)) * 1000L;
            if (adRetryAttempt <= 5) {
              if (isWaitingForAd) {
                // 대기 중인 상태면 재시도 사실을 로그나 토스트로 알릴 필요는 없고 그냥 백오프로 로드
                logDebug("Retrying ad load in " + retryDelayMillis + "ms (Attempt " + adRetryAttempt + ")");
              }
              adRetryHandler.postDelayed(() -> loadRewardedAd(), retryDelayMillis);
            } else {
              // 5번 이상 재시도 실패 시 완전히 포기
              if (isWaitingForAd) {
                isWaitingForAd = false;
                if (chargeCreditDialog != null && chargeCreditDialog.isShowing()) {
                  chargeCreditDialog.dismiss();
                }
                if (isAdded()) {
                  showToastSafe("광고를 불러오는 데 실패했어요. 나중에 다시 시도해주세요.");
                }
              }
            }
          }

          @Override
          public void onAdLoaded(@NonNull RewardedAd ad) {
            logDebug("Rewarded ad loaded.");
            rewardedAd = ad;
            adRetryAttempt = 0; // 성공 시 재시도 횟수 초기화
            setFullScreenContentCallback();

            // 만약 사용자가 로딩 다이얼로그를 띄운 채 대기 중이었다면
            if (isWaitingForAd && isAdded()) {
              isWaitingForAd = false;
              if (chargeCreditDialog != null && chargeCreditDialog.isShowing()) {
                chargeCreditDialog.dismiss();
              }

              isRewardEarned = false;
              rewardedAd.show(requireActivity(), rewardItem -> {
                isRewardEarned = true;
                increaseCredit();
              });
            }
          }
        });
  }

  private void setFullScreenContentCallback() {
    if (rewardedAd == null)
      return;
    rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
      @Override
      public void onAdShowedFullScreenContent() {
        rewardedAd = null; // 표시 직후 초기화 (재사용 불가)
      }

      @Override
      public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
        logDebug("Ad failed to show: " + adError.getMessage());
      }

      @Override
      public void onAdDismissedFullScreenContent() {
        logDebug("Ad dismissed");
        if (!isRewardEarned && isAdded()) {
          // 사용자가 중간에 광고를 닫아 보상을 받지 못했을 때 안내
          showToastSafe("광고 시청을 완료하지 않아 크레딧이 지급되지 않았어요");
        }
        loadRewardedAd(); // 다음 번을 대비하여 재생성
      }
    });
  }

  private void increaseCredit() {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      showToastSafe("로그인 정보를 확인할 수 없어요");
      return;
    }

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.getUid())
        .update("credit", com.google.firebase.firestore.FieldValue.increment(1))
        .addOnSuccessListener(aVoid -> {
          if (isAdded()) {
            showToastSafe("1 크레딧이 충전되었어요");
            fetchUserCredit(); // UI 업데이트
          }
        })
        .addOnFailureListener(e -> {
          logDebug("Failed to add credit: " + e.getMessage());
          if (isAdded()) {
            showToastSafe("크레딧 충전에 실패했어요");
          }
        });
  }

  private void showNicknameEditDialog() {
    View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_profile_nickname, null);

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
          Toast.makeText(getContext(), "닉네임이 저장되었어요", Toast.LENGTH_SHORT).show();
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
    fetchUserCredit();

    bindingState = false;
  }

  private void fetchUserCredit() {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null || tvCreditRemainingValue == null) {
      return;
    }

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.getUid())
        .get()
        .addOnSuccessListener(documentSnapshot -> {
          if (!isAdded())
            return;
          if (documentSnapshot.exists() && documentSnapshot.contains("credit")) {
            Long credit = documentSnapshot.getLong("credit");
            if (credit != null) {
              tvCreditRemainingValue.setText(String.valueOf(credit));
            } else {
              tvCreditRemainingValue.setText("0");
            }
          } else {
            // 저장된 크레딧이 없을 경우 0으로 표시하고 Firebase에 초기값 저장 (필요 시)
            tvCreditRemainingValue.setText("0");
            saveInitialCreditIfMissing(user.getUid());
          }
        })
        .addOnFailureListener(e -> {
          logDebug("Failed to fetch user credit: " + e.getMessage());
          if (isAdded() && tvCreditRemainingValue != null) {
            tvCreditRemainingValue.setText("-");
          }
        });
  }

  private void saveInitialCreditIfMissing(String uid) {
    java.util.Map<String, Object> data = new java.util.HashMap<>();
    data.put("credit", 0L);
    FirebaseFirestore.getInstance()
        .collection("users")
        .document(uid)
        .set(data, com.google.firebase.firestore.SetOptions.merge())
        .addOnFailureListener(e -> logDebug("Failed to set initial credit: " + e.getMessage()));
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
        success -> onLearningMetricsResetTaskCompleted(
            dialog, 0, success, taskResults, completedCount, studyTimeStore, pointStore));
    pointCloudRepository.resetTotalPointsForCurrentUser(
        success -> onLearningMetricsResetTaskCompleted(
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
