package com.jjundev.oneclickeng.settings;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppSettingsStoreTest {

  private Context context;
  private AppSettingsStore store;

  @Before
  public void setUp() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteSharedPreferences(AppSettingsStore.PREF_NAME);
    store = new AppSettingsStore(context);
  }

  @Test
  public void minefieldModel_defaultsToConstant() {
    AppSettings settings = store.getSettings();
    assertEquals(AppSettings.DEFAULT_MODEL_MINEFIELD, settings.getLlmModelMinefield());
  }

  @Test
  public void minefieldModel_persistsAndRestores() {
    store.setLlmModelMinefield("gemini-2.5-flash");
    AppSettings settings = store.getSettings();
    assertEquals("gemini-2.5-flash", settings.getLlmModelMinefield());
  }

  @Test
  public void minefieldModel_emptyValue_fallsBackToDefault() {
    store.setLlmModelMinefield(" ");
    AppSettings settings = store.getSettings();
    assertEquals(AppSettings.DEFAULT_MODEL_MINEFIELD, settings.getLlmModelMinefield());
  }

  @Test
  public void refinerModel_defaultsToConstant() {
    AppSettings settings = store.getSettings();
    assertEquals(AppSettings.DEFAULT_MODEL_REFINER, settings.getLlmModelRefiner());
  }

  @Test
  public void refinerModel_persistsAndRestores() {
    store.setLlmModelRefiner("gemini-2.5-flash");
    AppSettings settings = store.getSettings();
    assertEquals("gemini-2.5-flash", settings.getLlmModelRefiner());
  }
}
