package com.jjundev.oneclickeng.activity;

import android.content.Context;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
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
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.jjundev.oneclickeng.R;

public class LoginActivity extends AppCompatActivity {

  private static final long IME_HIDE_TIMEOUT_MS = 300L;
  private static final long IME_POLL_INTERVAL_MS = 16L;
  private VideoView videoViewBackground;
  private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
  private static final String TAG = "LoginActivity";
  private final Handler imeHandler = new Handler(Looper.getMainLooper());

  private View layoutLoginContent;
  private View layoutEmailStep;
  private View layoutPasswordStep;
  private View layoutSignupStep;
  private View layoutLoginLoading;
  private View currentStepLayout;
  private Runnable imeHidePollRunnable;
  private boolean isBackTransitionPending;

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
    TextInputLayout tilEmail = findViewById(R.id.tilEmail);
    TextInputEditText etEmail = findViewById(R.id.etEmail);
    MaterialButton btnEmailContinue = findViewById(R.id.btnEmailContinue);
    findViewById(R.id.btnBackFromEmail).setOnClickListener(v -> {
      if (isBackTransitionPending) {
        return;
      }
      isBackTransitionPending = true;
      etEmail.clearFocus();
      hideKeyboardThenRun(
          v,
          () -> {
            isBackTransitionPending = false;
            if (!isFinishing() && !isDestroyed()) {
              showStep(layoutLoginContent, layoutEmailStep, true);
            }
          });
    });

    etEmail.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        String email = s.toString().trim();
        if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
          tilEmail.setErrorEnabled(false);
          tilEmail.setError(null);
        }
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });

    btnEmailContinue.setOnClickListener(v -> {
      String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
      if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        tilEmail.setError("올바른 이메일 주소를 입력해주세요.");
        return;
      }
      checkEmailExists(email);
    });
  }

  private void hideKeyboardThenRun(View tokenView, Runnable afterHidden) {
    if (afterHidden == null) {
      return;
    }

    View rootView = findViewById(android.R.id.content);
    if (rootView == null) {
      afterHidden.run();
      return;
    }

    if (imeHidePollRunnable != null) {
      imeHandler.removeCallbacks(imeHidePollRunnable);
      imeHidePollRunnable = null;
    }

    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null && tokenView != null) {
      imm.hideSoftInputFromWindow(tokenView.getWindowToken(), 0);
    }

    WindowInsetsControllerCompat insetsController = ViewCompat.getWindowInsetsController(rootView);
    if (insetsController != null) {
      insetsController.hide(WindowInsetsCompat.Type.ime());
    }

    if (!isImeVisible(rootView)) {
      afterHidden.run();
      return;
    }

    final long startedAtMs = System.currentTimeMillis();
    imeHidePollRunnable = new Runnable() {
      @Override
      public void run() {
        long elapsedMs = System.currentTimeMillis() - startedAtMs;
        if (!isImeVisible(rootView) || elapsedMs >= IME_HIDE_TIMEOUT_MS) {
          imeHidePollRunnable = null;
          afterHidden.run();
          return;
        }
        imeHandler.postDelayed(this, IME_POLL_INTERVAL_MS);
      }
    };
    imeHandler.post(imeHidePollRunnable);
  }

  private boolean isImeVisible(View rootView) {
    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(rootView);
    return insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
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
    TextInputEditText etPassword = findViewById(R.id.etPassword);
    findViewById(R.id.btnBackFromPassword).setOnClickListener(v -> {
      if (isBackTransitionPending) {
        return;
      }
      isBackTransitionPending = true;
      etPassword.clearFocus();
      hideKeyboardThenRun(
          v,
          () -> {
            isBackTransitionPending = false;
            if (!isFinishing() && !isDestroyed()) {
              showStep(layoutEmailStep, layoutPasswordStep, true);
            }
          });
    });

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
    TextInputEditText etSignupPassword = findViewById(R.id.etSignupPassword);
    TextInputEditText etSignupPasswordConfirm = findViewById(R.id.etSignupPasswordConfirm);
    TextInputLayout tilSignupPassword = findViewById(R.id.tilSignupPassword);
    TextInputLayout tilSignupPasswordConfirm = findViewById(R.id.tilSignupPasswordConfirm);
    MaterialButton btnSignup = findViewById(R.id.btnSignup);
    findViewById(R.id.btnBackFromSignup).setOnClickListener(v -> {
      if (isBackTransitionPending) {
        return;
      }
      isBackTransitionPending = true;
      etSignupPassword.clearFocus();
      etSignupPasswordConfirm.clearFocus();
      hideKeyboardThenRun(
          v,
          () -> {
            isBackTransitionPending = false;
            if (!isFinishing() && !isDestroyed()) {
              showStep(layoutEmailStep, layoutSignupStep, true);
            }
          });
    });

    TextWatcher signupWatcher = new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        String p1 = etSignupPassword.getText() != null ? etSignupPassword.getText().toString() : "";
        String p2 = etSignupPasswordConfirm.getText() != null ? etSignupPasswordConfirm.getText().toString() : "";

        if (p1.length() >= 6) {
          tilSignupPassword.setErrorEnabled(false);
          tilSignupPassword.setError(null);
        } else if (tilSignupPassword.getError() != null) {
          // If the error was already shown by the button, keep updating the user if it's still invalid
          tilSignupPassword.setError("비밀번호는 6자리 이상이어야 합니다.");
        }
        
        if (p1.equals(p2)) {
          tilSignupPasswordConfirm.setErrorEnabled(false);
          tilSignupPasswordConfirm.setError(null);
        } else if (tilSignupPasswordConfirm.getError() != null || p2.length() > 0) {
           // Show error if they don't match, but only if they've started typing in the confirm box 
           // or if the button was already clicked (which sets the error initially)
           tilSignupPasswordConfirm.setError("비밀번호가 일치하지 않습니다.");
        }
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    };

    etSignupPassword.addTextChangedListener(signupWatcher);
    etSignupPasswordConfirm.addTextChangedListener(signupWatcher);

    btnSignup.setOnClickListener(v -> {
      String password = etSignupPassword.getText() != null ? etSignupPassword.getText().toString() : "";
      String passwordConfirm = etSignupPasswordConfirm.getText() != null ? etSignupPasswordConfirm.getText().toString()
          : "";
      TextView tvDisplaySignupEmail = findViewById(R.id.tvDisplaySignupEmail);
      String email = tvDisplaySignupEmail != null ? tvDisplaySignupEmail.getText().toString() : "";

      if (password.length() < 6) {
        tilSignupPassword.setError("비밀번호는 6자리 이상이어야 합니다.");
        return;
      }

      if (!password.equals(passwordConfirm)) {
        tilSignupPasswordConfirm.setError("비밀번호가 일치하지 않습니다.");
        return;
      }

      if (email.isEmpty()) {
        Toast.makeText(this, "이메일 정보가 유효하지 않아요", Toast.LENGTH_SHORT).show();
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
