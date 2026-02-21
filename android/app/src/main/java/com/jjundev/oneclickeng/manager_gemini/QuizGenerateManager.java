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
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.QuizData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import com.jjundev.oneclickeng.tool.IncrementalQuizQuestionParser;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

/** One-shot LLM manager for quiz question generation from summary seed data. */
public class QuizGenerateManager implements IQuizGenerationManager {
  private static final String TAG = "JOB_J-20260217-001";
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  private static final String DEFAULT_MODEL_NAME = "gemini-3-flash-preview";

  private static final String PREF_NAME = "gemini_quiz_cache_prefs";
  private static final String KEY_CACHE_NAME = "gemini_quiz_cache_name";
  private static final String KEY_CACHE_CREATED = "gemini_quiz_cache_created_at";
  private static final String KEY_CACHE_TTL = "gemini_quiz_cache_ttl_seconds";
  private static final int CACHE_TTL_SECONDS = 3600; // 1 hour
  private static final int MIN_REMAINING_TTL_SECONDS = 300; // 5 minutes

  private final OkHttpClient client;
  private final Gson gson;
  private final Handler mainHandler;
  private final String apiKey;
  private final String modelName;
  private final Context context;
  private final SharedPreferences prefs;

  private static final OkHttpClient streamingClient =
      new OkHttpClient.Builder()
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(0, TimeUnit.SECONDS)
          .writeTimeout(30, TimeUnit.SECONDS)
          .build();

  private String cachedContentName;
  private boolean cacheReady = false;

  public QuizGenerateManager(Context context, String apiKey, String modelName) {
    this.context = context;
    this.apiKey = normalizeOrDefault(apiKey, "");
    this.modelName = normalizeOrDefault(modelName, DEFAULT_MODEL_NAME);
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    this.gson = new Gson();
    this.mainHandler = new Handler(Looper.getMainLooper());
    this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
  }

