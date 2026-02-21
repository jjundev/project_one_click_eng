package com.jjundev.oneclickeng.game.minefield;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.jjundev.oneclickeng.game.minefield.manager.IMinefieldGenerationManager;

public final class MinefieldGameViewModelFactory implements ViewModelProvider.Factory {
  @NonNull private final IMinefieldGenerationManager generationManager;
  @NonNull private final MinefieldStatsStore statsStore;

  public MinefieldGameViewModelFactory(
      @NonNull IMinefieldGenerationManager generationManager,
      @NonNull MinefieldStatsStore statsStore) {
    this.generationManager = generationManager;
    this.statsStore = statsStore;
  }

  @NonNull
  @Override
  @SuppressWarnings("unchecked")
  public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
    if (!modelClass.isAssignableFrom(MinefieldGameViewModel.class)) {
      throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
    return (T) new MinefieldGameViewModel(generationManager, statsStore);
  }
}
