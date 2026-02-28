package com.jjundev.oneclickeng.manager_gemini;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IDialogueGenerateManager;
import com.jjundev.oneclickeng.tool.IncrementalDialogueScriptParser;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

public class DialogueGenerateManager implements IDialogueGenerateManager {
  private static final String TAG = "DialogueGenerateManager";
  private static final String DEFAULT_MODEL_NAME = "gemini-3-flash-preview";
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  private static final int CACHE_TTL_SECONDS = 3600; // 1 hour

  private static final String PREF_NAME = "gemini_script_cache_prefs";
  private static final String KEY_CACHE_NAME = "gemini_script_cache_name";
  private static final String KEY_CACHE_CREATED = "gemini_script_cache_created_at";
  private static final String KEY_CACHE_TTL = "gemini_script_cache_ttl_seconds";
  private static final int MIN_REMAINING_TTL_SECONDS = 300; // 5 minutes

  private static final OkHttpClient streamingClient =
      new OkHttpClient.Builder()
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(0, TimeUnit.SECONDS)
          .writeTimeout(30, TimeUnit.SECONDS)
          .build();

  private final Gson gson;
  private final OkHttpClient client;
  private final String apiKey;
  private final String modelName;
  private final Context context;
  private final SharedPreferences prefs;
  private final Handler mainHandler;

  private String cachedContentName;
  private boolean cacheReady = false;

  public interface ScriptGenerationCallback
      extends IDialogueGenerateManager.ScriptGenerationCallback {}

  public interface InitCallback extends IDialogueGenerateManager.InitCallback {}

  interface ValidationCallback {
    void onValid(String cacheName, long remainingSeconds);

    void onInvalid();

    void onError(String error);
  }

  // Data classes for Gson serialization
  private static class ScriptData {
    @SerializedName("topic")
    String topic;

    @SerializedName("opponent_name")
    String opponentName;

    @SerializedName("opponent_role")
    String opponentRole;

    @SerializedName("opponent_gender")
    String opponentGender;

    @SerializedName("script")
    List<ScriptLine> script;

    public ScriptData(String topic, String opponentName, List<ScriptLine> script) {
      this(topic, opponentName, "Partner", (Math.random() > 0.5 ? "male" : "female"), script);
    }

    public ScriptData(
        String topic, String opponentName, String opponentRole, List<ScriptLine> script) {
      this(topic, opponentName, opponentRole, (Math.random() > 0.5 ? "male" : "female"), script);
    }

    public ScriptData(
        String topic,
        String opponentName,
        String opponentRole,
        String opponentGender,
        List<ScriptLine> script) {
      this.topic = topic;
      this.opponentName = opponentName;
      this.opponentRole = opponentRole;
      this.opponentGender = opponentGender;
      this.script = script;
    }
  }

  private static class ScriptLine {
    @SerializedName("ko")
    String ko;

    @SerializedName("en")
    String en;

    @SerializedName("role")
    String role;

    public ScriptLine(String ko, String en, String role) {
      this.ko = ko;
      this.en = en;
      this.role = role;
    }
  }

  public DialogueGenerateManager(Context context, String apiKey, String modelName) {
    this.context = context;
    this.apiKey = normalizeOrDefault(apiKey, "");
    this.modelName = normalizeOrDefault(modelName, DEFAULT_MODEL_NAME);
    this.gson = new Gson();
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    this.mainHandler = new Handler(Looper.getMainLooper());
  }

  @Override
  public void initializeCache(IDialogueGenerateManager.InitCallback callback) {
    String savedCacheName = prefs.getString(KEY_CACHE_NAME, null);

    if (savedCacheName != null) {
      long createdAt = prefs.getLong(KEY_CACHE_CREATED, 0);
      int ttl = prefs.getInt(KEY_CACHE_TTL, CACHE_TTL_SECONDS);
      long elapsedSeconds = (System.currentTimeMillis() - createdAt) / 1000;

      if (elapsedSeconds > ttl) {
        Log.d(TAG, "Local cache expired, creating new one");
        clearLocalCacheData();
        createCache(callback);
        return;
      }

      validateCacheFromServer(
          savedCacheName,
          new ValidationCallback() {
            @Override
            public void onValid(String name, long remainingSeconds) {
              if (remainingSeconds > MIN_REMAINING_TTL_SECONDS) {
                Log.i(TAG, "Reusing existing cache: " + name);
                cachedContentName = name;
                cacheReady = true;
                mainHandler.post(callback::onReady);
              } else {
                Log.i(TAG, "Cache expiring soon. Creating new cache.");
                createCache(callback);
              }
            }

            @Override
            public void onInvalid() {
              Log.i(TAG, "Saved cache invalid. Creating new cache.");
              clearLocalCacheData();
              createCache(callback);
            }

            @Override
            public void onError(String error) {
              Log.w(TAG, "Validation error: " + error + ". Creating new cache.");
              createCache(callback);
            }
          });

    } else {
      Log.i(TAG, "No local cache found. Creating new cache.");
      createCache(callback);
    }
  }

