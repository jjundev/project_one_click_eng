package com.example.test.activity;

import android.os.Bundle;
import android.util.Log;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.example.test.BuildConfig;
import com.example.test.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = "JOB_J-20260216-003";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    EdgeToEdge.enable(this);
    setContentView(R.layout.activity_main);

    BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
    NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
        .findFragmentById(R.id.fragment_container);

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
          int currentDestinationId = navController.getCurrentDestination() != null
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
          if (destinationId == R.id.scriptSelectFragment || destinationId == R.id.dialogueSummaryFragment) {
            menuId = R.id.studyModeSelectFragment;
          } else if (destinationId == R.id.dialogueQuizFragment) {
            menuId = R.id.learningHistoryFragment;
          }

          if (bottomNavigation.getMenu().findItem(menuId) != null) {
            bottomNavigation.getMenu().findItem(menuId).setChecked(true);
          }

          String destinationName = getResources().getResourceEntryName(destinationId);
          logDebug("Fragment switched: " + destinationName + " (" + destinationId + ")");
        });
  }

  private void logDebug(String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }
}
