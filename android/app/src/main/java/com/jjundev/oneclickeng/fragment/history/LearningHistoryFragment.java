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
import java.util.List;

public class LearningHistoryFragment extends Fragment {

  private static final String TAG = "LearningHistoryFrag";

  private LearningHistoryViewModel viewModel;
  private LearningHistoryAdapter adapter;
  private TabLayout tabLayout;
  private RecyclerView recyclerView;

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

    setupTabs();

    adapter =
        new LearningHistoryAdapter(
            item -> {
              // Handle save/unsave click later
              logDebug("Item clicked: " + item.getType());
            });
    recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
    recyclerView.setAdapter(adapter);

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
              com.jjundev.oneclickeng.fragment.dialoguelearning.model.SummaryData seed =
                  viewModel.generateQuizSeed(periodBucket, currentTab);

              if (seed == null) {
                android.widget.Toast.makeText(
                        requireContext(),
                        R.string.history_quiz_err_no_items,
                        android.widget.Toast.LENGTH_SHORT)
                    .show();
              } else {
                Bundle args = new Bundle();
                args.putString(
                    com.jjundev.oneclickeng.fragment.DialogueQuizFragment.ARG_SUMMARY_JSON,
                    new com.google.gson.Gson().toJson(seed));
                args.putInt(
                    com.jjundev.oneclickeng.fragment.DialogueQuizFragment
                        .ARG_REQUESTED_QUESTION_COUNT,
                    questionCount);
                args.putInt(
                    com.jjundev.oneclickeng.fragment.DialogueQuizFragment.ARG_FINISH_BEHAVIOR,
                    com.jjundev.oneclickeng.fragment.DialogueQuizFragment.POP_BACK_STACK);
                try {
                  androidx.navigation.fragment.NavHostFragment.findNavController(this)
                      .navigate(R.id.action_learningHistoryFragment_to_dialogueQuizFragment, args);
                } catch (Exception e) {
                  logDebug("Navigation failed: " + e.getMessage());
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
    // Load dummy data
    viewModel.loadDummyData();
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

    if (recyclerView != null) {
      android.view.animation.LayoutAnimationController controller =
          android.view.animation.AnimationUtils.loadLayoutAnimation(
              recyclerView.getContext(), R.anim.layout_anim_slide_fade_in);
      recyclerView.setLayoutAnimation(controller);
      recyclerView.scheduleLayoutAnimation();
    }
  }

  private void logDebug(String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }
}
