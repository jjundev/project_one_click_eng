package com.jjundev.oneclickeng.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
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
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.jjundev.oneclickeng.R;

public class LoginActivity extends AppCompatActivity {

  private VideoView videoViewBackground;
  private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
  private static final String TAG = "LoginActivity";

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

    // Setup bottom sheet
    LinearLayout bottomSheet = findViewById(R.id.bottomSheetLogin);
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

    findViewById(R.id.btnEmailLogin)
        .setOnClickListener(
            v -> {
              Toast.makeText(this, "이메일 로그인 클릭", Toast.LENGTH_SHORT).show();
              navigateToMain();
            });

    findViewById(R.id.btnGoogleLogin)
        .setOnClickListener(
            v -> {
              setLoadingState(true);
              signInWithGoogle();
            });
  }

  // Login mockup - goes to MainActivity
  private void navigateToMain() {
    Intent intent = new Intent(this, MainActivity.class);
    startActivity(intent);
    finish();
  }

  private void setLoadingState(boolean isLoading) {
    View layoutLoginContent = findViewById(R.id.layoutLoginContent);
    View layoutLoginLoading = findViewById(R.id.layoutLoginLoading);

    if (isLoading) {
      if (layoutLoginContent != null) layoutLoginContent.setVisibility(View.GONE);
      if (layoutLoginLoading != null) layoutLoginLoading.setVisibility(View.VISIBLE);
    } else {
      if (layoutLoginContent != null) layoutLoginContent.setVisibility(View.VISIBLE);
      if (layoutLoginLoading != null) layoutLoginLoading.setVisibility(View.GONE);
    }
  }

  private void signInWithGoogle() {
    GetGoogleIdOption googleIdOption =
        new GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(true)
            .build();

    GetCredentialRequest request =
        new GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build();

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
                  GoogleIdTokenCredential googleIdTokenCredential =
                      GoogleIdTokenCredential.createFrom(credential.getData());
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
                Toast.makeText(LoginActivity.this, "로그인 성공!", Toast.LENGTH_SHORT).show();
                navigateToMain();
              } else {
                Log.w(TAG, "signInWithCredential:failure", task.getException());
                Toast.makeText(
                        LoginActivity.this,
                        "Firebase 인증 실패: " + task.getException().getMessage(),
                        Toast.LENGTH_SHORT)
                    .show();
                setLoadingState(false);
              }
            });
  }
}
