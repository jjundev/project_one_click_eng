package com.jjundev.oneclickeng.learning.dialoguelearning.coordinator;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.tool.RecordingAudioPlayer;
import java.util.Locale;
import java.util.Set;

public final class DialoguePlaybackCoordinator {
  private static final float DEFAULT_TTS_SPEECH_RATE = 1.00f;
  @NonNull private static final Locale DEFAULT_TTS_LOCALE = Locale.US;

  public interface LoggerDelegate {
    void trace(@NonNull String key);

    void gate(@NonNull String key);

    void ux(@NonNull String key, @Nullable String fields);
  }

  public interface OnTtsInitializedListener {
    void onTtsInitialized(boolean ready);
  }

  public interface PlaybackErrorListener {
    void onError(@NonNull String message);
  }

  @NonNull private final Handler mainHandler;
  @NonNull private final LoggerDelegate loggerDelegate;

  @Nullable private TextToSpeech tts;
  private boolean isTtsReady;
  @Nullable private RecordingAudioPlayer recordingAudioPlayer;
  @Nullable private ImageView currentlyPlayingSpeakerBtn;
  @Nullable private Runnable ttsWatchdogRunnable;
  @Nullable private Runnable currentTtsOnDoneCallback;
  @Nullable private String currentTtsSource;
  private boolean isPlaybackStopInProgress;
  private float ttsSpeechRate = DEFAULT_TTS_SPEECH_RATE;
  @NonNull private Locale ttsLocale = DEFAULT_TTS_LOCALE;

  public DialoguePlaybackCoordinator(
      @NonNull Handler mainHandler, @NonNull LoggerDelegate loggerDelegate) {
    this.mainHandler = mainHandler;
    this.loggerDelegate = loggerDelegate;
  }

