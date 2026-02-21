package com.jjundev.oneclickeng.fragment.history;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;

import java.util.List;

public class LearningHistoryFragment extends Fragment {

  private static final String TAG = "LearningHistoryFrag";

  private LearningHistoryViewModel viewModel;
  private LearningHistoryAdapter adapter;
  private TabLayout tabLayout;
  private RecyclerView recyclerView;
  private View emptyStateLayout;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_learning_history, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    initViewModel();
    initViews(view);
    observeViewModel();
  }

  private void initViews(View view) {
    tabLayout = view.findViewById(R.id.tab_layout_history);
    recyclerView = view.findViewById(R.id.rv_learning_history);
    emptyStateLayout = view.findViewById(R.id.layout_empty_state);

    setupTabs();

    adapter =
        new LearningHistoryAdapter(
            item -> {
              // Handle save/unsave click later
              logDebug("Item clicked: " + item.getType());
            });
    recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
    recyclerView.setAdapter(adapter);

    com.jjundev.oneclickeng.utils.SwipeHelper swipeHelper =
        new com.jjundev.oneclickeng.utils.SwipeHelper(requireContext(), recyclerView) {
          @Override
          public void instantiateUnderlayButton(
              RecyclerView.ViewHolder viewHolder, java.util.List<UnderlayButton> underlayButtons) {
            underlayButtons.add(
                new UnderlayButton(
                    requireContext(),
                    "",
                    androidx.core.content.ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_delete_sweep),
                    R.color.purple_700,
                    new UnderlayButtonClickListener() {
                      @Override
                      public void onClick(int pos) {
                        HistoryItemWrapper itemToSwipe = adapter.getItem(pos);
                        if (itemToSwipe != null && viewModel != null) {
                          viewModel.removeCard(itemToSwipe);
                          android.widget.Toast.makeText(
                                  requireContext(),
                                  "카드가 삭제되었습니다.",
                                  android.widget.Toast.LENGTH_SHORT)
                              .show();
                        }
                      }
                    }));
          }
        };

    View btnQuiz = view.findViewById(R.id.btn_history_quiz);
    if (btnQuiz != null) {
      btnQuiz.setOnClickListener(
          v -> {
            HistoryQuizConfigDialog dialog = new HistoryQuizConfigDialog();
            dialog.show(getChildFragmentManager(), "HistoryQuizConfigDialog");
          });
    }

    getChildFragmentManager()
        .setFragmentResultListener(
            HistoryQuizConfigDialog.REQUEST_KEY,
            getViewLifecycleOwner(),
            (requestKey, result) -> {
              int periodBucket = result.getInt(HistoryQuizConfigDialog.BUNDLE_KEY_PERIOD_BUCKET);
              int questionCount = result.getInt(HistoryQuizConfigDialog.BUNDLE_KEY_QUESTION_COUNT);

              int currentTab = tabLayout.getSelectedTabPosition();
              SummaryData seed =
                  viewModel.generateQuizSeed(periodBucket, currentTab);

              if (seed == null) {
                android.widget.Toast.makeText(
                        requireContext(),
                        R.string.history_quiz_err_no_items,
                        android.widget.Toast.LENGTH_SHORT)
                    .show();
              } else {
                android.content.Intent intent =
                    new android.content.Intent(
                        requireContext(),
                        com.jjundev.oneclickeng.activity.DialogueQuizActivity.class);
                intent.putExtra(
                    com.jjundev.oneclickeng.activity.DialogueQuizActivity.EXTRA_SUMMARY_JSON,
                    new com.google.gson.Gson().toJson(seed));
                intent.putExtra(
                    com.jjundev.oneclickeng.activity.DialogueQuizActivity
                        .EXTRA_REQUESTED_QUESTION_COUNT,
                    questionCount);
                try {
                  startActivity(intent);
                  // Enter animation for the Activity
                  if (getActivity() != null) {
                    getActivity()
                        .overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                  }
                } catch (Exception e) {
                  logDebug("Activity start failed: " + e.getMessage());
                }
              }
            });
  }

  private void setupTabs() {
    tabLayout.addTab(tabLayout.newTab().setText("표현"));
    tabLayout.addTab(tabLayout.newTab().setText("단어"));
    tabLayout.addTab(tabLayout.newTab().setText("문장"));

    tabLayout.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab tab) {
            filterData(tab.getPosition());
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {}

          @Override
          public void onTabReselected(TabLayout.Tab tab) {
            filterData(tab.getPosition());
          }
        });
  }

  private void initViewModel() {
    viewModel = new ViewModelProvider(this).get(LearningHistoryViewModel.class);
    viewModel.loadSavedCards();
  }

  private void observeViewModel() {
    viewModel
        .getHistoryItems()
        .observe(
            getViewLifecycleOwner(),
            items -> {
              if (items != null) {
                filterData(tabLayout.getSelectedTabPosition());
              }
            });
  }

  private void filterData(int tabPosition) {
    List<HistoryItemWrapper> allItems = viewModel.getHistoryItems().getValue();
    if (allItems == null) return;

    adapter.submitList(allItems, tabPosition);

    boolean isEmpty = adapter.getItemCount() == 0;
    if (isEmpty) {
      if (recyclerView != null) recyclerView.setVisibility(View.GONE);
      if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.VISIBLE);
    } else {
      if (recyclerView != null) {
        recyclerView.setVisibility(View.VISIBLE);
        android.view.animation.LayoutAnimationController controller =
            android.view.animation.AnimationUtils.loadLayoutAnimation(
                recyclerView.getContext(), R.anim.layout_anim_slide_fade_in);
        recyclerView.setLayoutAnimation(controller);
        recyclerView.scheduleLayoutAnimation();
      }
      if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.GONE);
    }
  }

  private void logDebug(String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }
}
