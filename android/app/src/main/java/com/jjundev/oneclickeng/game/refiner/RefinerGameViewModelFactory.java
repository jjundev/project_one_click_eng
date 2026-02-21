package com.jjundev.oneclickeng.game.refiner;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.jjundev.oneclickeng.game.refiner.manager.IRefinerGenerationManager;

public final class RefinerGameViewModelFactory implements ViewModelProvider.Factory {
  @NonNull private final IRefinerGenerationManager generationManager;
  @NonNull private final RefinerStatsStore statsStore;

  public RefinerGameViewModelFactory(
      @NonNull IRefinerGenerationManager generationManager, @NonNull RefinerStatsStore statsStore) {
    this.generationManager = generationManager;
    this.statsStore = statsStore;
  }

  @NonNull
  @Override
  @SuppressWarnings("unchecked")
  public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
    if (!modelClass.isAssignableFrom(RefinerGameViewModel.class)) {
      throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
    return (T) new RefinerGameViewModel(generationManager, statsStore);
  }
}
