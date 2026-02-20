package com.example.test.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.OvershootInterpolator;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * 슬롯머신 스타일의 숫자 카운팅 애니메이션을 제공하는 커스텀 TextView. 0에서 목표값까지 숫자가 빠르게 롤링되며, OvershootInterpolator로 자연스러운
 * 감속 효과를 줍니다.
 */
public class SlotMachineTextView extends AppCompatTextView {

  private ValueAnimator animator;

  public SlotMachineTextView(Context context) {
    super(context);
  }

  public SlotMachineTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SlotMachineTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  /**
   * 0에서 targetValue까지 슬롯머신 스타일로 숫자를 애니메이션합니다.
   *
   * @param targetValue 최종 표시할 숫자
   * @param suffix 숫자 뒤에 붙는 접미사 (예: "분", "XP", "일째 열공 중 🔥")
   * @param duration 애니메이션 지속 시간 (ms)
   * @param startDelay 애니메이션 시작 딜레이 (ms)
   */
  public void animateValue(int targetValue, String suffix, long duration, long startDelay) {
    cancelAnimation();

    // 초기 상태: 0 + 접미사
    setText("0" + suffix);

    animator = ValueAnimator.ofInt(0, targetValue);
    animator.setDuration(duration);
    animator.setStartDelay(startDelay);
    animator.setInterpolator(new OvershootInterpolator(0.6f));

    animator.addUpdateListener(
        animation -> {
          int value = (int) animation.getAnimatedValue();
          // overshoot으로 인해 targetValue를 초과할 수 있으므로 클램핑
          if (value > targetValue) {
            value = targetValue;
          } else if (value < 0) {
            value = 0;
          }
          setText(value + suffix);
        });

    animator.start();
  }

  /** 진행 중인 애니메이션을 취소합니다. */
  public void cancelAnimation() {
    if (animator != null && animator.isRunning()) {
      animator.cancel();
      animator = null;
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    cancelAnimation();
  }
}
