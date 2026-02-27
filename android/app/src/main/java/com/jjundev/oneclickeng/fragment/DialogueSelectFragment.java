package com.jjundev.oneclickeng.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.activity.DialogueLearningActivity;
import com.jjundev.oneclickeng.dialog.DialogueGenerateDialog;
import com.jjundev.oneclickeng.dialog.DialogueLearningSettingDialog;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IDialogueGenerateManager;
import com.jjundev.oneclickeng.others.ScriptSelectAdapter;
import com.jjundev.oneclickeng.others.ScriptTemplate;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import java.util.ArrayList;
import java.util.List;

public class DialogueSelectFragment extends Fragment
    implements DialogueGenerateDialog.OnScriptParamsSelectedListener {
  private static final String DIALOG_TAG_LEARNING_SETTINGS = "DialogueLearningSettingDialog";

  private ImageButton btnBack;
  private ImageButton btnSettings;
  private RecyclerView rvScripts;
  private View layoutEmptyState;
  private AppCompatButton btnGenerate;
  private ScriptSelectAdapter adapter;
  private List<ScriptTemplate> templateList;
  private IDialogueGenerateManager scriptGenerator;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_dialogue_select, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    scriptGenerator = resolveScriptGenerator();
    scriptGenerator.initializeCache(
        new IDialogueGenerateManager.InitCallback() {
          @Override
          public void onReady() {
            android.util.Log.d("DialogueSelectFragment", "Script generator cache ready");
          }

          @Override
          public void onError(String error) {
            android.util.Log.e(
                "DialogueSelectFragment", "Script generator cache init error: " + error);
          }
        });

    btnBack = view.findViewById(R.id.btn_back);
    btnSettings = view.findViewById(R.id.btn_settings);
    rvScripts = view.findViewById(R.id.rv_scripts);
    layoutEmptyState = view.findViewById(R.id.layout_empty_state);
    btnGenerate = view.findViewById(R.id.btn_generate_script);

    setupRecyclerView();
    setupListeners();
  }

  private void setupRecyclerView() {
    templateList = new ArrayList<>();
    // Sample data
    templateList.add(
        new ScriptTemplate("☕", "카페에서 주문하기", "자연스러운 영어 회화", "안녕하세요! 따뜻한 아메리카노 한 잔 부탁합니다."));
    templateList.add(
        new ScriptTemplate("🏢", "회사에서 자기소개", "전문적인 비즈니스 표현", "만나서 반갑습니다. 저는 마케팅 팀의 김현준입니다."));
    templateList.add(
        new ScriptTemplate("✈️", "공항 입국 심사", "필수 여행 영어", "방문 목적은 관광입니다. 일주일 동안 머무를 예정이에요."));
    templateList.add(
        new ScriptTemplate("🚕", "택시 목적지 말하기", "실전 생활 표현", "기사님, 강남역으로 가주세요. 얼마나 걸릴까요?"));

    adapter =
        new ScriptSelectAdapter(
            templateList,
            template -> {
              String json = scriptGenerator.getPredefinedScript(template.getTitle());
              startScriptStudy(json, null);
            });

    rvScripts.setLayoutManager(new GridLayoutManager(getContext(), 2));
    rvScripts.setAdapter(adapter);

    // Apply layout animation to the RecyclerView
    android.view.animation.LayoutAnimationController controller =
        android.view.animation.AnimationUtils.loadLayoutAnimation(
            rvScripts.getContext(), R.anim.layout_anim_slide_fade_in);
    rvScripts.setLayoutAnimation(controller);

    updateEmptyState();
  }

  private IDialogueGenerateManager resolveScriptGenerator() {
    Context appContext = requireContext().getApplicationContext();
    AppSettings settings = new AppSettingsStore(appContext).getSettings();
    return LearningDependencyProvider.provideDialogueGenerateManager(
        appContext,
        settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
        settings.getLlmModelScript());
  }

  private void setupListeners() {
    btnBack.setOnClickListener(
        v -> {
          Navigation.findNavController(v).popBackStack();
        });

    btnSettings.setOnClickListener(v -> showDialogueLearningSettingDialog());

    btnGenerate.setOnClickListener(
        v -> {
          hideKeyboard(); // Ensure keyboard is hidden

          DialogueGenerateDialog dialogueGenerateDialog = new DialogueGenerateDialog();
          dialogueGenerateDialog.show(getChildFragmentManager(), "DialogueGenerateDialog");
        });
  }

  private void showDialogueLearningSettingDialog() {
    if (!isAdded()) {
      return;
    }

    FragmentManager fragmentManager = getChildFragmentManager();
    if (fragmentManager.isStateSaved()) {
      return;
    }

    Fragment existingDialog =
        fragmentManager.findFragmentByTag(DIALOG_TAG_LEARNING_SETTINGS);
    if (existingDialog != null && existingDialog.isAdded()) {
      return;
    }

    new DialogueLearningSettingDialog()
        .show(fragmentManager, DIALOG_TAG_LEARNING_SETTINGS);
  }

  @Override
  public void onScriptParamsSelected(
      String level, String topic, String format, int length, DialogueGenerateDialog dialog) {
    generateScript(level, topic, format, length, dialog);
  }

  private void generateScript(
      String level, String topic, String format, int length, DialogueGenerateDialog dialog) {
    // If dialog is null, it means it was called from somewhere else (not the case
    // here yet)
    // If dialog is not null, the dialog itself is showing the loading UI

    scriptGenerator.generateScript(
        level,
        topic,
        format,
        length,
        new com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts
            .IDialogueGenerateManager.ScriptGenerationCallback() {
          @Override
          public void onSuccess(String jsonResult) {
            if (!isAdded()) return;

            if (dialog != null) {
              dialog.dismiss();
            }

            startScriptStudy(jsonResult, level);
          }

          @Override
          public void onError(Throwable t) {
            if (!isAdded()) return;

            if (dialog != null) {
              dialog.showLoading(false);
            }
            Toast.makeText(getContext(), "대본 생성 중 오류가 발생했어요", Toast.LENGTH_SHORT).show();
          }
        });
  }

  private void startScriptStudy(@NonNull String scriptJson, @Nullable String level) {
    if (getActivity() == null) return;

    Intent intent = new Intent(getActivity(), DialogueLearningActivity.class);
    intent.putExtra("SCRIPT_DATA", scriptJson);
    if (level != null && !level.trim().isEmpty()) {
      intent.putExtra(DialogueLearningActivity.EXTRA_SCRIPT_LEVEL, level);
    }
    startActivity(intent);

    hideKeyboard();
  }

  private void updateEmptyState() {
    if (templateList.isEmpty()) {
      layoutEmptyState.setVisibility(View.VISIBLE);
      rvScripts.setVisibility(View.GONE);
    } else {
      layoutEmptyState.setVisibility(View.GONE);
      rvScripts.setVisibility(View.VISIBLE);
      rvScripts.scheduleLayoutAnimation();
    }
  }

  private void hideKeyboard() {
    View view = getActivity().getCurrentFocus();
    if (view != null) {
      InputMethodManager imm =
          (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
  }
}
