package com.example.test.fragment;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.test.fragment.dialoguelearning.manager_contracts.IQuizGenerationManager;

public class DialogueQuizViewModelFactory implements ViewModelProvider.Factory {

  private final IQuizGenerationManager quizGenerationManager;

  public DialogueQuizViewModelFactory(@NonNull IQuizGenerationManager quizGenerationManager) {
    this.quizGenerationManager = quizGenerationManager;
  }

  @NonNull
  @Override
  @SuppressWarnings("unchecked")
  public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
    if (!modelClass.isAssignableFrom(DialogueQuizViewModel.class)) {
      throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
    return (T) new DialogueQuizViewModel(quizGenerationManager);
  }
}