  private void createCache(IDialogueGenerateManager.InitCallback callback) {
    new Thread(
            () -> {
              try {
                String systemPrompt = getSystemPrompt();

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", "models/" + modelName);

                JsonArray contents = new JsonArray();

                // Add system instruction as part of the cache request structure
                // Note: For createCachedContent, systemInstruction is a top-level field, NOT
                // inside contents
                // But we also usually provide some example or context in 'contents' if needed.
                // Here we just cache the system instruction mainly.
                // However, the API requires 'contents' to be present and non-empty usually?
                // Let's check GeminiCachedManager. It puts a dummy user/model turn in
                // 'contents' to satisfy requirements or provide few-shot.
                // The user request didn't strictly say to use few-shot, but caching just a
                // system prompt is valid.
                // Let's stick to just system instruction if possible, or add a simple "I'm
                // ready" turn if strictly needed for context.
                // GeminiCachedFeedbackManager used a dummy user message with context.
                // I will add a simple acknowledgment interaction to be safe and ensure the
                // model is "primed".

                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray userParts = new JsonArray();
                JsonObject userPart = new JsonObject();
                userPart.addProperty("text", "Initialize conversation script generator.");
                userParts.add(userPart);
                userContent.add("parts", userParts);
                contents.add(userContent);

                JsonObject modelContent = new JsonObject();
                modelContent.addProperty("role", "model");
                JsonArray modelParts = new JsonArray();
                JsonObject modelPart = new JsonObject();
                modelPart.addProperty(
                    "text",
                    "I am ready to generate English conversation scripts based on your requirements.");
                modelParts.add(modelPart);
                modelContent.add("parts", modelParts);
                contents.add(modelContent);

                requestBody.add("contents", contents);

                JsonObject sysInstruction = new JsonObject();
                JsonArray sysParts = new JsonArray();
                JsonObject sysPart = new JsonObject();
                sysPart.addProperty("text", systemPrompt);
                sysParts.add(sysPart);
                sysInstruction.add("parts", sysParts);
                requestBody.add("systemInstruction", sysInstruction);

                requestBody.addProperty("ttl", CACHE_TTL_SECONDS + "s");
                requestBody.addProperty("displayName", "ScriptGeneratorCache");

                String url = BASE_URL + "/cachedContents?key=" + apiKey;
                String jsonBody = gson.toJson(requestBody);

                Request request =
                    new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                  String responseBody = response.body() != null ? response.body().string() : "";

                  if (!response.isSuccessful()) {
                    Log.e(TAG, "Cache creation failed: " + response.code() + " - " + responseBody);
                    mainHandler.post(
                        () -> callback.onError("Cache creation failed: " + responseBody));
                    return;
                  }

                  JsonObject result = JsonParser.parseString(responseBody).getAsJsonObject();
                  cachedContentName = result.get("name").getAsString();
                  cacheReady = true;

                  prefs
                      .edit()
                      .putString(KEY_CACHE_NAME, cachedContentName)
                      .putLong(KEY_CACHE_CREATED, System.currentTimeMillis())
                      .putInt(KEY_CACHE_TTL, CACHE_TTL_SECONDS)
                      .apply();

                  Log.i(TAG, "New cache created: " + cachedContentName);
                  mainHandler.post(callback::onReady);
                }

              } catch (Exception e) {
                Log.e(TAG, "Cache initialization error", e);
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
              }
            })
        .start();
  }

  private void validateCacheFromServer(String cacheName, ValidationCallback callback) {
    new Thread(
            () -> {
              try {
                String url = BASE_URL + "/" + cacheName + "?key=" + apiKey;

                Request request = new Request.Builder().url(url).get().build();

                try (Response response = client.newCall(request).execute()) {
                  if (response.code() == 404) {
                    callback.onInvalid();
                    return;
                  }

                  if (!response.isSuccessful()) {
                    callback.onError("Server check failed: " + response.code());
                    return;
                  }

                  String responseBody = response.body() != null ? response.body().string() : "";
                  JsonObject result = JsonParser.parseString(responseBody).getAsJsonObject();

                  if (result.has("expireTime")) {
                    String expireTimeStr = result.get("expireTime").getAsString();
                    Instant expireTime = Instant.parse(expireTimeStr);
                    long remainingSeconds =
                        expireTime.getEpochSecond() - Instant.now().getEpochSecond();
                    callback.onValid(cacheName, remainingSeconds);
                  } else {
                    callback.onError("No expireTime in response");
                  }
                }
              } catch (Exception e) {
                Log.e(TAG, "Cache validation error", e);
                callback.onError(e.getMessage());
              }
            })
        .start();
  }

  private void clearLocalCacheData() {
    prefs.edit().remove(KEY_CACHE_NAME).remove(KEY_CACHE_CREATED).remove(KEY_CACHE_TTL).apply();
  }

  @Override
  public void generateScript(
      String level,
      String topic,
      String format,
      int length,
      IDialogueGenerateManager.ScriptGenerationCallback callback) {
    if (cacheReady && cachedContentName != null) {
      generateScriptWithCache(level, topic, "dialogue", length, callback);
    } else {
      Log.w(TAG, "Cache not ready, fallback to non-cached generation");
      generateScriptWithoutCache(level, topic, "dialogue", length, callback);
    }
  }

  @Override
  public void generateScriptStreamingAsync(
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length,
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback) {
    logStream(
        "request start: level="
            + level
            + ", topic="
            + trimForLog(topic)
            + ", format="
            + format
            + ", requestedLength="
            + Math.max(1, length)
            + ", cacheReady="
            + cacheReady);
    if (cacheReady && cachedContentName != null) {
      generateScriptStreamingWithCache(level, topic, format, length, callback);
    } else {
      Log.w(TAG, "Cache not ready, fallback to non-cached streaming generation");
      generateScriptStreamingWithoutCache(level, topic, format, length, callback);
    }
  }

  private void generateScriptStreamingWithCache(
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length,
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback) {
    logStream(
        "request path: cached, cacheName="
            + trimForLog(cachedContentName)
            + ", length="
            + Math.max(1, length));
    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("cachedContent", cachedContentName);
                appendScriptStreamingPayload(requestBody, level, topic, format, length);
                streamAndParseRequest(requestBody, true, length, callback);
              } catch (Exception e) {
                Log.e(TAG, "Cached streaming generation error", e);
                postStreamingFailure(callback, "Streaming error: " + safeMessage(e));
              }
            })
        .start();
  }

  private void generateScriptStreamingWithoutCache(
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length,
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback) {
    logStream("request path: non-cached, length=" + Math.max(1, length));
    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                JsonObject sysInstruction = new JsonObject();
                JsonArray sysParts = new JsonArray();
                JsonObject sysPart = new JsonObject();
                sysPart.addProperty("text", getSystemPrompt());
                sysParts.add(sysPart);
                sysInstruction.add("parts", sysParts);
                requestBody.add("systemInstruction", sysInstruction);

                appendScriptStreamingPayload(requestBody, level, topic, format, length);
                streamAndParseRequest(requestBody, false, length, callback);
              } catch (Exception e) {
                Log.e(TAG, "Non-cached streaming generation error", e);
                postStreamingFailure(callback, "Streaming error: " + safeMessage(e));
              }
            })
        .start();
  }

  private void generateScriptWithCache(
      String level,
      String topic,
      String format,
      int length,
      IDialogueGenerateManager.ScriptGenerationCallback callback) {
    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("cachedContent", cachedContentName);

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();

                String userPrompt =
                    String.format(
                        "Generate a script with these parameters:\n- level: %s\n- topic: %s\n- format: %s\n- length: %d",
                        level, topic, format, length);

                part.addProperty("text", userPrompt);
                parts.add(part);
                userContent.add("parts", parts);
                contents.add(userContent);
                requestBody.add("contents", contents);

                JsonObject generationConfig = new JsonObject();
                generationConfig.addProperty("responseMimeType", "application/json");
                requestBody.add("generationConfig", generationConfig);

                sendAndParseRequest(requestBody, true, callback);

              } catch (Exception e) {
                Log.e(TAG, "Cached generation error", e);
                mainHandler.post(() -> callback.onError(e));
              }
            })
        .start();
  }

  private void generateScriptWithoutCache(
      String level,
      String topic,
      String format,
      int length,
      IDialogueGenerateManager.ScriptGenerationCallback callback) {
    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();

                // Add system instruction directly
                JsonObject sysInstruction = new JsonObject();
                JsonArray sysParts = new JsonArray();
                JsonObject sysPart = new JsonObject();
                sysPart.addProperty("text", getSystemPrompt());
                sysParts.add(sysPart);
                sysInstruction.add("parts", sysParts);
                requestBody.add("systemInstruction", sysInstruction);

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();

                String userPrompt =
                    String.format(
                        "Generate a script with these parameters:\n- level: %s\n- topic: %s\n- format: %s\n- length: %d",
                        level, topic, format, length);

                part.addProperty("text", userPrompt);
                parts.add(part);
                userContent.add("parts", parts);
                contents.add(userContent);
                requestBody.add("contents", contents);

                JsonObject generationConfig = new JsonObject();
                generationConfig.addProperty("responseMimeType", "application/json");
                requestBody.add("generationConfig", generationConfig);

                sendAndParseRequest(requestBody, false, callback);

              } catch (Exception e) {
                Log.e(TAG, "Non-cached generation error", e);
                mainHandler.post(() -> callback.onError(e));
              }
            })
        .start();
  }

  private void appendScriptStreamingPayload(
      @NonNull JsonObject requestBody,
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length) {
    JsonArray contents = new JsonArray();
    JsonObject userContent = new JsonObject();
    userContent.addProperty("role", "user");
    JsonArray parts = new JsonArray();
    JsonObject part = new JsonObject();
    String userPrompt =
        String.format(
            "Generate a script with these parameters:\n- level: %s\n- topic: %s\n- format: %s\n- length: %d",
            level, topic, format, length);
    part.addProperty("text", userPrompt);
    parts.add(part);
    userContent.add("parts", parts);
    contents.add(userContent);
    requestBody.add("contents", contents);

    JsonObject generationConfig = new JsonObject();
    generationConfig.addProperty("responseMimeType", "application/json");
    requestBody.add("generationConfig", generationConfig);
  }

  private void streamAndParseRequest(
      @NonNull JsonObject requestBody,
      boolean usedCache,
      int requestedLength,
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback) {
    int safeRequestedLength = Math.max(1, requestedLength);
    long streamStartedAt = System.currentTimeMillis();
    int emittedTurns = 0;
    logStream("sse open: usedCache=" + usedCache + ", requestedLength=" + safeRequestedLength);
    try {
      Request request =
          new Request.Builder()
              .url(
                  BASE_URL
                      + "/models/"
                      + modelName
                      + ":streamGenerateContent?alt=sse&key="
                      + apiKey)
              .post(
                  RequestBody.create(gson.toJson(requestBody), MediaType.parse("application/json")))
              .build();

      IncrementalDialogueScriptParser parser = new IncrementalDialogueScriptParser();
      try (Response response = streamingClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          String body = response.body() != null ? response.body().string() : "";
          Log.e(TAG, "Streaming request failed: " + response.code() + " - " + body);
          logStream("sse failed: code=" + response.code() + ", bodyLength=" + body.length());
          if (usedCache && (response.code() == 400 || response.code() == 404)) {
            cacheReady = false;
            cachedContentName = null;
          }
          postStreamingFailure(callback, "Request failed: " + response.code());
          return;
        }
        logStream("sse connected: code=" + response.code());
        if (response.body() == null) {
          logStream("sse failed: empty body");
          postStreamingFailure(callback, "Streaming response body is empty");
          return;
        }

        BufferedSource source = response.body().source();
        while (!source.exhausted() && emittedTurns < safeRequestedLength) {
          String line = source.readUtf8Line();
          if (line == null || !line.startsWith("data: ")) {
            continue;
          }
          String data = line.substring(6).trim();
          if (data.isEmpty() || "[DONE]".equals(data)) {
            continue;
          }

          try {
            JsonObject root = JsonParser.parseString(data).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) {
              continue;
            }
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            if (content == null) {
              continue;
            }
            JsonArray responseParts = content.getAsJsonArray("parts");
            if (responseParts == null || responseParts.size() == 0) {
              continue;
            }

            for (JsonElement responsePart : responseParts) {
              if (responsePart == null || !responsePart.isJsonObject()) {
                continue;
              }
              JsonObject responsePartObject = responsePart.getAsJsonObject();
              if (!responsePartObject.has("text")) {
                continue;
              }

              IncrementalDialogueScriptParser.ParseUpdate update =
                  parser.addChunk(responsePartObject.get("text").getAsString());
              IncrementalDialogueScriptParser.Metadata metadata = update.getMetadata();
              if (metadata != null) {
                logStream(
                    "metadata received: topic="
                        + trimForLog(metadata.getTopic())
                        + ", opponent="
                        + trimForLog(metadata.getOpponentName())
                        + ", gender="
                        + trimForLog(metadata.getOpponentGender()));
                postStreamingMetadata(
                    callback,
                    metadata.getTopic(),
                    metadata.getOpponentName(),
                    metadata.getOpponentGender());
              }

              for (String turnObject : update.getCompletedTurnObjects()) {
                if (emittedTurns >= safeRequestedLength) {
                  break;
                }
                IDialogueGenerateManager.ScriptTurnChunk turn =
                    parseTurnObjectForStreaming(turnObject, emittedTurns);
                if (turn == null) {
                  continue;
                }
                emittedTurns++;
                logStream(
                    "turn received: "
                        + emittedTurns
                        + "/"
                        + safeRequestedLength
                        + ", role="
                        + trimForLog(turn.getRole())
                        + ", ko="
                        + previewText(turn.getKorean())
                        + ", en="
                        + previewText(turn.getEnglish()));
                postStreamingTurn(callback, turn);
              }
            }
          } catch (Exception ignored) {
            // Ignore malformed chunk and continue streaming.
          }
        }
      }

      if (emittedTurns <= 0) {
        logStream("stream finished without valid turns");
        postStreamingFailure(callback, "Failed to parse script turns");
        return;
      }
      logStream(
          "stream complete: emittedTurns="
              + emittedTurns
              + ", elapsedMs="
              + (System.currentTimeMillis() - streamStartedAt));
      postStreamingComplete(callback, null);
    } catch (Exception e) {
      logStream(
          "stream exception: emittedTurns="
              + emittedTurns
              + ", message="
              + trimForLog(safeMessage(e)));
      postStreamingFailure(callback, "Streaming error: " + safeMessage(e));
    }
  }

  @Nullable
  private IDialogueGenerateManager.ScriptTurnChunk parseTurnObjectForStreaming(
      @Nullable String turnObject, int turnIndex) {
    if (turnObject == null || turnObject.trim().isEmpty()) {
      return null;
    }
    try {
      JsonObject item = JsonParser.parseString(turnObject).getAsJsonObject();
      String korean = trimToNull(readAsString(item, "ko"));
      String english = trimToNull(readAsString(item, "en"));
      if (korean == null || english == null) {
        return null;
      }
      String role = trimToNull(readAsString(item, "role"));
      if (role == null) {
        role = turnIndex % 2 == 0 ? "model" : "user";
      }
      return new IDialogueGenerateManager.ScriptTurnChunk(korean, english, role);
    } catch (Exception ignored) {
      return null;
    }
  }

  private void postStreamingMetadata(
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback,
      @NonNull String topic,
      @NonNull String opponentName,
      @NonNull String opponentGender) {
    mainHandler.post(() -> callback.onMetadata(topic, opponentName, opponentGender));
  }

  private void postStreamingTurn(
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback,
      @NonNull IDialogueGenerateManager.ScriptTurnChunk turn) {
    mainHandler.post(() -> callback.onTurn(turn));
  }

  private void postStreamingComplete(
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback,
      @Nullable String warningMessage) {
    mainHandler.post(() -> callback.onComplete(warningMessage));
  }

  private void postStreamingFailure(
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback, @NonNull String error) {
    mainHandler.post(() -> callback.onFailure(error));
  }

  private void sendAndParseRequest(
      JsonObject requestBody,
      boolean usedCache,
      IDialogueGenerateManager.ScriptGenerationCallback callback) {
    try {
      Log.d(TAG, "Sending script generation request. Used cache: " + usedCache);
      String jsonBody = gson.toJson(requestBody);
      Request request =
          new Request.Builder()
              .url(
                  requestBody.has("cachedContent")
                      ? BASE_URL + "/models/" + modelName + ":generateContent?key=" + apiKey
                      : BASE_URL + "/models/" + modelName + ":generateContent?key=" + apiKey)
              .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
              .build();

      try (Response response = client.newCall(request).execute()) {
        String responseBody = response.body() != null ? response.body().string() : "";

        if (!response.isSuccessful()) {
          Log.e(TAG, "Request failed: " + response.code() + " - " + responseBody);
          if (usedCache && (response.code() == 400 || response.code() == 404)) {
            cacheReady = false;
            cachedContentName = null;
            // Optionally retry without cache here, but for now just error out or
            // let user retry
          }
          throw new Exception("Request failed: " + response.code());
        }

        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates != null && candidates.size() > 0) {
          JsonObject candidate = candidates.get(0).getAsJsonObject();
          JsonObject content = candidate.getAsJsonObject("content");
          JsonArray parts = content.getAsJsonArray("parts");
          String text = parts.get(0).getAsJsonObject().get("text").getAsString();
          mainHandler.post(() -> callback.onSuccess(text));
        } else {
          throw new Exception("No candidates found");
        }
      }
    } catch (Exception e) {
      mainHandler.post(() -> callback.onError(e));
    }
  }

  private String getSystemPrompt() {
    String prompt = readAssetFile("prompts/dialogue_generate/system_prompt.md");
    if (prompt.isEmpty()) {
      return getSystemPrompt_Dummy();
    }
    return prompt;
  }

  private String readAssetFile(String fileName) {
    try (java.io.InputStream is = context.getAssets().open(fileName);
        java.io.BufferedReader reader =
            new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      return sb.toString().trim();
    } catch (IOException e) {
      Log.e(TAG, "Failed to read asset file: " + fileName, e);
      return "";
    }
  }

  private String getSystemPrompt_Dummy() {
    return "You are an English conversation script generator for Korean language learners.\n"
        + "\n"
        + "Your task is to generate a realistic, natural English conversation script based on the user's input.\n"
        + "\n"
        + "---\n"
        + "\n"
        + "## Input Parameters\n"
        + "\n"
        + "The user will provide the following:\n"
        + "- **level**: one of [beginner, elementary, intermediate, upper-intermediate, advanced]\n"
        + "- **topic**: the conversation topic (e.g., \"ordering coffee\", \"job interview\", \"airport check-in\")\n"
        + "- **format**: fixed as [dialogue]\n"
        + "- **length**: number of script lines to generate (e.g., 10, 20, 30)\n"
        + "\n"
        + "---\n"
        + "\n"
        + "## Level Definitions\n"
        + "\n"
        + "### beginner (CEFR A1)\n"
        + "- Vocabulary: ~500 words, basic survival vocabulary only\n"
        + "- Grammar: present simple, \"can\", basic imperatives\n"
        + "- Sentence length: 3-6 words average\n"
        + "- Restrictions: NO idioms, NO phrasal verbs, NO complex connectors\n"
        + "- Style: very short turns, simple questions and answers\n"
        + "\n"
        + "### elementary (CEFR A2)\n"
        + "- Vocabulary: ~1,000 words, everyday vocabulary\n"
        + "- Grammar: past simple, future with \"will/going to\", basic modals (can, should)\n"
        + "- Sentence length: 6-9 words average\n"
        + "- Restrictions: NO idioms, basic phrasal verbs only (e.g., look for, pick up)\n"
        + "- Style: short turns, some follow-up questions\n"
        + "\n"
        + "### intermediate (CEFR B1)\n"
        + "- Vocabulary: ~2,500 words, broader everyday and some abstract vocabulary\n"
        + "- Grammar: present perfect, basic conditionals (if + present), comparatives/superlatives, passive voice (simple)\n"
        + "- Sentence length: 9-13 words average\n"
        + "- Allowed: common idioms (e.g., \"break the ice\"), common phrasal verbs\n"
        + "- Style: natural turn-taking, opinions and reasons\n"
        + "\n"
        + "### upper-intermediate (CEFR B2)\n"
        + "- Vocabulary: ~5,000 words, includes semi-formal and topic-specific vocabulary\n"
        + "- Grammar: all conditionals, reported speech, relative clauses, passive voice (all forms), wish/would rather\n"
        + "- Sentence length: 13-18 words average\n"
        + "- Allowed: natural idioms, phrasal verbs, discourse markers (however, on the other hand)\n"
        + "- Style: extended turns, persuasion, nuance, hedging language\n"
        + "\n"
        + "### advanced (CEFR C1)\n"
        + "- Vocabulary: unrestricted, includes academic, professional, and nuanced vocabulary\n"
        + "- Grammar: unrestricted, including inversion, cleft sentences, subjunctive, mixed conditionals\n"
        + "- Sentence length: 15-25 words average\n"
        + "- Allowed: all idiomatic expressions, colloquialisms, cultural references\n"
        + "- Style: complex argumentation, humor, implicit meaning, natural speech patterns\n"
        + "\n"
        + "---\n"
        + "\n"
        + "## Output Rules\n"
        + "\n"
        + "1. You MUST respond ONLY with a valid JSON object. No markdown, no explanation, no preamble.\n"
        + "2. The JSON must strictly follow this schema:\n"
        + "\n"
        + "{\n"
        + "  \"topic\": \"A short description of the conversation topic in Korean (max 15 characters, including spaces and punctuation)\",\n"
        + "  \"opponent_name\": \"The conversation partner's name or title (e.g., John, The Manager, Driver)\",\n"
        + "  \"opponent_gender\": \"The conversation partner's gender (male or female)\",\n"
        + "  \"opponent_role\": \"The conversation partner's role in English (e.g., Barista, Interviewer, Immigration Officer)\",\n"
        + "  \"script\": [\n"
        + "    {\n"
        + "      \"ko\": \"Korean translation of the line\",\n"
        + "      \"en\": \"English original line\",\n"
        + "      \"role\": \"model\" (for Opponent) OR \"user\"\n"
        + "    }\n"
        + "  ]\n"
        + "}\n"
        + "\n"
        + "3. Write the English line (\"en\") FIRST as the original, then provide a natural Korean translation (\"ko\"). The Korean should feel natural, not word-for-word literal.\n"
        + "4. **role**: Use \"model\" for the Opponent (AI/Check-in Agent/Interviewer) and \"user\" for the learner.\n"
        + "5. Format is strictly **dialogue**: alternate between the user and the opponent naturally.\n"
        + "6. The conversation should feel realistic and culturally appropriate for the given topic.\n"
        + "7. The \"topic\" value MUST be written in Korean and MUST be 15 characters or fewer (including spaces and punctuation). If it exceeds 15 characters, rewrite it to a shorter Korean title while preserving the core meaning.\n"
        + "8. Strictly adhere to the vocabulary, grammar, and sentence length constraints of the specified level.\n"
        + "9. Do NOT include any text outside the JSON object.\n"
        + "\n"
        + "---\n"
        + "\n"
        + "## CRITICAL: Length and Conversation Structure Rules\n"
        + "\n"
        + "The **length** parameter defines the EXACT number of lines in the \"script\" array. This is a hard constraint. Follow these rules strictly:\n"
        + "\n"
        + "### Rule 1: Exact Line Count\n"
        + "- The \"script\" array MUST contain EXACTLY the number of items specified by **length**.\n"
        + "- Not one more. Not one less. Count carefully before outputting.\n"
        + "\n"
        + "### Rule 2: Plan the Conversation Arc BEFORE Writing\n"
        + "Before generating the script, mentally plan the conversation in three phases:\n"
        + "\n"
        + "| Phase | Line Range | Purpose |\n"
        + "|-------|-----------|---------|\n"
        + "| **Opening** | Lines 1 ~ 20% | Greetings, establishing context, opening the topic |\n"
        + "| **Body** | Lines 20% ~ 75% | Main content, questions, exchanges, key information |\n"
        + "| **Closing** | Lines 75% ~ 100% | Wrapping up, confirming, saying goodbye, final farewell |\n"
        + "\n"
        + "For example, if length = 10:\n"
        + "- Lines 1-2: Opening (greeting, starting the conversation)\n"
        + "- Lines 3-7: Body (main topic exchange)\n"
        + "- Lines 8-10: Closing (wrapping up, farewell)\n"
        + "\n"
        + "If length = 20:\n"
        + "- Lines 1-4: Opening\n"
        + "- Lines 5-15: Body\n"
        + "- Lines 16-20: Closing\n"
        + "\n"
        + "### Rule 3: The Conversation MUST Reach a Natural Conclusion at the LAST Line\n"
        + "- The very last line (line number = length) MUST be a clear, natural ending of the conversation.\n"
        + "- Appropriate final lines include: a farewell (\"Goodbye!\", \"See you later!\", \"Have a great day!\"), a final confirmation (\"Thanks, I appreciate it!\"), or a closing remark that signals the conversation is over.\n"
        + "- The conversation must NOT feel cut off, unfinished, or like it could continue.\n"
        + "- The conversation must NOT end prematurely before reaching the specified length. Do not insert farewells or closing lines too early.\n"
        + "\n"
        + "### Rule 4: Pacing — Avoid Rushing or Dragging\n"
        + "- Do NOT cram all the important content into the first few lines and then pad the rest with filler.\n"
        + "- Do NOT drag out the opening with excessive small talk if the length is short.\n"
        + "- Distribute the content evenly. The conversation should flow naturally across the full length.\n"
        + "- If the length is long (e.g., 30+), introduce sub-topics, follow-up questions, or minor complications to keep the conversation engaging throughout.\n"
        + "- If the length is short (e.g., 6-8), get to the point quickly but still include a proper greeting and farewell.\n"
        + "\n"
        + "### Rule 5: Self-Validation\n"
        + "After generating the script, verify:\n"
        + "- [ ] The \"script\" array has EXACTLY **length** items\n"
        + "- [ ] The last line is a natural conversation ending\n"
        + "- [ ] The conversation does not end abruptly or feel incomplete\n"
        + "- [ ] No farewell or closing appears before the final 25% of lines\n"
        + "- [ ] The \"opponent_gender\" field is \"male\" or \"female\" based on the opponent's name/role.\n"
        + "\n"
        + "### Rule 7: Diversity and Realism Rule\n"
        + "- Do NOT default to one gender.\n"
        + "- Vary the opponent's gender based on the context, role, and name. \n"
        + "- For example, a Barista could be male or female, a Manager could be male or female. \n"
        + "- Ensure a healthy mix of male and female characters across different requests.\n"
        + "\n"
        + "### Rule 6: First Speaker\n"
        + "- The FIRST line of the script (index 0) MUST be spoken by the **Opponent** (the person talking to the user).\n"
        + "- For example, if the topic is \"Ordering Coffee\", the first line should be the Barista saying \"Hello, what can I get for you?\".\n"
        + "- Ensure the roles alternate naturally from there: Opponent -> User -> Opponent -> User...";
  }

  @Nullable
  private static String readAsString(@Nullable JsonObject obj, @NonNull String key) {
    try {
      if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
        return obj.get(key).getAsString();
      }
    } catch (Exception ignored) {
      // No-op.
    }
    return null;
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
  private static String safeMessage(@Nullable Exception e) {
    if (e == null) {
      return "unknown";
    }
    String message = e.getMessage();
    return message == null ? "unknown" : message;
  }

  private void logStream(@NonNull String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, "[DL_STREAM] " + message);
    }
  }

  @NonNull
  private static String previewText(@Nullable String text) {
    String value = trimToNull(text);
    if (value == null) {
      return "-";
    }
    if (value.length() <= 24) {
      return value;
    }
    return value.substring(0, 24) + "...";
  }

  @NonNull
  private static String trimForLog(@Nullable String value) {
    String trimmed = trimToNull(value);
    return trimmed == null ? "-" : trimmed;
  }

  private static String normalizeOrDefault(String value, String defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }
}