  public void initialize(@NonNull Context context, @NonNull OnTtsInitializedListener listener) {
    release();
    recordingAudioPlayer = new RecordingAudioPlayer();
    tts =
        new TextToSpeech(
            context,
            status -> {
              boolean ready = false;
              if (status == TextToSpeech.SUCCESS && tts != null) {
                ready = applyConfiguredTtsLanguage();
                tts.setSpeechRate(ttsSpeechRate);
              }
              isTtsReady = ready;
              listener.onTtsInitialized(ready);
            });

    if (tts != null) {
      tts.setOnUtteranceProgressListener(
          new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
              onTtsTerminal(false);
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onError(String utteranceId) {
              onTtsTerminal(true);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
              onError(utteranceId);
            }
          });
    }
  }

  public void release() {
    if (ttsWatchdogRunnable != null) {
      mainHandler.removeCallbacks(ttsWatchdogRunnable);
      ttsWatchdogRunnable = null;
    }
    currentTtsOnDoneCallback = null;
    currentTtsSource = null;
    isTtsReady = false;
    stopAllPlayback("release");
    if (tts != null) {
      tts.shutdown();
      tts = null;
    }
    if (recordingAudioPlayer != null) {
      recordingAudioPlayer.stop();
      recordingAudioPlayer = null;
    }
    resetSpeakerButton();
  }

  public void stopAllPlayback(@NonNull String reason) {
    if (isPlaybackStopInProgress) {
      return;
    }
    isPlaybackStopInProgress = true;
    try {
      if (tts != null && tts.isSpeaking()) {
        tts.stop();
      }
      if (recordingAudioPlayer != null) {
        recordingAudioPlayer.stop();
      }
      resetSpeakerButton();
    } finally {
      isPlaybackStopInProgress = false;
    }
  }

  public void applyTtsSettings(float speechRate, @Nullable String localeTag) {
    ttsSpeechRate = clamp(speechRate, 0.5f, 1.5f);
    ttsLocale = resolveLocale(localeTag);
    if (tts == null) {
      return;
    }
    applyConfiguredTtsLanguage();
    tts.setSpeechRate(ttsSpeechRate);
  }

  public void playMessageTts(
      @NonNull String text,
      @Nullable ImageView speakerBtn,
      @Nullable String gender,
      @Nullable PlaybackErrorListener errorListener) {
    if (speakerBtn != null && currentlyPlayingSpeakerBtn == speakerBtn) {
      stopAllPlayback("playMessageTts_toggle");
      return;
    }

    stopAllPlayback("playMessageTts_start");

    if (!isTtsReady || tts == null) {
      loggerDelegate.ux("UX_TTS_DONE", "source=message_error");
      if (errorListener != null) {
        errorListener.onError("TTS unavailable");
      }
      return;
    }

    if (speakerBtn != null) {
      currentlyPlayingSpeakerBtn = speakerBtn;
      speakerBtn.setImageResource(R.drawable.ic_speaker_active);
      speakerBtn.setColorFilter(0xFF2196F3);
    }

    currentTtsSource = "message";
    currentTtsOnDoneCallback = null;
    loggerDelegate.ux("UX_TTS_START", "source=message");

    applyConfiguredTtsLanguage();
    if (shouldApplyGenderVoiceSelection()) {
      setTtsVoice(gender);
    }
    Bundle params = new Bundle();
    tts.setSpeechRate(ttsSpeechRate);
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "tts_utterance");
  }

  public void playRecordedAudio(
      @Nullable byte[] audioData,
      @Nullable ImageView speakerBtn,
      @Nullable PlaybackErrorListener errorListener) {
    if (speakerBtn != null && currentlyPlayingSpeakerBtn == speakerBtn) {
      stopAllPlayback("playRecordedAudio_toggle");
      return;
    }

    stopAllPlayback("playRecordedAudio_start");

    if (audioData == null || audioData.length == 0 || recordingAudioPlayer == null) {
      loggerDelegate.ux("UX_TTS_DONE", "source=recorded_error");
      if (errorListener != null) {
        errorListener.onError("Audio unavailable");
      }
      return;
    }

    if (speakerBtn != null) {
      currentlyPlayingSpeakerBtn = speakerBtn;
      speakerBtn.setImageResource(R.drawable.ic_speaker_active);
      speakerBtn.setColorFilter(0xFF2196F3);
    }

    loggerDelegate.ux("UX_TTS_START", "source=recorded");
    recordingAudioPlayer.play(
        audioData,
        new RecordingAudioPlayer.PlaybackCallback() {
          @Override
          public void onPlaybackCompleted() {
            resetSpeakerButton();
            loggerDelegate.ux("UX_TTS_DONE", "source=recorded_callback");
          }

          @Override
          public void onPlaybackError(String error) {
            resetSpeakerButton();
            loggerDelegate.ux("UX_TTS_DONE", "source=recorded_error");
            if (errorListener != null) {
              errorListener.onError(error == null ? "Playback failed" : error);
            }
          }
        });
  }

  public void playScriptTts(
      @Nullable String text,
      @Nullable String gender,
      @Nullable Runnable activateLatestAiSpeaker,
      @NonNull Runnable onComplete) {
    if (text == null || text.isEmpty()) {
      loggerDelegate.trace("TRACE_TTS_FLOW reason=empty_text");
      onComplete.run();
      return;
    }
    loggerDelegate.trace("TRACE_TTS_FLOW source=playScriptTts textLen=" + text.length());

    stopAllPlayback("playScriptTts_start");

    if (activateLatestAiSpeaker != null) {
      activateLatestAiSpeaker.run();
    }

    if (ttsWatchdogRunnable != null) {
      mainHandler.removeCallbacks(ttsWatchdogRunnable);
    }
    ttsWatchdogRunnable =
        () -> {
          Log.w("DialoguePlaybackCoordinator", "TTS Watchdog triggered - forcing next step");
          loggerDelegate.trace("TRACE_TTS_FLOW source=watchdog");
          loggerDelegate.gate("M1_TTS_DONE source=watchdog");
          loggerDelegate.ux("UX_TTS_DONE", "source=watchdog");
          currentTtsOnDoneCallback = null;
          currentTtsSource = null;
          onComplete.run();
        };
    mainHandler.postDelayed(ttsWatchdogRunnable, 7000);

    currentTtsSource = "script";
    currentTtsOnDoneCallback = onComplete;

    if (isTtsReady && tts != null) {
      loggerDelegate.trace("TRACE_TTS_FLOW source=tts_start");
      loggerDelegate.gate("M1_TTS_START");
      loggerDelegate.ux("UX_TTS_START", "source=script");
      applyConfiguredTtsLanguage();
      if (shouldApplyGenderVoiceSelection()) {
        setTtsVoice(gender);
      }
      Bundle params = new Bundle();
      tts.setSpeechRate(ttsSpeechRate);
      tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "script_tts_utterance");
      return;
    }

    loggerDelegate.trace("TRACE_TTS_FLOW reason=tts_not_ready");
    loggerDelegate.trace("TRACE_TTS_FLOW source=tts_callback");
    loggerDelegate.gate("M1_TTS_DONE source=tts_callback");
    loggerDelegate.ux("UX_TTS_DONE", "source=script_error");
    if (ttsWatchdogRunnable != null) {
      mainHandler.removeCallbacks(ttsWatchdogRunnable);
      ttsWatchdogRunnable = null;
    }
    currentTtsOnDoneCallback = null;
    currentTtsSource = null;
    onComplete.run();
  }

  private void onTtsTerminal(boolean isError) {
    mainHandler.post(
        () -> {
          resetSpeakerButton();
          if (ttsWatchdogRunnable != null) {
            mainHandler.removeCallbacks(ttsWatchdogRunnable);
            ttsWatchdogRunnable = null;
          }
          String source = currentTtsSource;
          Runnable callback = currentTtsOnDoneCallback;
          currentTtsSource = null;
          currentTtsOnDoneCallback = null;

          if (isError && source != null) {
            if ("script".equals(source)) {
              loggerDelegate.trace("TRACE_TTS_FLOW source=tts_callback");
              loggerDelegate.gate("M1_TTS_DONE source=tts_callback");
              loggerDelegate.ux("UX_TTS_DONE", "source=script_error");
              if (callback != null) {
                callback.run();
              }
            } else if ("message".equals(source)) {
              loggerDelegate.ux("UX_TTS_DONE", "source=message_error");
            }
            return;
          }

          if ("script".equals(source)) {
            loggerDelegate.trace("TRACE_TTS_FLOW source=tts_callback");
            loggerDelegate.gate("M1_TTS_DONE source=tts_callback");
            loggerDelegate.ux("UX_TTS_DONE", "source=script_callback");
          } else if ("message".equals(source)) {
            loggerDelegate.ux("UX_TTS_DONE", "source=message_callback");
          }

          if (callback != null) {
            callback.run();
          }
        });
  }

  private void resetSpeakerButton() {
    if (currentlyPlayingSpeakerBtn != null) {
      currentlyPlayingSpeakerBtn.setImageResource(R.drawable.ic_speaker);
      currentlyPlayingSpeakerBtn.clearColorFilter();
      currentlyPlayingSpeakerBtn = null;
    }
  }

  public void markSpeakerButtonActive(@Nullable ImageView speakerBtn) {
    if (speakerBtn == null) {
      return;
    }
    currentlyPlayingSpeakerBtn = speakerBtn;
    speakerBtn.setImageResource(R.drawable.ic_speaker_active);
    speakerBtn.setColorFilter(0xFF2196F3);
  }

  private void setTtsVoice(@Nullable String gender) {
    if (!isTtsReady || tts == null) {
      return;
    }

    Set<android.speech.tts.Voice> voices = null;
    try {
      voices = tts.getVoices();
    } catch (Exception e) {
      Log.e("DialoguePlaybackCoordinator", "Error getting voices: " + e.getMessage());
    }

    if (voices == null || voices.isEmpty()) {
      return;
    }

    boolean isMaleRequested = "male".equalsIgnoreCase(gender);
    String targetLanguage = ttsLocale.getLanguage();
    String targetCountry = ttsLocale.getCountry();
    android.speech.tts.Voice selectedVoice = null;

    for (android.speech.tts.Voice voice : voices) {
      if (voice.getLocale().getLanguage().equalsIgnoreCase(targetLanguage)) {
        String name = voice.getName().toLowerCase();
        if (isMaleRequested) {
          if (name.contains("male") && !name.contains("female")) {
            selectedVoice = voice;
            break;
          }
        } else if (name.contains("female")) {
          selectedVoice = voice;
          break;
        }
      }
    }

    if (selectedVoice == null) {
      for (android.speech.tts.Voice voice : voices) {
        if (voice.getLocale().getLanguage().equalsIgnoreCase(targetLanguage)) {
          String name = voice.getName().toLowerCase();
          if (isMaleRequested) {
            if (name.contains("iol")
                || name.contains("-im-")
                || name.contains("guy")
                || name.contains("-m-")
                || name.endsWith("-m")
                || name.contains(".male")) {
              selectedVoice = voice;
              break;
            }
          } else if (name.contains("sfg")
              || name.contains("tpf")
              || name.contains("lady")
              || name.contains("-f-")
              || name.endsWith("-f")
              || name.contains(".female")) {
            selectedVoice = voice;
            break;
          }
        }
      }
    }

    if (selectedVoice == null) {
      for (android.speech.tts.Voice voice : voices) {
        if (voice.getLocale().getLanguage().equalsIgnoreCase(targetLanguage)) {
          String name = voice.getName().toLowerCase();
          if (isMaleRequested) {
            if (name.contains("female")
                || name.contains("sfg")
                || name.contains("tpf")
                || name.contains("lady")) {
              continue;
            }
            selectedVoice = voice;
            if (!targetCountry.isEmpty()
                && voice.getLocale().getCountry().equalsIgnoreCase(targetCountry)) {
              break;
            }
          } else {
            if (name.contains("male")
                || name.contains("iol")
                || name.contains("-im-")
                || name.contains("guy")) {
              continue;
            }
            selectedVoice = voice;
            if (!targetCountry.isEmpty()
                && voice.getLocale().getCountry().equalsIgnoreCase(targetCountry)) {
              break;
            }
          }
        }
      }
    }

    if (selectedVoice == null) {
      for (android.speech.tts.Voice voice : voices) {
        if (voice.getLocale().getLanguage().equalsIgnoreCase(targetLanguage)
            && (targetCountry.isEmpty()
                || voice.getLocale().getCountry().equalsIgnoreCase(targetCountry))) {
          selectedVoice = voice;
          break;
        }
      }
    }

    if (selectedVoice != null) {
      tts.setVoice(selectedVoice);
    }
  }

  private boolean shouldApplyGenderVoiceSelection() {
    return "en".equalsIgnoreCase(ttsLocale.getLanguage());
  }

  @NonNull
  private Locale resolveLocale(@Nullable String localeTag) {
    if (localeTag == null) {
      return DEFAULT_TTS_LOCALE;
    }
    String normalized = localeTag.trim().replace('_', '-');
    if (normalized.isEmpty()) {
      return DEFAULT_TTS_LOCALE;
    }
    Locale locale = Locale.forLanguageTag(normalized);
    if (locale.getLanguage() == null || locale.getLanguage().trim().isEmpty()) {
      return DEFAULT_TTS_LOCALE;
    }
    return locale;
  }

  private boolean applyConfiguredTtsLanguage() {
    if (tts == null) {
      return false;
    }
    int result = tts.setLanguage(ttsLocale);
    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
      ttsLocale = DEFAULT_TTS_LOCALE;
      int fallbackResult = tts.setLanguage(DEFAULT_TTS_LOCALE);
      return fallbackResult != TextToSpeech.LANG_MISSING_DATA
          && fallbackResult != TextToSpeech.LANG_NOT_SUPPORTED;
    }
    return true;
  }

  private float clamp(float value, float min, float max) {
    if (value < min) {
      return min;
    }
    return Math.min(value, max);
  }
}
