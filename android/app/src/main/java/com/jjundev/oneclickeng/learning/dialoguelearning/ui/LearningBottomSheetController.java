package com.jjundev.oneclickeng.learning.dialoguelearning.ui;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.BottomSheetMode;

public class LearningBottomSheetController {

  private static final float BOTTOM_SHEET_MAX_HEIGHT_RATIO = 0.8f;
  private static final float BOTTOM_SHEET_MIN_HEIGHT_RATIO = 0.2f;

  @NonNull private final LearningBottomSheetRenderer bottomSheetRenderer;
  @NonNull private final LearningChatRenderer chatRenderer;
  @Nullable private final LearningControlsRenderer controlsRenderer;

  @Nullable private BottomSheetBehavior<View> bottomSheetBehavior;
  @Nullable private Runnable onRequestChatScroll;
  @Nullable private Runnable onSlideCollapsedAction;
  @Nullable private View bottomSheet;

  private boolean isAutoScrollingToBottom;

  private final BottomSheetBehavior.BottomSheetCallback sheetCallback =
      new BottomSheetBehavior.BottomSheetCallback() {
        @Override
        public void onStateChanged(@NonNull View bottomSheet, int newState) {
          LearningBottomSheetController.this.onSlideStateChanged(newState);
        }

        @Override
        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
          int visibleHeight = calculateVisibleHeight(bottomSheet);
          LearningBottomSheetController.this.onBottomSheetSlide(
              bottomSheet, slideOffset, visibleHeight, isAutoScrollingToBottom);
        }
      };

  public LearningBottomSheetController(
      @NonNull LearningBottomSheetRenderer bottomSheetRenderer,
      @NonNull LearningChatRenderer chatRenderer,
      @Nullable LearningControlsRenderer controlsRenderer) {
    this.bottomSheetRenderer = bottomSheetRenderer;
    this.chatRenderer = chatRenderer;
    this.controlsRenderer = controlsRenderer;
  }

  public void setup(
      @NonNull View root,
      @NonNull View bottomSheet,
      @NonNull View contentContainer,
      @NonNull Runnable onRequestChatScroll) {
    this.bottomSheet = bottomSheet;
    this.onRequestChatScroll = onRequestChatScroll;
    bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

    bottomSheetBehavior.setFitToContents(true);
    bottomSheetBehavior.setHideable(false);

    root.getViewTreeObserver()
        .addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                root.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                int fragmentHeight = root.getHeight();
                int maxHeight = (int) (fragmentHeight * BOTTOM_SHEET_MAX_HEIGHT_RATIO);
                int minHeight = (int) (fragmentHeight * BOTTOM_SHEET_MIN_HEIGHT_RATIO);

                ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                if (bottomSheet.getHeight() > maxHeight) {
                  params.height = maxHeight;
                } else {
                  params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                bottomSheet.setLayoutParams(params);

                bottomSheetBehavior.setPeekHeight(minHeight);
                bottomSheetBehavior.setMaxHeight(maxHeight);
                chatRenderer.setFooterHeight(minHeight);
                chatRenderer.syncFooterWithBottomSheet(bottomSheet);
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

                setVisible(false);
              }
            });

    bottomSheetBehavior.addBottomSheetCallback(sheetCallback);
  }

  public void setVisible(boolean visible) {
    bottomSheetRenderer.showSheet(visible);
    syncChatFooterForCurrentSheetState();
  }

  public View replaceOrReuseContent(
      @LayoutRes int layoutResId,
      @NonNull BottomSheetSceneRenderer renderer,
      @NonNull Runnable renderBinder) {
    View content = bottomSheetRenderer.getCurrentContent();
    if (content == null
        || !(content.getTag() instanceof Integer)
        || ((Integer) content.getTag()) != layoutResId) {
      if (bottomSheet == null) {
        return null;
      }
      android.view.LayoutInflater inflater =
          android.view.LayoutInflater.from(bottomSheet.getContext());
      if (inflater != null) {
        content = bottomSheetRenderer.replaceContent(layoutResId, inflater);
      }
      if (content != null) {
        content.setTag(layoutResId);
      }
    }

    if (content == null) {
      return null;
    }

    if (bottomSheet != null) {
      chatRenderer.syncFooterWithBottomSheet(bottomSheet);
    }
    if (controlsRenderer != null) {
      controlsRenderer.syncBottomSheetContent(content, isAutoScrollingToBottom);
    }
    isAutoScrollingToBottom = false;

    renderBinder.run();
    return content;
  }

  public View getCurrentContent() {
    return bottomSheetRenderer.getCurrentContent();
  }

  public void clearContent() {
    bottomSheetRenderer.clearContent();
    syncChatFooterForCurrentSheetState();
  }

  public void changeContent(@NonNull Runnable action) {
    changeContent(action, false);
  }

  public void changeContent(@NonNull Runnable action, boolean skipStateAnimation) {
    if (bottomSheetBehavior == null) {
      action.run();
      return;
    }

    if (skipStateAnimation) {
      action.run();
      return;
    }

    if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
      action.run();
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    } else {
      onSlideCollapsedAction = action;
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }
  }

  public void onDestroy() {
    if (bottomSheetBehavior != null) {
      bottomSheetBehavior.removeBottomSheetCallback(sheetCallback);
      bottomSheetBehavior = null;
    }
    onRequestChatScroll = null;
    onSlideCollapsedAction = null;
  }

  public void onSlideStateChanged(int newState) {
    if (bottomSheetBehavior == null) {
      return;
    }

    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
      if (onSlideCollapsedAction != null) {
        onSlideCollapsedAction.run();
        onSlideCollapsedAction = null;
      }
      if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
      }
      return;
    }

    if (newState == BottomSheetBehavior.STATE_EXPANDED) {
      if (isAutoScrollingToBottom && onRequestChatScroll != null) {
        onRequestChatScroll.run();
      }
      isAutoScrollingToBottom = false;
    }
  }

  public void onBottomSheetSlide(
      @NonNull View bottomSheet, float slideOffset, int visibleHeight, boolean canAutoScroll) {
    isAutoScrollingToBottom = canAutoScroll;
    chatRenderer.setFooterHeight(visibleHeight);

    if (canAutoScroll || chatRenderer.isAtBottom()) {
      chatRenderer.scrollToBottom();
    }

    if (controlsRenderer != null) {
      controlsRenderer.syncBottomSheetContent(getCurrentContent(), canAutoScroll);
    }
  }

  public void setAutoScrollRequested(boolean autoScrollRequested) {
    isAutoScrollingToBottom = autoScrollRequested;
  }

  public void bindFromSnapshot(@Nullable BottomSheetMode mode) {
    bottomSheetRenderer.bindFromSnapshot(mode);
  }

  private int calculateVisibleHeight(@NonNull View bottomSheet) {
    int visibleHeight = 0;
    if (bottomSheet.getVisibility() != View.VISIBLE) {
      return visibleHeight;
    }

    if (bottomSheet.getParent() instanceof View) {
      int parentHeight = ((View) bottomSheet.getParent()).getHeight();
      visibleHeight = Math.max(parentHeight - bottomSheet.getTop(), 0);
    }

    return visibleHeight;
  }

  private void syncChatFooterForCurrentSheetState() {
    View sheet = bottomSheet;
    if (sheet == null) {
      chatRenderer.setFooterHeight(0);
      return;
    }
    if (sheet.getVisibility() == View.VISIBLE) {
      sheet.post(() -> chatRenderer.syncFooterWithBottomSheet(sheet));
      return;
    }
    chatRenderer.setFooterHeight(0);
  }
}
