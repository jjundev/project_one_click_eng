package com.jjundev.oneclickeng.activity;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.fragment.RefinerInputV1Fragment;

public final class RefinerGameActivity extends AppCompatActivity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_refiner_game);

    View backButton = findViewById(R.id.btn_back);
    if (backButton != null) {
      backButton.setOnClickListener(v -> finish());
    }

    if (savedInstanceState == null) {
      getSupportFragmentManager()
          .beginTransaction()
          .replace(R.id.fragment_container_refiner_input, new RefinerInputV1Fragment())
          .commit();
    }
  }
}
