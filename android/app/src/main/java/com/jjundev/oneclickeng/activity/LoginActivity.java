package com.jjundev.oneclickeng.activity;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.jjundev.oneclickeng.R;

public class LoginActivity extends AppCompatActivity {

  private VideoView videoViewBackground;
  private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
  private static final String TAG = "LoginActivity";

  private View layoutLoginContent;
  private View layoutEmailStep;
  private View layoutPasswordStep;
  private View layoutSignupStep;
  private View layoutLoginLoading;
  private View currentStepLayout;

  @Override
  protected void onStart() {
    super.onStart();
    // Check if user is already signed in
    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
      navigateToMain();
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_login);

    videoViewBackground = findViewById(R.id.videoViewBackground);
    videoViewBackground.setZOrderOnTop(false);
    setupBackgroundVideo();

    // Setup bottom sheet
    LinearLayout bottomSheet = findViewById(R.id.bottomSheetLogin);
    bottomSheet.bringToFront();
    bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

    // Prevent hiding bottom sheet completely by user drag
    bottomSheetBehavior.setHideable(false);

    bottomSheet.post(
        () -> {
          int sheetHeight = bottomSheet.getHeight();
          // Start it pushed down by its height
          bottomSheet.setTranslationY(sheetHeight);

          // Show it after 200ms delay with a powerful spring-like/decelerate animation
          bottomSheet.postDelayed(
              () -> {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

                bottomSheet
                    .animate()
                    .translationY(0f)
                    .setDuration(600) // 600ms for a long smooth feel
                    .setInterpolator(
                        new android.view.animation.DecelerateInterpolator(
                            2.0f)) // Strong deceleration
                    // (starts fast/"힘있게",
                    // ends soft/"부드럽게")
                    .start();
              },
              200);
        });

    // Add callback to keep bottom sheet expanded
    bottomSheetBehavior.addBottomSheetCallback(
        new BottomSheetBehavior.BottomSheetCallback() {
          @Override
          public void onStateChanged(
              @androidx.annotation.NonNull View bottomSheetView, int newState) {
            // If the user drags and it tries to collapse, force it back to expanded
            if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
              bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
          }

          @Override
          public void onSlide(
              @androidx.annotation.NonNull View bottomSheetView, float slideOffset) {
            // We can add a slight overshoot/spring effect by scaling the view slightly when
            // dragged down
            // If slideOffset < 1.0 (meaning it's being dragged down)
            if (slideOffset >= 0f && slideOffset < 1f) {
              float scale = 1f + (1f - slideOffset) * 0.05f; // Slightly stretch it up to 5% larger
              bottomSheetView.setScaleY(scale);
            } else {
              bottomSheetView.setScaleY(1f);
            }
          }
        });

    layoutLoginContent = findViewById(R.id.layoutLoginContent);
    layoutEmailStep = findViewById(R.id.layoutEmailStep);
    layoutPasswordStep = findViewById(R.id.layoutPasswordStep);
    layoutSignupStep = findViewById(R.id.layoutSignupStep);
    layoutLoginLoading = findViewById(R.id.layoutLoginLoading);

    currentStepLayout = layoutLoginContent;

    findViewById(R.id.btnEmailLogin)
        .setOnClickListener(
            v -> {
              showStep(layoutEmailStep, layoutLoginContent, false);
            });

    setupEmailStep();
    setupPasswordStep();
    setupSignupStep();

    View.OnClickListener googleLoginListener = v -> {
      setLoadingState(true);
      signInWithGoogle();
    };

