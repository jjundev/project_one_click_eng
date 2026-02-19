package com.example.test.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.test.BuildConfig;
import com.example.test.R;
import com.example.test.dialog.DialogueLearningSettingDialog;
import com.example.test.dialog.ExitConfirmDialog;
import com.example.test.fragment.DialogueLearningFragment;
import com.example.test.fragment.DialogueQuizFragment;
import com.example.test.fragment.DialogueSummaryFragment;

public class DialogueLearningActivity extends AppCompatActivity
    implements DialogueLearningFragment.OnScriptProgressListener,
        ExitConfirmDialog.OnExitConfirmListener {
  private static final String TAG = "JOB_J-20260216-004";
  private static final String DIALOG_TAG_LEARNING_SETTINGS = "DialogueLearningSettingDialog";

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
    btnLeft.setOnClickListener(
        v -> {
          // backPress와 동일한 효과
          getOnBackPressedDispatcher().onBackPressed();
        });
    ImageButton btnRight = findViewById(R.id.btn_right);
    btnRight.setOnClickListener(v -> showDialogueLearningSettingDialog());

    // backPress 콜백 등록
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                // 현재 컨테이너에 있는 프래그먼트 확인
                androidx.fragment.app.Fragment currentFragment =
                    getSupportFragmentManager().findFragmentById(R.id.fragment_container);

                if (currentFragment instanceof DialogueSummaryFragment
                    || currentFragment instanceof DialogueQuizFragment) {
                  // Summary/Quiz Fragment인 경우 이전 화면(LearningFragment)으로 돌아감
                  getSupportFragmentManager().popBackStack();
                } else {
                  // 그 외(LearningFragment 등)인 경우 종료 확인 다이얼로그 표시
                  showExitDialog();
                }
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
    ExitConfirmDialog dialog = new ExitConfirmDialog();
    dialog.show(getSupportFragmentManager(), "ExitConfirmDialog");
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
