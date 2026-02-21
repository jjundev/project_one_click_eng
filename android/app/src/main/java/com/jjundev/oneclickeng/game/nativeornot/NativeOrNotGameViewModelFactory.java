package com.jjundev.oneclickeng.game.nativeornot;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.jjundev.oneclickeng.game.nativeornot.manager.INativeOrNotGenerationManager;

public final class NativeOrNotGameViewModelFactory implements ViewModelProvider.Factory {
  @NonNull private final INativeOrNotGenerationManager generationManager;
  @NonNull private final NativeOrNotStatsStore statsStore;

  public NativeOrNotGameViewModelFactory(
      @NonNull INativeOrNotGenerationManager generationManager,
      @NonNull NativeOrNotStatsStore statsStore) {
    this.generationManager = generationManager;
    this.statsStore = statsStore;
  }

  @NonNull
  @Override
  @SuppressWarnings("unchecked")
  public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
    if (!modelClass.isAssignableFrom(NativeOrNotGameViewModel.class)) {
      throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
    return (T) new NativeOrNotGameViewModel(generationManager, statsStore);
  }
}
