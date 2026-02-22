package com.jjundev.oneclickeng.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.jjundev.oneclickeng.R;

public final class RefinerInputV1Activity extends AppCompatActivity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.fragment_refiner_input_v1);

    View backView = findViewById(R.id.btn_back);
    if (backView != null) {
      backView.setOnClickListener(v -> finish());
    }

    View cancelView = findViewById(R.id.btn_cancel);
    if (cancelView != null) {
      cancelView.setOnClickListener(v -> finish());
    }

    EditText refinementInput = findViewById(R.id.input_refinement);
    ImageButton clearButton = findViewById(R.id.btn_clear_refinement);
    if (clearButton != null && refinementInput != null) {
      clearButton.setOnClickListener(v -> refinementInput.setText(""));
    }
  }
}
