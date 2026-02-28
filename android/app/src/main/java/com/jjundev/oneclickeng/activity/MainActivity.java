package com.jjundev.oneclickeng.activity;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = "JOB_J-20260216-003";
  private static final String PREF_NOTIFICATION_PERMISSION = "notification_permission_prefs";
  private static final String KEY_PERMISSION_REQUESTED = "post_notifications_requested";

  private long backPressedTime = 0;

  @NonNull
  private final ActivityResultLauncher<String> notificationPermissionLauncher =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          isGranted -> logDebug("POST_NOTIFICATIONS permission result: " + isGranted));

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    EdgeToEdge.enable(this);
    setContentView(R.layout.activity_main);
    requestNotificationPermissionIfNeeded();

    BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
    NavHostFragment navHostFragment =
        (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);

    if (navHostFragment == null) {
      logDebug("NavHostFragment is null. Bottom navigation setup skipped.");
      return;
    }

    NavController navController = navHostFragment.getNavController();
    NavigationUI.setupWithNavController(bottomNavigation, navController);

    bottomNavigation.setOnItemSelectedListener(
        item -> {
          String selectedName = getResources().getResourceEntryName(item.getItemId());
          logDebug("Tab selected: " + selectedName + " (" + item.getItemId() + ")");
          return NavigationUI.onNavDestinationSelected(item, navController);
        });

    bottomNavigation.setOnItemReselectedListener(
        item -> {
          int itemId = item.getItemId();
          int currentDestinationId =
              navController.getCurrentDestination() != null
                  ? navController.getCurrentDestination().getId()
                  : -1;

          if (currentDestinationId != itemId) {
            String selectedName = getResources().getResourceEntryName(itemId);
            logDebug("Tab reselected: Popping to " + selectedName);
            navController.popBackStack(itemId, false);
          }
        });

    navController.addOnDestinationChangedListener(
        (controller, destination, arguments) -> {
          int destinationId = destination.getId();

          int menuId = destinationId;
          if (destinationId == R.id.scriptSelectFragment
              || destinationId == R.id.dialogueSummaryFragment) {
            menuId = R.id.studyModeSelectFragment;
          }

          if (bottomNavigation.getMenu().findItem(menuId) != null) {
            bottomNavigation.getMenu().findItem(menuId).setChecked(true);
          }

          String destinationName = getResources().getResourceEntryName(destinationId);
          logDebug("Fragment switched: " + destinationName + " (" + destinationId + ")");
        });

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                int currentDestId =
                    navController.getCurrentDestination() != null
                        ? navController.getCurrentDestination().getId()
                        : -1;

                if (currentDestId == R.id.studyModeSelectFragment) {
                  // 홈 화면(learningModeSelect)에서만 두 번 눌러 종료
                  if (System.currentTimeMillis() - backPressedTime < 2000) {
                    finish();
                  } else {
                    Toast.makeText(MainActivity.this, "앱을 종료하려면 한번 더 눌러주세요", Toast.LENGTH_SHORT)
                        .show();
                    backPressedTime = System.currentTimeMillis();
                  }
                } else {
                  // 다른 Fragment에서는 이전 화면으로 돌아가기
                  if (!navController.popBackStack()) {
                    finish();
                  }
                }
              }
            });
  }

  private void requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      return;
    }
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED) {
      return;
    }

    SharedPreferences preferences =
        getSharedPreferences(PREF_NOTIFICATION_PERMISSION, MODE_PRIVATE);
    if (preferences.getBoolean(KEY_PERMISSION_REQUESTED, false)) {
      return;
    }

    preferences.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply();
    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
  }

  private void logDebug(String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }
}
