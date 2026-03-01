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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.jjundev.oneclickeng.R;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = "JOB_J-20260216-003";
  private static final String PREF_NOTIFICATION_PERMISSION = "notification_permission_prefs";
  private static final String KEY_PERMISSION_REQUESTED = "post_notifications_requested";

  private long backPressedTime = 0;
  @Nullable private BottomNavigationView bottomNavigation;
  @Nullable private NavController navController;

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

    bottomNavigation = findViewById(R.id.bottom_navigation);
    NavHostFragment navHostFragment =
        (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);

    if (navHostFragment == null) {
      logDebug("NavHostFragment is null. Bottom navigation setup skipped.");
      return;
    }

    navController = navHostFragment.getNavController();
    NavigationUI.setupWithNavController(bottomNavigation, navController);

    bottomNavigation.setOnItemSelectedListener(
        item -> {
          int itemId = item.getItemId();
          String selectedName = getResources().getResourceEntryName(itemId);
          logDebug("Tab selected: " + selectedName + " (" + itemId + ")");

          NavController controller = navController;
          if (controller == null) {
            return false;
          }

          int currentDestinationId =
              controller.getCurrentDestination() != null
                  ? controller.getCurrentDestination().getId()
                  : -1;
          if (currentDestinationId == R.id.creditStoreFragment) {
            if (itemId == R.id.settingFragment) {
              return navigateToSettingFromCreditStore(controller);
            }
            return navigateFromCreditStoreToBottomTab(itemId, controller);
          }

          return NavigationUI.onNavDestinationSelected(item, controller);
        });

    bottomNavigation.setOnItemReselectedListener(
        item -> {
          int itemId = item.getItemId();
          String selectedName = getResources().getResourceEntryName(itemId);
          logDebug("Tab reselected: " + selectedName + " (" + itemId + ")");
          NavController controller = navController;
          if (controller == null) {
            return;
          }

          int currentDestinationId =
              controller.getCurrentDestination() != null
                  ? controller.getCurrentDestination().getId()
                  : -1;
          if (currentDestinationId == R.id.creditStoreFragment) {
            if (itemId == R.id.settingFragment) {
              navigateToSettingFromCreditStore(controller);
              return;
            }
            navigateFromCreditStoreToBottomTab(itemId, controller);
          }
        });

    navController.addOnDestinationChangedListener(
        (controller, destination, arguments) -> {
          int destinationId = destination.getId();

          int menuId = destinationId;
          if (destinationId == R.id.scriptSelectFragment
              || destinationId == R.id.dialogueSummaryFragment) {
            menuId = R.id.studyModeSelectFragment;
          } else if (destinationId == R.id.creditStoreFragment) {
            menuId = R.id.settingFragment;
          }

          BottomNavigationView navigationView = bottomNavigation;
          if (navigationView != null && navigationView.getMenu().findItem(menuId) != null) {
            navigationView.getMenu().findItem(menuId).setChecked(true);
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
                NavController controller = navController;
                if (controller == null) {
                  finish();
                  return;
                }

                int currentDestId =
                    controller.getCurrentDestination() != null
                        ? controller.getCurrentDestination().getId()
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
                  if (!controller.popBackStack()) {
                    finish();
                  }
                }
              }
            });
  }

  private boolean navigateToSettingFromCreditStore(@NonNull NavController controller) {
    logDebug("Navigating from CreditStore to Setting via bottom tab.");
    if (controller.popBackStack(R.id.settingFragment, false)) {
      return true;
    }

    int startDestinationId = controller.getGraph().getStartDestinationId();
    NavOptions navOptions =
        new NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(false)
            .setPopUpTo(startDestinationId, false, false)
            .build();
    try {
      controller.navigate(R.id.settingFragment, null, navOptions);
      return true;
    } catch (IllegalArgumentException | IllegalStateException exception) {
      logDebug(
          "Failed to navigate from CreditStore to Setting via bottom navigation."
              + " destinationId="
              + R.id.settingFragment
              + ", message="
              + exception.getMessage());
      return recoverToStartDestination(controller, "setting-navigation-fallback");
    }
  }

  private boolean navigateFromCreditStoreToBottomTab(int menuId, @NonNull NavController controller) {
    String destinationName = getResources().getResourceEntryName(menuId);
    logDebug("Navigating from CreditStore to tab: " + destinationName + " (" + menuId + ")");
    controller.popBackStack(R.id.creditStoreFragment, true);

    int startDestinationId = controller.getGraph().getStartDestinationId();
    NavOptions navOptions =
        new NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(false)
            .setPopUpTo(startDestinationId, false, false)
            .build();
    try {
      controller.navigate(menuId, null, navOptions);
      return true;
    } catch (IllegalArgumentException | IllegalStateException exception) {
      logDebug(
          "Failed to navigate from CreditStore via bottom navigation."
              + " destinationId="
              + menuId
              + ", message="
              + exception.getMessage());
      return recoverToStartDestination(controller, "credit-store-tab-fallback");
    }
  }

  private boolean recoverToStartDestination(
      @NonNull NavController controller, @NonNull String recoveryReason) {
    int startDestinationId = controller.getGraph().getStartDestinationId();
    try {
      controller.navigate(startDestinationId);
      logDebug(
          "Recovered navigation by moving to start destination. reason="
              + recoveryReason
              + ", startDestination="
              + startDestinationId);
      return true;
    } catch (IllegalArgumentException | IllegalStateException recoveryException) {
      logDebug(
          "Failed to recover to start destination. reason="
              + recoveryReason
              + ", startDestination="
              + startDestinationId
              + ", message="
              + recoveryException.getMessage());
      return false;
    }
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
    Log.d(TAG, message);
  }
}