    findViewById(R.id.btnGoogleLogin).setOnClickListener(googleLoginListener);
    findViewById(R.id.btnGoogleLoginFromEmail).setOnClickListener(googleLoginListener);
    findViewById(R.id.btnGoogleLoginFromPassword).setOnClickListener(googleLoginListener);
    findViewById(R.id.btnGoogleLoginFromSignup).setOnClickListener(googleLoginListener);
  }

  // Login mockup - goes to MainActivity
  private void navigateToMain() {
    Intent intent = new Intent(this, MainActivity.class);
    startActivity(intent);
    finish();
  }

  private void setLoadingState(boolean isLoading) {
    if (isLoading) {
      if (currentStepLayout != null)
        currentStepLayout.setVisibility(View.GONE);
      if (layoutLoginLoading != null)
        layoutLoginLoading.setVisibility(View.VISIBLE);
    } else {
      if (currentStepLayout != null)
        currentStepLayout.setVisibility(View.VISIBLE);
      if (layoutLoginLoading != null)
        layoutLoginLoading.setVisibility(View.GONE);
    }
  }

  private void showStep(View nextView, View currentView, boolean isBack) {
    if (currentView != null) {
      currentView.setVisibility(View.GONE);
      if (isBack) {
        currentView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_right));
      } else {
        currentView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left));
      }
    }
    if (nextView != null) {
      nextView.setVisibility(View.VISIBLE);
      currentStepLayout = nextView;
      if (isBack) {
        nextView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_left));
      } else {
        nextView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
      }
    }
  }

  private void setupEmailStep() {
    findViewById(R.id.btnBackFromEmail).setOnClickListener(v -> showStep(layoutLoginContent, layoutEmailStep, true));
    TextInputEditText etEmail = findViewById(R.id.etEmail);
    MaterialButton btnEmailContinue = findViewById(R.id.btnEmailContinue);

    etEmail.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        String email = s.toString().trim();
        btnEmailContinue.setEnabled(Patterns.EMAIL_ADDRESS.matcher(email).matches());
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });

    btnEmailContinue.setOnClickListener(v -> {
      String email = etEmail.getText().toString().trim();
      checkEmailExists(email);
    });
  }

  private void checkEmailExists(String email) {
    setLoadingState(true);
    FirebaseAuth.getInstance()
        .fetchSignInMethodsForEmail(email)
        .addOnCompleteListener(
            task -> {
              setLoadingState(false);
              if (task.isSuccessful()) {
                boolean isNewUser = task.getResult().getSignInMethods().isEmpty();
                TextView tvDisplayEmail = findViewById(R.id.tvDisplayEmail);
                if (tvDisplayEmail != null)
                  tvDisplayEmail.setText(email);

                TextView tvDisplaySignupEmail = findViewById(R.id.tvDisplaySignupEmail);
                if (tvDisplaySignupEmail != null)
                  tvDisplaySignupEmail.setText(email);

                if (isNewUser) {
                  showStep(layoutSignupStep, layoutEmailStep, false);
                } else {
                  showStep(layoutPasswordStep, layoutEmailStep, false);
                }
              } else {
                Toast.makeText(this, "이메일 확인 중 오류가 발생했어요", Toast.LENGTH_SHORT).show();
              }
            });
  }

  private void setupPasswordStep() {
    findViewById(R.id.btnBackFromPassword).setOnClickListener(v -> showStep(layoutEmailStep, layoutPasswordStep, true));
    TextInputEditText etPassword = findViewById(R.id.etPassword);

    findViewById(R.id.btnLogin).setOnClickListener(v -> {
      String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
      if (password.isEmpty()) {
        Toast.makeText(this, "비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
        return;
      }

      TextView tvDisplayEmail = findViewById(R.id.tvDisplayEmail);
      String email = tvDisplayEmail.getText().toString();

      setLoadingState(true);
      FirebaseAuth.getInstance()
          .signInWithEmailAndPassword(email, password)
          .addOnCompleteListener(
              this,
              task -> {
                setLoadingState(false);
                if (task.isSuccessful()) {
                  Log.d(TAG, "signInWithEmail:success");
                  Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show();
                  navigateToMain();
                } else {
                  Log.w(TAG, "signInWithEmail:failure", task.getException());
                  Toast.makeText(this, "로그인 실패: 비밀번호를 확인해주세요.", Toast.LENGTH_SHORT).show();
                }
              });
    });
  }

  private void setupSignupStep() {
    findViewById(R.id.btnBackFromSignup).setOnClickListener(v -> showStep(layoutEmailStep, layoutSignupStep, true));
    TextInputEditText etSignupPassword = findViewById(R.id.etSignupPassword);
    TextInputEditText etSignupPasswordConfirm = findViewById(R.id.etSignupPasswordConfirm);
    MaterialButton btnSignup = findViewById(R.id.btnSignup);
    TextView tvPasswordStrength = findViewById(R.id.tvPasswordStrength);

    TextWatcher signupWatcher = new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        String p1 = etSignupPassword.getText() != null ? etSignupPassword.getText().toString() : "";
        String p2 = etSignupPasswordConfirm.getText() != null ? etSignupPasswordConfirm.getText().toString() : "";

        if (p1.length() == 0) {
          tvPasswordStrength.setText("비밀번호 강도: -");
          btnSignup.setEnabled(false);
          return;
        }

        if (p1.length() >= 8 && p1.matches(".*[a-zA-Z]+.*") && p1.matches(".*[0-9]+.*")) {
          tvPasswordStrength.setText("비밀번호 강도: 강함");
          tvPasswordStrength.setTextColor(Color.parseColor("#4DD9AC"));
        } else if (p1.length() >= 6) {
          tvPasswordStrength.setText("비밀번호 강도: 보통");
          tvPasswordStrength.setTextColor(ContextCompat.getColor(LoginActivity.this, R.color.color_sub_text));
        } else {
          tvPasswordStrength.setText("비밀번호 강도: 약함 (6자 이상)");
          tvPasswordStrength.setTextColor(Color.parseColor("#E53935"));
        }

        btnSignup.setEnabled(p1.length() >= 6 && p1.equals(p2));
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    };

    etSignupPassword.addTextChangedListener(signupWatcher);
    etSignupPasswordConfirm.addTextChangedListener(signupWatcher);

    btnSignup.setOnClickListener(v -> {
      String password = etSignupPassword.getText() != null ? etSignupPassword.getText().toString() : "";
      TextView tvDisplaySignupEmail = findViewById(R.id.tvDisplaySignupEmail);
      String email = tvDisplaySignupEmail != null ? tvDisplaySignupEmail.getText().toString() : "";

      if (email.isEmpty() || password.isEmpty()) {
        Toast.makeText(this, "이메일 또는 비밀번호가 유효하지 않아요", Toast.LENGTH_SHORT).show();
        return;
      }

      setLoadingState(true);
      FirebaseAuth.getInstance()
          .createUserWithEmailAndPassword(email, password)
          .addOnCompleteListener(
              this,
              task -> {
                if (task.isSuccessful()) {
                  Log.d(TAG, "createUserWithEmail:success");
                  if (task.getResult().getUser() != null) {
                    initializeUserCredit(task.getResult().getUser().getUid(), "회원가입 및 로그인 완료! (10 크레딧 지급)");
                  } else {
                    setLoadingState(false);
                    Toast.makeText(LoginActivity.this, "회원가입 및 로그인 완료!", Toast.LENGTH_SHORT).show();
                    navigateToMain();
                  }
                } else {
                  setLoadingState(false);
                  Log.w(TAG, "createUserWithEmail:failure", task.getException());
                  Toast.makeText(LoginActivity.this, "회원가입 실패: " + task.getException().getMessage(), Toast.LENGTH_SHORT)
                      .show();
                }
              });
    });
  }

  private void signInWithGoogle() {
    GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(getString(R.string.default_web_client_id))
        .setAutoSelectEnabled(true)
        .build();

    GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build();

    CredentialManager credentialManager = CredentialManager.create(this);
    credentialManager.getCredentialAsync(
        this,
        request,
        new CancellationSignal(),
        ContextCompat.getMainExecutor(this),
        new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
          @Override
          public void onResult(GetCredentialResponse result) {
            try {
              if (result.getCredential() instanceof CustomCredential) {
                CustomCredential credential = (CustomCredential) result.getCredential();
                if (credential
                    .getType()
                    .equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
                  GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential
                      .createFrom(credential.getData());
                  String idToken = googleIdTokenCredential.getIdToken();
                  firebaseAuthWithGoogle(idToken);
                }
              }
            } catch (Exception e) {
              Log.e(TAG, "Parsing credential failed", e);
              Toast.makeText(LoginActivity.this, "구글 로그인 중 오류 발생", Toast.LENGTH_SHORT).show();
              setLoadingState(false);
            }
          }

          @Override
          public void onError(GetCredentialException e) {
            Log.e(TAG, "Google Sign-In failed", e);
            setLoadingState(false);
            // Toast.makeText(LoginActivity.this, "구글 로그인 실패",
            // Toast.LENGTH_SHORT).show();
          }
        });
  }

  private void firebaseAuthWithGoogle(String idToken) {
    AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
    FirebaseAuth.getInstance()
        .signInWithCredential(credential)
        .addOnCompleteListener(
            this,
            task -> {
              if (task.isSuccessful()) {
                Log.d(TAG, "signInWithCredential:success");
                boolean isNewUser = false;
                if (task.getResult().getAdditionalUserInfo() != null) {
                  isNewUser = task.getResult().getAdditionalUserInfo().isNewUser();
                }

                if (isNewUser && task.getResult().getUser() != null) {
                  initializeUserCredit(task.getResult().getUser().getUid(), "로그인 성공! (10 크레딧 지급)");
                } else {
                  setLoadingState(false);
                  Toast.makeText(LoginActivity.this, "로그인 성공!", Toast.LENGTH_SHORT).show();
                  navigateToMain();
                }
              } else {
                setLoadingState(false);
                Log.w(TAG, "signInWithCredential:failure", task.getException());
                Toast.makeText(
                    LoginActivity.this,
                    "Firebase 인증 실패: " + task.getException().getMessage(),
                    Toast.LENGTH_SHORT)
                    .show();
              }
            });
  }

  private void setupBackgroundVideo() {
    Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.video_login_activity);
    videoViewBackground.setVideoURI(videoUri);

    videoViewBackground.setOnPreparedListener(
        mp -> {
          mp.setLooping(true);
          mp.setVolume(0f, 0f);
          mp.start();
        });
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (videoViewBackground != null && !videoViewBackground.isPlaying()) {
      videoViewBackground.start();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (videoViewBackground != null && videoViewBackground.isPlaying()) {
      videoViewBackground.pause();
    }
  }

  private void initializeUserCredit(String uid, String successMessage) {
    java.util.Map<String, Object> data = new java.util.HashMap<>();
    data.put("credit", 10L);
    FirebaseFirestore.getInstance()
        .collection("users")
        .document(uid)
        .set(data, com.google.firebase.firestore.SetOptions.merge())
        .addOnCompleteListener(
            task -> {
              setLoadingState(false);
              Toast.makeText(LoginActivity.this, successMessage, Toast.LENGTH_SHORT).show();
              navigateToMain();
            });
  }
}
