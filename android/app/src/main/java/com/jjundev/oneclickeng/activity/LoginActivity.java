package com.jjundev.oneclickeng.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.jjundev.oneclickeng.R;

public class LoginActivity extends AppCompatActivity {

    private VideoView videoViewBackground;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;

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

        bottomSheet.post(() -> {
            int sheetHeight = bottomSheet.getHeight();
            // Start it pushed down by its height
            bottomSheet.setTranslationY(sheetHeight);

            // Show it after 200ms delay with a powerful spring-like/decelerate animation
            bottomSheet.postDelayed(() -> {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

                bottomSheet.animate()
                        .translationY(0f)
                        .setDuration(600) // 600ms for a long smooth feel
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(2.0f)) // Strong deceleration
                                                                                                  // (starts fast/"힘있게",
                                                                                                  // ends soft/"부드럽게")
                        .start();
            }, 200);
        });

        // Add callback to keep bottom sheet expanded
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@androidx.annotation.NonNull View bottomSheetView, int newState) {
                // If the user drags and it tries to collapse, force it back to expanded
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            }

            @Override
            public void onSlide(@androidx.annotation.NonNull View bottomSheetView, float slideOffset) {
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

        findViewById(R.id.btnEmailLogin).setOnClickListener(v -> {
            Toast.makeText(this, "이메일 로그인 클릭", Toast.LENGTH_SHORT).show();
            navigateToMain();
        });

        findViewById(R.id.btnGoogleLogin).setOnClickListener(v -> {
            Toast.makeText(this, "구글 로그인 클릭", Toast.LENGTH_SHORT).show();
            navigateToMain();
        });
    }

    // Login mockup - goes to MainActivity
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
