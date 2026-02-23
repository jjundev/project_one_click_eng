package com.jjundev.oneclickeng.fragment.history;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.activity.DialogueQuizActivity;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.QuizData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LearningHistoryFragment extends Fragment {

  private static final String TAG = "LearningHistoryFrag";
  private static final String TAG_HISTORY_QUIZ_CONFIG_DIALOG = "HistoryQuizConfigDialog";

  private LearningHistoryViewModel viewModel;
  private LearningHistoryAdapter adapter;
  private TabLayout tabLayout;
  private RecyclerView recyclerView;
  private View emptyStateLayout;
  @Nullable private HistoryQuizConfigDialog pendingConfigDialog;
  private long quizPreparationRequestId = 0L;

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
            pendingConfigDialog = dialog;
            dialog.show(getChildFragmentManager(), TAG_HISTORY_QUIZ_CONFIG_DIALOG);
          });
    }

    getChildFragmentManager()
        .setFragmentResultListener(
            HistoryQuizConfigDialog.REQUEST_KEY,
        getViewLifecycleOwner(),
            (requestKey, result) -> {
              long requestId = ++quizPreparationRequestId;
              int periodBucket = result.getInt(HistoryQuizConfigDialog.BUNDLE_KEY_PERIOD_BUCKET);
              int questionCount = result.getInt(HistoryQuizConfigDialog.BUNDLE_KEY_QUESTION_COUNT);
              int currentTab = tabLayout != null ? tabLayout.getSelectedTabPosition() : 0;
              HistoryQuizConfigDialog dialog = resolveConfigDialog();

              if (dialog == null) {
                return;
              }

              dialog.setLoadingState(true);
              if (!isAdded()) {
                dialog.setLoadingState(false);
                return;
              }

              SummaryData seed = viewModel.generateQuizSeed(periodBucket, currentTab);
              if (seed == null) {
                dialog.setLoadingState(false);
                if (requestId == quizPreparationRequestId) {
                  quizPreparationRequestId = 0L;
                }
                if (dialog == pendingConfigDialog) {
                  pendingConfigDialog = null;
                }
                showToast(R.string.history_quiz_err_no_items);
                return;
              }

              AppSettings settings = new AppSettingsStore(requireContext()).getSettings();
              IQuizGenerationManager quizManager =
                  LearningDependencyProvider.provideQuizGenerationManager(
                      requireContext(),
                      settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
                      settings.getLlmModelSummary());

              final boolean[] completed = {false};
              final SummaryData finalSeed = seed;
              quizManager.generateQuizFromSummaryStreamingAsync(
                  finalSeed,
                  questionCount,
                  new IQuizGenerationManager.QuizStreamingCallback() {
                    @Override
                    public void onQuestion(@NonNull QuizData.QuizQuestion question) {
                      if (requestId != quizPreparationRequestId || completed[0]) {
                        return;
                      }
                      if (!isValidQuestionReadyForStart(question)) {
                        return;
                      }
                      completed[0] = true;
                      dialog.setLoadingState(false);
                      quizPreparationRequestId = 0L;
                      if (dialog == pendingConfigDialog) {
                        pendingConfigDialog = null;
                      }
                      dialog.dismiss();
                      startDialogueQuiz(finalSeed, questionCount);
                    }

                    @Override
                    public void onComplete(@Nullable String warningMessage) {
                      if (requestId != quizPreparationRequestId || completed[0]) {
                        return;
                      }
                      completed[0] = true;
                      quizPreparationRequestId = 0L;
                      dialog.setLoadingState(false);
                      if (dialog == pendingConfigDialog) {
                        pendingConfigDialog = null;
                      }
                      if (isAdded()) {
                        if (warningMessage == null || warningMessage.trim().isEmpty()) {
                          showToast(R.string.quiz_error_default);
                        } else {
                          Toast.makeText(
                                  requireContext(),
                                  getString(
                                      R.string.quiz_error_default)
                                      + " ("
                                      + warningMessage
                                      + ")",
                                  Toast.LENGTH_SHORT)
                              .show();
                        }
                      }
                    }

                    @Override
                    public void onFailure(@NonNull String error) {
                      if (requestId != quizPreparationRequestId || completed[0]) {
                        return;
                      }
                      completed[0] = true;
                      quizPreparationRequestId = 0L;
                      dialog.setLoadingState(false);
                      if (dialog == pendingConfigDialog) {
                        pendingConfigDialog = null;
                      }
                      if (isAdded()) {
                        String safeMessage = trimToNull(error);
                        if (safeMessage == null) {
                          showToast(R.string.quiz_error_default);
                        } else {
                          showToast(safeMessage);
                        }
                      }
                    }
                  });
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

  private void startDialogueQuiz(@NonNull SummaryData seed, int questionCount) {
    if (!isAdded()) {
      return;
    }
    Intent intent = new Intent(requireContext(), DialogueQuizActivity.class);
    intent.putExtra(DialogueQuizActivity.EXTRA_SUMMARY_JSON, new Gson().toJson(seed));
    intent.putExtra(DialogueQuizActivity.EXTRA_REQUESTED_QUESTION_COUNT, questionCount);
    try {
      startActivity(intent);
      if (getActivity() != null) {
        getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
      }
    } catch (Exception e) {
      logDebug("Activity start failed: " + e.getMessage());
      showToast("퀴즈 이동 중 오류가 발생했어요.");
    }
  }

  private boolean isValidQuestionReadyForStart(@Nullable QuizData.QuizQuestion question) {
    if (question == null) {
      return false;
    }

    String questionText = trimToNull(question.getQuestion());
    String answer = trimToNull(question.getAnswer());
    if (questionText == null || answer == null) {
      return false;
    }

    List<String> choices = question.getChoices();
    if (choices == null) {
      return false;
    }
    List<String> sanitizedChoices = sanitizeChoices(choices, answer);
    return sanitizedChoices != null && sanitizedChoices.size() >= 2;
  }

  @Nullable
  private List<String> sanitizeChoices(@Nullable List<String> sourceChoices, @NonNull String answer) {
    if (sourceChoices == null || sourceChoices.isEmpty()) {
      return null;
    }

    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    String trimmedAnswer = trimToNull(answer);
    for (String rawChoice : sourceChoices) {
      String choice = trimToNull(rawChoice);
      if (choice == null) {
        continue;
      }
      String normalized = normalize(choice);
      if (!seen.add(normalized)) {
        continue;
      }
      result.add(choice);
    }

    if (result.isEmpty() && trimmedAnswer != null) {
      result.add(trimmedAnswer);
    }

    if (trimmedAnswer != null && !seen.contains(normalize(trimmedAnswer))) {
      result.add(trimmedAnswer);
    }

    return result.size() >= 2 ? result : null;
  }

  private void showToast(int messageResId) {
    if (isAdded()) {
      Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show();
    }
  }

  private void showToast(@NonNull String message) {
    if (isAdded()) {
      Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
  }

  @Nullable
  private static String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @NonNull
  private static String normalize(@Nullable String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private HistoryQuizConfigDialog resolveConfigDialog() {
    androidx.fragment.app.Fragment dialogFragment =
        getChildFragmentManager().findFragmentByTag(TAG_HISTORY_QUIZ_CONFIG_DIALOG);
    if (dialogFragment instanceof HistoryQuizConfigDialog) {
      return (HistoryQuizConfigDialog) dialogFragment;
    }
    return pendingConfigDialog;
  }

  private void logDebug(String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }
}
