package com.jjundev.oneclickeng.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.dialog.DialogueLearningSettingDialog;
import com.jjundev.oneclickeng.dialog.ExitConfirmDialog;
import com.jjundev.oneclickeng.learning.dialoguelearning.DialogueLearningFragment;
import com.jjundev.oneclickeng.learning.dialoguelearning.DialogueSummaryFragment;
import java.util.List;

public class DialogueLearningActivity extends LearningActivity
    implements DialogueLearningFragment.OnScriptProgressListener,
        ExitConfirmDialog.OnExitConfirmListener {
  private static final String TAG = "JOB_J-20260216-004";
  private static final String DIALOG_TAG_LEARNING_SETTINGS = "DialogueLearningSettingDialog";
  private static final String DIALOG_TAG_EXIT_CONFIRM = "ExitConfirmDialog";

  private ProgressBar progressBar;
  private TextView tvProgress;
  private TextView tvTitle;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    EdgeToEdge.enable(this);
    setContentView(R.layout.activity_dialogue_learning);

    // Initialize Progress Views
    progressBar = findViewById(R.id.progress_bar);
    tvProgress = findViewById(R.id.tv_progress);
    tvTitle = findViewById(R.id.tv_title);

    // 키보드(IME) 인셋 처리 - 키보드가 올라오면 화면이 위로 밀리도록
    View rootView = findViewById(android.R.id.content);
    ViewCompat.setOnApplyWindowInsetsListener(
        rootView,
        (v, windowInsets) -> {
          Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
          Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

          // 키보드가 올라오면 bottom에 키보드 높이만큼 패딩 적용
          v.setPadding(
              systemBarInsets.left,
              systemBarInsets.top,
              systemBarInsets.right,
              Math.max(imeInsets.bottom, systemBarInsets.bottom));

          return WindowInsetsCompat.CONSUMED;
        });

    // Toolbar의 btn_left 클릭 리스너 설정
    ImageButton btnLeft = findViewById(R.id.btn_left);
    btnLeft.setOnClickListener(v -> showExitDialog());
    ImageButton btnRight = findViewById(R.id.btn_right);
    btnRight.setOnClickListener(v -> showDialogueLearningSettingDialog());

    // backPress 콜백 등록
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                showExitDialog();
              }
            });

    // ScriptChatFragment를 FragmentContainerView에 로드
    if (savedInstanceState == null) {
      String scriptData = getIntent().getStringExtra("SCRIPT_DATA");

      DialogueLearningFragment fragment = new DialogueLearningFragment();
      if (scriptData != null) {
        Bundle args = new Bundle();
        args.putString("SCRIPT_DATA", scriptData);
        fragment.setArguments(args);
      }

      getSupportFragmentManager()
          .beginTransaction()
          .setCustomAnimations(
              R.anim.slide_in_right,
              R.anim.slide_out_left,
              R.anim.slide_in_left,
              R.anim.slide_out_right)
          .replace(R.id.fragment_container, fragment)
          .commit();
    }
  }

  @Override
  public void onProgressUpdate(int currentStep, int totalSteps) {
    if (progressBar != null) {
      progressBar.setMax(totalSteps);
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        progressBar.setProgress(currentStep, true);
      } else {
        progressBar.setProgress(currentStep);
      }
    }
    if (tvProgress != null) {
      tvProgress.setText(currentStep + " of " + totalSteps);
    }
  }

  @Override
  public void onMetadataLoaded(String topic, String opponentName) {
    if (tvTitle != null) {
      tvTitle.setText(topic);
    }
  }

  @Override
  public void onConfirmExit() {
    finish();
  }

  // 커스텀 다이얼로그 표시
  private void showExitDialog() {
    if (isFinishing() || isDestroyed()) {
      logDebug("exit dialog open skipped: activity finishing or destroyed");
      return;
    }
    if (getSupportFragmentManager().isStateSaved()) {
      logDebug("exit dialog open skipped: fragment manager state already saved");
      return;
    }
    androidx.fragment.app.Fragment existingDialog =
        getSupportFragmentManager().findFragmentByTag(DIALOG_TAG_EXIT_CONFIRM);
    if (existingDialog != null && existingDialog.isAdded()) {
      logDebug("exit dialog open skipped: dialog already visible");
      return;
    }

    String overrideMessage =
        isSummaryFragmentVisible() ? getString(R.string.dialog_exit_message_back_to_main) : null;

    logDebug("exit dialog open");
    ExitConfirmDialog.newInstance(overrideMessage)
        .show(getSupportFragmentManager(), DIALOG_TAG_EXIT_CONFIRM);
  }

  private boolean isSummaryFragmentVisible() {
    List<androidx.fragment.app.Fragment> fragments = getSupportFragmentManager().getFragments();
    for (int i = fragments.size() - 1; i >= 0; i--) {
      androidx.fragment.app.Fragment fragment = fragments.get(i);
      if (fragment == null || fragment.getId() != R.id.fragment_container) {
        continue;
      }
      if (!fragment.isAdded() || !fragment.isVisible() || fragment.isHidden()) {
        continue;
      }
      return fragment instanceof DialogueSummaryFragment;
    }
    return false;
  }

  private void showDialogueLearningSettingDialog() {
    if (isFinishing() || isDestroyed()) {
      logDebug("settings dialog open skipped: activity finishing or destroyed");
      return;
    }
    if (getSupportFragmentManager().isStateSaved()) {
      logDebug("settings dialog open skipped: fragment manager state already saved");
      return;
    }
    androidx.fragment.app.Fragment existingDialog =
        getSupportFragmentManager().findFragmentByTag(DIALOG_TAG_LEARNING_SETTINGS);
    if (existingDialog != null && existingDialog.isAdded()) {
      logDebug("settings dialog open skipped: dialog already visible");
      return;
    }

    logDebug("settings dialog open");
    new DialogueLearningSettingDialog()
        .show(getSupportFragmentManager(), DIALOG_TAG_LEARNING_SETTINGS);
  }

  private void logDebug(String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }
}
