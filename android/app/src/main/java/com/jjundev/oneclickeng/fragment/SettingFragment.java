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
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.activity.LoginActivity;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;

public class SettingFragment extends Fragment {
  private static final String TAG = "SettingFragment";

  @Nullable
  private AppSettingsStore appSettingsStore;

  private boolean bindingState;

  private LinearLayout layoutProfileNickname;
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

    if (tvLogout != null) {
      tvLogout.setOnClickListener(v -> performLogout());
    }
  }

  private void performLogout() {
    FirebaseAuth.getInstance().signOut();
    if (appSettingsStore != null) {
      appSettingsStore.setUserNickname("");
    }
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
    AppSettings settings = store.getSettings();
    bindingState = true;

    if (tvProfileNicknameValue != null) {
      tvProfileNicknameValue.setText(getEffectiveNickname());
    }

    bindingState = false;
  }

  private void logDebug(@NonNull String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }
}
