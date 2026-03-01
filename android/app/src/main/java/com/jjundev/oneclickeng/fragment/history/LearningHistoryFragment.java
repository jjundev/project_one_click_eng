package com.jjundev.oneclickeng.fragment.history;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.jjundev.oneclickeng.activity.QuizActivity;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.QuizData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import com.jjundev.oneclickeng.learning.quiz.session.QuizStreamingSessionStore;
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
  @Nullable private String pendingQuizSessionId;
  @Nullable private QuizStreamingSessionStore.Listener pendingQuizSessionListener;
  @NonNull private final Handler uiHandler = new Handler(Looper.getMainLooper());
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

  @Override
  public void onDestroyView() {
    clearPendingQuizSession(true);
    pendingConfigDialog = null;
    quizPreparationRequestId = 0L;
    super.onDestroyView();
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
                                  requireContext(), "카드를 삭제했어요", android.widget.Toast.LENGTH_SHORT)
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
              clearPendingQuizSession(true);
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
                finishQuizPreparation(dialog, requestId);
                showToast(R.string.history_quiz_err_no_items);
                return;
              }

              AppSettings settings = new AppSettingsStore(requireContext()).getSettings();
              IQuizGenerationManager quizManager =
                  LearningDependencyProvider.provideQuizGenerationManager(
                      requireContext(),
                      settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
                      settings.getLlmModelSummary());
              QuizStreamingSessionStore sessionStore =
                  LearningDependencyProvider.provideQuizStreamingSessionStore();

              final boolean[] completed = {false};
              final SummaryData finalSeed = seed;
              String sessionId = sessionStore.startSession(quizManager, finalSeed, questionCount);

              QuizStreamingSessionStore.Listener listener =
                  new QuizStreamingSessionStore.Listener() {
                    @Override
                    public void onQuestion(@NonNull QuizData.QuizQuestion question) {
                      runOnMainThread(
                          () -> {
                            if (requestId != quizPreparationRequestId || completed[0]) {
                              return;
                            }
                            if (!isValidQuestionReadyForStart(question)) {
                              return;
                            }
                            completed[0] = true;
                            startPreparedQuiz(dialog, requestId, finalSeed, questionCount, sessionId);
                          });
                    }

                    @Override
                    public void onComplete(@Nullable String warningMessage) {
                      runOnMainThread(
                          () -> {
                            if (requestId != quizPreparationRequestId || completed[0]) {
                              return;
                            }
                            completed[0] = true;
                            showPreparationError(dialog, requestId, warningMessage);
                          });
                    }

                    @Override
                    public void onFailure(@NonNull String error) {
                      runOnMainThread(
                          () -> {
                            if (requestId != quizPreparationRequestId || completed[0]) {
                              return;
                            }
                            completed[0] = true;
                            showPreparationError(dialog, requestId, error);
                          });
                    }
                  };
              pendingQuizSessionId = sessionId;
              pendingQuizSessionListener = listener;

              QuizStreamingSessionStore.Snapshot snapshot =
                  sessionStore.attach(sessionId, listener);
              if (snapshot == null) {
                completed[0] = true;
                showPreparationError(dialog, requestId, null);
                return;
              }
              if (hasStartableQuestion(snapshot.getBufferedQuestions())) {
                completed[0] = true;
                startPreparedQuiz(dialog, requestId, finalSeed, questionCount, sessionId);
                return;
              }

              String snapshotFailure = trimToNull(snapshot.getFailureMessage());
              if (snapshotFailure != null) {
                completed[0] = true;
                showPreparationError(dialog, requestId, snapshotFailure);
                return;
              }
              if (snapshot.isCompleted()) {
                completed[0] = true;
                showPreparationError(dialog, requestId, snapshot.getWarningMessage());
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

  private void startPreparedQuiz(
      @NonNull HistoryQuizConfigDialog dialog,
      long requestId,
      @NonNull SummaryData seed,
      int questionCount,
      @NonNull String sessionId) {
    clearPendingQuizSession(false);
    finishQuizPreparation(dialog, requestId);
    if (dialog.isAdded()) {
      dialog.dismiss();
    }
    startDialogueQuiz(seed, questionCount, sessionId);
  }

  private void showPreparationError(
      @NonNull HistoryQuizConfigDialog dialog, long requestId, @Nullable String detailMessage) {
    clearPendingQuizSession(true);
    finishQuizPreparation(dialog, requestId);
    if (!isAdded()) {
      return;
    }
    String safeMessage = trimToNull(detailMessage);
    if (safeMessage == null) {
      showToast(R.string.quiz_error_default);
      return;
    }
    Toast.makeText(
            requireContext(),
            getString(R.string.quiz_error_default) + " (" + safeMessage + ")",
            Toast.LENGTH_SHORT)
        .show();
  }

  private void finishQuizPreparation(@NonNull HistoryQuizConfigDialog dialog, long requestId) {
    dialog.setLoadingState(false);
    if (requestId == quizPreparationRequestId) {
      quizPreparationRequestId = 0L;
    }
    if (dialog == pendingConfigDialog) {
      pendingConfigDialog = null;
    }
  }

  private void clearPendingQuizSession(boolean releaseSession) {
    String sessionId = pendingQuizSessionId;
    QuizStreamingSessionStore.Listener listener = pendingQuizSessionListener;
    pendingQuizSessionId = null;
    pendingQuizSessionListener = null;

    if (sessionId == null) {
      return;
    }
    QuizStreamingSessionStore sessionStore =
        LearningDependencyProvider.provideQuizStreamingSessionStore();
    if (listener != null) {
      sessionStore.detach(sessionId, listener);
    }
    if (releaseSession) {
      sessionStore.release(sessionId);
    }
  }

  private boolean hasStartableQuestion(@Nullable List<QuizData.QuizQuestion> questions) {
    if (questions == null || questions.isEmpty()) {
      return false;
    }
    for (QuizData.QuizQuestion question : questions) {
      if (isValidQuestionReadyForStart(question)) {
        return true;
      }
    }
    return false;
  }

  private void startDialogueQuiz(
      @NonNull SummaryData seed, int questionCount, @NonNull String sessionId) {
    if (!isAdded()) {
      LearningDependencyProvider.provideQuizStreamingSessionStore().release(sessionId);
      return;
    }
    Intent intent = new Intent(requireContext(), QuizActivity.class);
    intent.putExtra(QuizActivity.EXTRA_SUMMARY_JSON, new Gson().toJson(seed));
    intent.putExtra(QuizActivity.EXTRA_REQUESTED_QUESTION_COUNT, questionCount);
    intent.putExtra(QuizActivity.EXTRA_STREAM_SESSION_ID, sessionId);
    try {
      startActivity(intent);
      if (getActivity() != null) {
        getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
      }
    } catch (Exception e) {
      LearningDependencyProvider.provideQuizStreamingSessionStore().release(sessionId);
      logDebug("Activity start failed: " + e.getMessage());
      showToast("퀴즈 이동 중 오류가 발생했어요.");
    }
  }

  private boolean isValidQuestionReadyForStart(@Nullable QuizData.QuizQuestion question) {
    if (question == null) {
      return false;
    }

    String questionText = trimToNull(question.getQuestionMain());
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
  private List<String> sanitizeChoices(
      @Nullable List<String> sourceChoices, @NonNull String answer) {
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

  private void runOnMainThread(@NonNull Runnable action) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      action.run();
      return;
    }
    uiHandler.post(action);
  }
}