  @Override
  public void generateQuizFromSummaryAsync(
      @NonNull SummaryData summaryData,
      int requestedQuestionCount,
      @NonNull QuizCallback callback) {
    if (callback == null) {
      return;
    }
    if (summaryData == null) {
      postFailure(callback, "Summary data is null");
      return;
    }
    if (apiKey == null || apiKey.trim().isEmpty()) {
      postFailure(callback, "API key is missing");
      return;
    }

    final int maxQuestions = Math.max(1, Math.min(10, requestedQuestionCount));

    QuizData.QuizSeed seed = buildQuizSeed(summaryData);
    if (!hasSeed(seed)) {
      postFailure(callback, "Quiz seed is empty");
      return;
    }

    int expressionCount = seed.getExpressions() == null ? 0 : seed.getExpressions().size();
    int wordCount = seed.getWords() == null ? 0 : seed.getWords().size();
    logDebug("quiz request start: expressionCount=" + expressionCount + ", wordCount=" + wordCount);

    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                if (cacheReady && cachedContentName != null) {
                  requestBody.addProperty("cachedContent", cachedContentName);
                } else {
                  JsonObject sysInstruction = new JsonObject();
                  JsonArray sysParts = new JsonArray();
                  JsonObject sysPart = new JsonObject();
                  sysPart.addProperty("text", getSystemPrompt());
                  sysParts.add(sysPart);
                  sysInstruction.add("parts", sysParts);
                  requestBody.add("systemInstruction", sysInstruction);
                }

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty("text", buildUserPrompt(seed, maxQuestions));
                parts.add(part);
                userContent.add("parts", parts);
                contents.add(userContent);
                requestBody.add("contents", contents);

                JsonObject generationConfig = new JsonObject();
                generationConfig.addProperty("responseMimeType", "application/json");
                requestBody.add("generationConfig", generationConfig);

                String url = BASE_URL + "/models/" + modelName + ":generateContent?key=" + apiKey;
                Request request =
                    new Request.Builder()
                        .url(url)
                        .post(
                            RequestBody.create(
                                gson.toJson(requestBody), MediaType.parse("application/json")))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                  String body = response.body() != null ? response.body().string() : "";
                  if (!response.isSuccessful()) {
                    logDebug("quiz request failed: code=" + response.code());
                    postFailure(callback, "Quiz LLM request failed: " + response.code());
                    return;
                  }

                  String responseText = extractFirstTextPart(body);
                  ParseResult parseResult = parseQuizQuestionsPayload(responseText, maxQuestions);
                  if (parseResult.getQuestions().isEmpty()) {
                    logDebug("quiz parse failed: no valid questions");
                    postFailure(callback, "Failed to parse quiz questions");
                    return;
                  }

                  logDebug(
                      "quiz parse success: questionCount="
                          + parseResult.getQuestions().size()
                          + ", validCount="
                          + parseResult.getValidQuestionCount()
                          + ", capped="
                          + parseResult.isCapped());
                  mainHandler.post(() -> callback.onSuccess(parseResult.getQuestions()));
                }
              } catch (Exception e) {
                logDebug("quiz request exception: " + safeMessage(e));
                postFailure(callback, "Quiz LLM error: " + safeMessage(e));
              }
            })
        .start();
  }

  @Override
  public void generateQuizFromSummaryStreamingAsync(
      @NonNull SummaryData summaryData,
      int requestedQuestionCount,
      @NonNull QuizStreamingCallback callback) {
    if (callback == null) {
      return;
    }
    if (summaryData == null) {
      postFailure(callback, "Summary data is null");
      return;
    }
    if (apiKey == null || apiKey.trim().isEmpty()) {
      postFailure(callback, "API key is missing");
      return;
    }

    final int maxQuestions = Math.max(1, Math.min(10, requestedQuestionCount));

    QuizData.QuizSeed seed = buildQuizSeed(summaryData);
    if (!hasSeed(seed)) {
      postFailure(callback, "Quiz seed is empty");
      return;
    }

    int expressionCount = seed.getExpressions() == null ? 0 : seed.getExpressions().size();
    int wordCount = seed.getWords() == null ? 0 : seed.getWords().size();
    logDebug(
        "quiz stream request start: expressionCount="
            + expressionCount
            + ", wordCount="
            + wordCount);

    new Thread(
            () -> {
              int emittedQuestionCount = 0;
              try {
                JsonObject requestBody = new JsonObject();
                if (cacheReady && cachedContentName != null) {
                  requestBody.addProperty("cachedContent", cachedContentName);
                } else {
                  JsonObject sysInstruction = new JsonObject();
                  JsonArray sysParts = new JsonArray();
                  JsonObject sysPart = new JsonObject();
                  sysPart.addProperty("text", getSystemPrompt());
                  sysParts.add(sysPart);
                  sysInstruction.add("parts", sysParts);
                  requestBody.add("systemInstruction", sysInstruction);
                }

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty("text", buildUserPrompt(seed, maxQuestions));
                parts.add(part);
                userContent.add("parts", parts);
                contents.add(userContent);
                requestBody.add("contents", contents);

                JsonObject generationConfig = new JsonObject();
                generationConfig.addProperty("responseMimeType", "application/json");
                requestBody.add("generationConfig", generationConfig);

                String url =
                    BASE_URL
                        + "/models/"
                        + modelName
                        + ":streamGenerateContent?alt=sse&key="
                        + apiKey;
                Request request =
                    new Request.Builder()
                        .url(url)
                        .post(
                            RequestBody.create(
                                gson.toJson(requestBody), MediaType.parse("application/json")))
                        .build();

                IncrementalQuizQuestionParser parser = new IncrementalQuizQuestionParser();
                try (Response response = streamingClient.newCall(request).execute()) {
                  if (!response.isSuccessful()) {
                    logDebug("quiz stream request failed: code=" + response.code());
                    postFailure(callback, "Quiz LLM request failed: " + response.code());
                    return;
                  }
                  if (response.body() == null) {
                    postFailure(callback, "Quiz LLM empty response body");
                    return;
                  }

                  BufferedSource source = response.body().source();
                  while (!source.exhausted() && emittedQuestionCount < maxQuestions) {
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
                      JsonObject content =
                          candidates.get(0).getAsJsonObject().getAsJsonObject("content");
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

                        List<String> questionObjects =
                            parser.addChunk(responsePartObject.get("text").getAsString());
                        for (String questionObject : questionObjects) {
                          if (emittedQuestionCount >= maxQuestions) {
                            break;
                          }
                          QuizData.QuizQuestion parsedQuestion =
                              parseQuestionObjectForStreaming(questionObject);
                          if (parsedQuestion == null) {
                            continue;
                          }
                          emittedQuestionCount++;
                          QuizData.QuizQuestion callbackQuestion = parsedQuestion;
                          mainHandler.post(() -> callback.onQuestion(callbackQuestion));
                        }
                      }
                    } catch (Exception ignored) {
                      // Ignore malformed chunk and keep streaming.
                    }
                  }
                }

                if (emittedQuestionCount > 0) {
                  postComplete(callback, null);
                  return;
                }
                postFailure(callback, "Failed to parse quiz questions");
              } catch (Exception e) {
                logDebug("quiz stream request exception: " + safeMessage(e));
                if (emittedQuestionCount > 0) {
                  postComplete(callback, "partial stream interrupted");
                } else {
                  postFailure(callback, "Quiz LLM error: " + safeMessage(e));
                }
              }
            })
        .start();
  }

  @NonNull
  public static QuizData.QuizSeed buildQuizSeed(@Nullable SummaryData summaryData) {
    List<QuizData.QuizSeedExpression> expressionSeeds = new ArrayList<>();
    List<QuizData.QuizSeedWord> wordSeeds = new ArrayList<>();
    if (summaryData == null) {
      return new QuizData.QuizSeed(expressionSeeds, wordSeeds);
    }

    Set<String> expressionKeys = new HashSet<>();
    List<SummaryData.ExpressionItem> expressions = summaryData.getExpressions();
    if (expressions != null) {
      for (SummaryData.ExpressionItem expression : expressions) {
        if (expression == null) {
          continue;
        }
        String before = trimToNull(expression.getBefore());
        String after = trimToNull(expression.getAfter());
        if (before == null && after == null) {
          continue;
        }

        String key = normalize(before) + "|" + normalize(after);
        if (!expressionKeys.add(key)) {
          continue;
        }
        String koreanPrompt = trimToNull(expression.getKoreanPrompt());
        String explanation = trimToNull(expression.getExplanation());
        expressionSeeds.add(
            new QuizData.QuizSeedExpression(
                firstNonBlank(before, ""),
                firstNonBlank(after, ""),
                firstNonBlank(koreanPrompt, ""),
                firstNonBlank(explanation, "")));
      }
    }

    Set<String> wordKeys = new HashSet<>();
    List<SummaryData.WordItem> words = summaryData.getWords();
    if (words != null) {
      for (SummaryData.WordItem word : words) {
        if (word == null) {
          continue;
        }
        String english = trimToNull(word.getEnglish());
        String korean = trimToNull(word.getKorean());
        if (english == null || korean == null) {
          continue;
        }
        String key = normalize(english);
        if (!wordKeys.add(key)) {
          continue;
        }
        String exampleEnglish = trimToNull(word.getExampleEnglish());
        String exampleKorean = trimToNull(word.getExampleKorean());
        wordSeeds.add(
            new QuizData.QuizSeedWord(
                english,
                korean,
                firstNonBlank(exampleEnglish, english),
                firstNonBlank(exampleKorean, korean)));
      }
    }

    return new QuizData.QuizSeed(expressionSeeds, wordSeeds);
  }

  @NonNull
  public static ParseResult parseQuizQuestionsPayload(
      @Nullable String rawPayload, int maxQuestions) {
    String cleanJson = stripJsonFence(rawPayload);
    if (cleanJson.isEmpty()) {
      return ParseResult.empty();
    }

    JsonArray items = resolveQuestionArray(cleanJson);
    if (items == null) {
      return ParseResult.empty();
    }

    Set<String> seenQuestions = new HashSet<>();
    List<QuizData.QuizQuestion> validQuestions = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      if (!items.get(i).isJsonObject()) {
        continue;
      }
      JsonObject item = items.get(i).getAsJsonObject();
      String question = trimToNull(readAsString(item, "question"));
      String answer = trimToNull(readAsString(item, "answer"));
      if (question == null || answer == null) {
        continue;
      }

      String dedupeKey = normalize(question);
      if (!seenQuestions.add(dedupeKey)) {
        continue;
      }

      List<String> choices = sanitizeChoices(readAsArray(item, "choices"));
      String explanation = trimToNull(readAsString(item, "explanation"));
      validQuestions.add(new QuizData.QuizQuestion(question, answer, choices, explanation));
    }

    boolean capped = validQuestions.size() > maxQuestions;
    if (!capped) {
      return new ParseResult(validQuestions, false, validQuestions.size());
    }

    return new ParseResult(
        new ArrayList<>(validQuestions.subList(0, maxQuestions)), true, validQuestions.size());
  }

  private void postFailure(@NonNull QuizCallback callback, @NonNull String error) {
    mainHandler.post(() -> callback.onFailure(error));
  }

  private void postFailure(@NonNull QuizStreamingCallback callback, @NonNull String error) {
    mainHandler.post(() -> callback.onFailure(error));
  }

  private void postComplete(
      @NonNull QuizStreamingCallback callback, @Nullable String warningMessage) {
    mainHandler.post(() -> callback.onComplete(warningMessage));
  }

  // Removed addSystemInstruction and buildSystemPrompt

  @NonNull
  private String buildUserPrompt(@NonNull QuizData.QuizSeed seed, int maxQuestions) {
    JsonObject payload = new JsonObject();
    payload.add("expressions", gson.toJsonTree(seed.getExpressions()));
    payload.add("words", gson.toJsonTree(seed.getWords()));
    return "Generate review quiz questions from this summary seed payload.\n"
        + "Requested Question Count: "
        + maxQuestions
        + "\n"
        + "Input data:\n"
        + gson.toJson(payload);
  }

  @Nullable
  private static JsonArray resolveQuestionArray(@NonNull String cleanJson) {
    try {
      JsonElement root = JsonParser.parseString(cleanJson);
      if (root.isJsonArray()) {
        return root.getAsJsonArray();
      }
      if (root.isJsonObject()) {
        JsonObject rootObject = root.getAsJsonObject();
        if (rootObject.has("questions") && rootObject.get("questions").isJsonArray()) {
          return rootObject.getAsJsonArray("questions");
        }
        if (rootObject.has("items") && rootObject.get("items").isJsonArray()) {
          return rootObject.getAsJsonArray("items");
        }
      }
    } catch (Exception ignored) {
      return null;
    }
    return null;
  }

  @Nullable
  private static QuizData.QuizQuestion parseQuestionObjectForStreaming(
      @Nullable String rawQuestionObject) {
    if (rawQuestionObject == null || rawQuestionObject.trim().isEmpty()) {
      return null;
    }
    try {
      JsonObject item = JsonParser.parseString(rawQuestionObject).getAsJsonObject();
      String question = trimToNull(readAsString(item, "question"));
      String answer = trimToNull(readAsString(item, "answer"));
      if (question == null || answer == null) {
        return null;
      }

      List<String> choices = sanitizeChoices(readAsArray(item, "choices"));
      String explanation = trimToNull(readAsString(item, "explanation"));
      return new QuizData.QuizQuestion(question, answer, choices, explanation);
    } catch (Exception ignored) {
      return null;
    }
  }

  @Nullable
  private static List<String> sanitizeChoices(@Nullable JsonArray choicesArray) {
    if (choicesArray == null || choicesArray.size() == 0) {
      return null;
    }
    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < choicesArray.size(); i++) {
      if (choicesArray.get(i) == null || !choicesArray.get(i).isJsonPrimitive()) {
        continue;
      }
      String choice = trimToNull(choicesArray.get(i).getAsString());
      if (choice == null) {
        continue;
      }
      String key = normalize(choice);
      if (!seen.add(key)) {
        continue;
      }
      result.add(choice);
    }
    return result.isEmpty() ? null : result;
  }

  @NonNull
  private static String extractFirstTextPart(@Nullable String body) {
    if (body == null || body.trim().isEmpty()) {
      return "";
    }
    try {
      JsonObject root = JsonParser.parseString(body).getAsJsonObject();
      JsonArray candidates = root.getAsJsonArray("candidates");
      if (candidates == null || candidates.size() == 0) {
        return "";
      }
      JsonObject candidate = candidates.get(0).getAsJsonObject();
      JsonObject content = candidate.getAsJsonObject("content");
      if (content == null) {
        return "";
      }
      JsonArray parts = content.getAsJsonArray("parts");
      if (parts == null || parts.size() == 0) {
        return "";
      }
      JsonObject firstPart = parts.get(0).getAsJsonObject();
      if (firstPart == null || !firstPart.has("text")) {
        return "";
      }
      return firstPart.get("text").getAsString();
    } catch (Exception ignored) {
      return "";
    }
  }

  private static boolean hasSeed(@Nullable QuizData.QuizSeed seed) {
    if (seed == null) {
      return false;
    }
    List<QuizData.QuizSeedExpression> expressions = seed.getExpressions();
    List<QuizData.QuizSeedWord> words = seed.getWords();
    return (expressions != null && !expressions.isEmpty()) || (words != null && !words.isEmpty());
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
  private static JsonArray readAsArray(@Nullable JsonObject obj, @NonNull String key) {
    try {
      if (obj != null && obj.has(key) && obj.get(key).isJsonArray()) {
        return obj.getAsJsonArray(key);
      }
    } catch (Exception ignored) {
      // No-op.
    }
    return null;
  }

  @NonNull
  private static String stripJsonFence(@Nullable String rawPayload) {
    if (rawPayload == null) {
      return "";
    }
    return rawPayload.replace("```json", "").replace("```", "").trim();
  }

  @Nullable
  private static String trimToNull(@Nullable String text) {
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @NonNull
  private static String firstNonBlank(@Nullable String first, @NonNull String fallback) {
    String value = trimToNull(first);
    return value == null ? fallback : value;
  }

  @NonNull
  private static String normalize(@Nullable String text) {
    return text == null ? "" : text.trim().toLowerCase();
  }

  @NonNull
  private static String normalizeOrDefault(@Nullable String value, @NonNull String defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }

  @NonNull
  private static String safeMessage(@Nullable Exception e) {
    if (e == null) {
      return "unknown";
    }
    String message = e.getMessage();
    return message == null ? "unknown" : message;
  }

  private void logDebug(@NonNull String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }

  interface ValidationCallback {
    void onValid(String cacheName, long remainingSeconds);

    void onInvalid();

    void onError(String error);
  }

  @Override
  public void initializeCache(@NonNull InitCallback callback) {
    String savedCacheName = prefs.getString(KEY_CACHE_NAME, null);

    if (savedCacheName != null) {
      long createdAt = prefs.getLong(KEY_CACHE_CREATED, 0);
      int ttl = prefs.getInt(KEY_CACHE_TTL, CACHE_TTL_SECONDS);
      long elapsedSeconds = (System.currentTimeMillis() - createdAt) / 1000;

      if (elapsedSeconds > ttl) {
        logDebug("Local cache expired, creating new one");
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
                logDebug("Reusing existing cache: " + name);
                cachedContentName = name;
                cacheReady = true;
                mainHandler.post(callback::onReady);
              } else {
                logDebug("Cache expiring soon. Creating new cache.");
                createCache(callback);
              }
            }

            @Override
            public void onInvalid() {
              logDebug("Saved cache invalid. Creating new cache.");
              clearLocalCacheData();
              createCache(callback);
            }

            @Override
            public void onError(String error) {
              logDebug("Validation error: " + error + ". Creating new cache.");
              createCache(callback);
            }
          });
    } else {
      logDebug("No local cache found. Creating new cache.");
      createCache(callback);
    }
  }

  private void createCache(@NonNull InitCallback callback) {
    new Thread(
            () -> {
              try {
                String systemPrompt = getSystemPrompt();

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", "models/" + modelName);

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray userParts = new JsonArray();
                JsonObject userPart = new JsonObject();
                userPart.addProperty("text", "Initialize quiz generator.");
                userParts.add(userPart);
                userContent.add("parts", userParts);
                contents.add(userContent);

                JsonObject modelContent = new JsonObject();
                modelContent.addProperty("role", "model");
                JsonArray modelParts = new JsonArray();
                JsonObject modelPart = new JsonObject();
                modelPart.addProperty(
                    "text",
                    "I am ready to generate English learning quizzes based on your requirements.");
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
                requestBody.addProperty("displayName", "QuizGeneratorCache");

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
                    logDebug("Cache creation failed: " + response.code() + " - " + responseBody);
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

                  logDebug("New cache created: " + cachedContentName);
                  mainHandler.post(callback::onReady);
                }
              } catch (Exception e) {
                logDebug("Cache initialization error: " + safeMessage(e));
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
                logDebug("Cache validation error: " + safeMessage(e));
                callback.onError(e.getMessage());
              }
            })
        .start();
  }

  private void clearLocalCacheData() {
    prefs.edit().remove(KEY_CACHE_NAME).remove(KEY_CACHE_CREATED).remove(KEY_CACHE_TTL).apply();
  }

  private String getSystemPrompt() {
    String prompt = readAssetFile("prompts/quiz_generate/system_prompt.md");
    if (prompt.isEmpty()) {
      return buildSystemPrompt_Dummy();
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

  private String buildSystemPrompt_Dummy() {
    return "You are an expert English learning quiz generator specialized in creating effective review quizzes for Korean English learners.\n"
        + "Your sole responsibility is to generate well-structured quiz questions based strictly on the provided input data, and return them as a single valid JSON object.\n"
        + "\n"
        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        + "RESPONSE FORMAT\n"
        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        + "\n"
        + "You MUST respond with ONLY a raw valid JSON object.\n"
        + "- Do NOT include any markdown code fences (e.g. ```json or ```)\n"
        + "- Do NOT include any explanation, commentary, or extra text outside the JSON\n"
        + "- Do NOT include a BOM or any invisible characters\n"
        + "- The response must be directly parseable by a standard JSON parser\n"
        + "\n"
        + "Required top-level structure:\n"
        + "{\n"
        + "\"questions\": [\n"
        + "{\n"
        + "\"question\": \"string (required)\",\n"
        + "\"answer\": \"string (required)\",\n"
        + "\"choices\": [\"string\", \"string\", \"string\", \"string\"],\n"
        + "\"explanation\": \"string (optional)\"\n"
        + "}\n"
        + "]\n"
        + "}\n"
        + "\n"
        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        + "LANGUAGE RULES (CRITICAL — MUST FOLLOW WITHOUT EXCEPTION)\n"
        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        + "\n"
        + "The target audience is Korean English learners. Therefore, strict language rules apply to every field in the output.\n"
        + "\n"
        + "Rule L-1 — Korean as the default language\n"
        + "All text that is NOT an English sentence or English word MUST be written in Korean.\n"
        + "This applies universally across all fields: question, answer, choices, and explanation.\n"
        + "\n"
        + "Rule L-2 — When English is allowed\n"
        + "English is only permitted in the following cases:\n"
        + "- When quoting or presenting an English word or sentence as part of the quiz content (e.g. a fill-in-the-blank sentence, a vocabulary item, or a corrected expression)\n"
        + "- When the answer itself is an English word or sentence\n"
        + "- When choices are English words or sentences being evaluated\n"
        + "\n"
        + "Rule L-3 — Field-level language guidance\n"
        + "\n"
        + "[question field]\n"
        + "- All instructions, context descriptions, and question prompts MUST be written in Korean.\n"
        + "- English sentences or words may appear inside the question only when they are the subject being tested (e.g. quoted in a fill-in-the-blank or error-correction item).\n"
        + "- Example (correct):   \"다음 빈칸에 알맞은 단어를 고르세요. 'She was ___ by the unexpected news.'\"\n"
        + "- Example (incorrect): \"Choose the correct word to fill in the blank.\"\n"
        + "\n"
        + "[answer field]\n"
        + "- If the question asks for an English word or sentence: write the answer in English.\n"
        + "- If the question asks for a Korean meaning or translation: write the answer in Korean.\n"
        + "- Example (correct):   \"surprised\" — when asking for the English word\n"
        + "- Example (correct):   \"놀란, 충격을 받은\" — when asking for the Korean meaning\n"
        + "- Example (incorrect): \"The answer is surprised.\" — do not mix languages unnecessarily\n"
        + "\n"
        + "[choices field]\n"
        + "- If the choices are English words or sentences being evaluated: write them in English.\n"
        + "- If the choices are Korean meanings or translations: write them in Korean.\n"
        + "- Do NOT mix Korean and English within the same set of choices.\n"
        + "\n"
        + "[explanation field]\n"
        + "- MUST be written entirely in Korean.\n"
        + "- English words or sentences may be quoted within the explanation when directly referencing the quiz content.\n"
        + "- Example (correct):   \"'surprised'는 '놀란, 충격을 받은'이라는 뜻의 형용사로, 감정의 수동적 상태를 표현할 때 사용합니다.\"\n"
        + "- Example (incorrect): \"This word means to feel shocked or amazed by something unexpected.\"\n"
        + "\n"
        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        + "INPUT DATA STRUCTURE\n"
        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        + "\n"
        + "The user prompt provides a JSON payload containing two arrays: `expressions` and `words`.\n"
        + "You MUST generate all questions exclusively from this data. Do not invent or assume any content beyond what is provided.\n"
        + "\n"
        + "[ expressions array ]\n"
        + "Each item contains:\n"
        + "- before         : The original English sentence written by the learner (unnatural or incorrect expression)\n"
        + "- after          : The corrected English sentence (natural and accurate expression)\n"
        + "- koreanPrompt   : A Korean description of the situation or meaning behind the expression\n"
        + "- explanation    : A detailed explanation of why the correction was made or how the expression is used\n"
        + "\n"
        + "[ words array ]\n"
        + "Each item contains:\n"
        + "- english        : An English word or short expression\n"
        + "- korean         : The Korean meaning or translation of the word/expression\n"
        + "- exampleEnglish : An English example sentence using the word\n"
        + "- exampleKorean  : The Korean translation of the example sentence\n"
        + "\n"
        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        + "QUESTION GENERATION RULES\n"
        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        + "\n"
        + "Rule Q-1 — Source restriction\n"
        + "Generate questions ONLY from the content within the provided `expressions` and `words` arrays.\n"
        + "Do NOT introduce vocabulary, sentences, or concepts that are not present in the input data.\n"
        + "\n"
        + "Rule Q-2 — Exact question count\n"
        + "Generate EXACTLY {maxQuestions} questions — no more, no fewer.\n"
        + "If the input data is limited, vary the question type for the same content to meet the required count.\n"
        + "\n"
        + "Rule Q-3 — Required fields\n"
        + "`question` and `answer` are REQUIRED for every item and MUST NOT be empty.\n"
        + "Empty strings (\"\") and null values are not acceptable.\n"
        + "\n"
        + "Rule Q-4 — choices (multiple choice options)\n"
        + "`choices` is optional. If included, the following conditions MUST all be met:\n"
        + "- Provide exactly 4 options\n"
        + "- The correct answer (matching the `answer` field) MUST be included among the 4 options\n"
        + "- Shuffle the order so the correct answer is not always in the same position\n"
        + "- Wrong answer options MUST be drawn from other items within the input data (do not fabricate distractors)\n"
        + "\n"
        + "Rule Q-5 — explanation (answer explanation)\n"
        + "`explanation` is optional. If included, the following conditions MUST all be met:\n"
        + "- Write entirely in Korean (English words or sentences may be quoted inline when referencing quiz content)\n"
        + "- Keep it concise: 1 to 2 sentences maximum\n"
        + "- Focus on why the answer is correct and in what context the expression or word is used\n"
        + "\n"
        + "Rule Q-6 — Question type variety\n"
        + "Mix the following question types throughout the quiz. Do NOT repeat the same question type more than 2 times in a row.\n"
        + "\n"
        + "From `expressions`:\n"
        + "- Error correction    : Present the `before` sentence and ask the learner to identify or select the corrected `after` sentence\n"
        + "- Situation-to-English: Present the `koreanPrompt` context and ask the learner to produce or select the matching `after` expression\n"
        + "- Correction rationale: Present both `before` and `after` and ask what was changed and why, based on `explanation`\n"
        + "\n"
        + "From `words`:\n"
        + "- English-to-Korean   : Present the English word and ask the learner to select or write the Korean meaning\n"
        + "- Korean-to-English   : Present the Korean meaning and ask the learner to select or write the English word\n"
        + "- Fill in the blank   : Use `exampleEnglish` with the target word replaced by a blank (___) and ask the learner to fill it in\n"
        + "- Sentence translation: Present `exampleEnglish` and ask for the Korean translation, or vice versa\n"
        + "\n"
        + "Rule Q-7 — Difficulty and clarity\n"
        + "- Use simple and clear language appropriate for Korean English learners at CEFR A2–B1 level.\n"
        + "- Keep question prompts concise. Avoid unnecessarily long or complex phrasing.\n"
        + "- Each question should test only one concept at a time.\n"
        + "\n"
        + "Rule Q-8 — No duplicate questions\n"
        + "Do not generate two questions that test the exact same content in the exact same format.\n"
        + "The same source item may be reused if the question type is different.\n"
        + "\n"
        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        + "OUTPUT VALIDATION CHECKLIST\n"
        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        + "\n"
        + "Before returning your response, verify every item on this checklist:\n"
        + "\n"
        + "[ ] The response is a raw JSON object with no markdown code fences\n"
        + "[ ] The `questions` array contains exactly {maxQuestions} items\n"
        + "[ ] Every `question` and `answer` field is non-empty\n"
        + "[ ] Every `choices` array (when present) contains exactly 4 options and includes the correct answer\n"
        + "[ ] All text except English words and sentences is written in Korean\n"
        + "[ ] All content is derived from the provided input data with no fabricated content\n"
        + "[ ] No two questions are identical in both source content and question type";
  }

  public static final class ParseResult {
    @NonNull private final List<QuizData.QuizQuestion> questions;
    private final boolean capped;
    private final int validQuestionCount;

    public ParseResult(
        @NonNull List<QuizData.QuizQuestion> questions, boolean capped, int validQuestionCount) {
      this.questions = questions;
      this.capped = capped;
      this.validQuestionCount = validQuestionCount;
    }

    @NonNull
    public static ParseResult empty() {
      return new ParseResult(new ArrayList<>(), false, 0);
    }

    @NonNull
    public List<QuizData.QuizQuestion> getQuestions() {
      return questions;
    }

    public boolean isCapped() {
      return capped;
    }

    public int getValidQuestionCount() {
      return validQuestionCount;
    }
  }
}
