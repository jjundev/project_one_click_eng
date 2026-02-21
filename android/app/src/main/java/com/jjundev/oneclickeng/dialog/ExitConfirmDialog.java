package com.jjundev.oneclickeng.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.DialogFragment;
import com.jjundev.oneclickeng.R;

public class ExitConfirmDialog extends DialogFragment {

  public interface OnExitConfirmListener {
    void onConfirmExit();
  }

  private OnExitConfirmListener listener;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (context instanceof OnExitConfirmListener) {
      listener = (OnExitConfirmListener) context;
    } else {
      //             Activity가 리스너를 직접 구현하지 않은 경우를 위해 예외 방지 (강제성은 생략 가능하나 권장됨)
      throw new RuntimeException(context.toString() + " must implement OnExitConfirmListener");
    }
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.dialog_exit_confirm, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    AppCompatButton btnCancel = view.findViewById(R.id.btn_cancel);
    AppCompatButton btnConfirm = view.findViewById(R.id.btn_confirm);

    btnCancel.setOnClickListener(v -> dismiss());

    btnConfirm.setOnClickListener(
        v -> {
          if (listener != null) {
            listener.onConfirmExit();
          }
          dismiss();
        });
  }

  @Override
  public void onStart() {
    super.onStart();
    if (getDialog() != null && getDialog().getWindow() != null) {
      // 투명 배경 설정 (둥근 모서리 적용을 위해 필수)
      getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);

      // 가로 너비를 화면의 90%로 설정
      int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
      getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
  }
}
