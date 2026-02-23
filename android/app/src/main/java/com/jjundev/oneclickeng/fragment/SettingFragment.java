package com.jjundev.oneclickeng.fragment;

import android.os.Bundle;
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
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import com.jjundev.oneclickeng.settings.LearningDataRetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public class SettingFragment extends Fragment
    implements LearningDataResetDialog.OnLearningDataResetListener {
  private static final String TAG = "SettingFragment";
  private static final String TAG_LEARNING_DATA_RESET_DIALOG = "LearningDataResetDialog";
  private static final int FIRESTORE_BATCH_DELETE_LIMIT = 450;

  @Nullable private AppSettingsStore appSettingsStore;

  private boolean bindingState;
  private boolean isLearningDataResetInProgress;

  private LinearLayout layoutProfileNickname;
  private LinearLayout layoutInitLearningData;
  private TextView tvProfileNicknameValue;

  private TextView tvAppVersion;
  private TextView tvLogout;

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
    setupListeners();
    renderSettings();
  }

  private void bindViews(@NonNull View view) {
    layoutProfileNickname = view.findViewById(R.id.layout_profile_nickname);
    layoutInitLearningData = view.findViewById(R.id.layout_init_learning_data);
    tvProfileNicknameValue = view.findViewById(R.id.tv_profile_nickname_value);
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

    if (tvLogout != null) {
      tvLogout.setOnClickListener(v -> performLogout());
    }
  }

  private void performLogout() {
    FirebaseAuth.getInstance().signOut();
    if (appSettingsStore != null) {
      appSettingsStore.setUserNickname("");
    }
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
