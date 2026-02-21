package com.jjundev.oneclickeng.manager_gemini;

import android.content.Context;
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
import com.jjundev.oneclickeng.game.minefield.manager.IMinefieldGenerationManager;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldDifficulty;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldEvaluation;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldQuestion;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class MinefieldGenerateManager implements IMinefieldGenerationManager {
  private static final String TAG = "JOB_J-20260221-002";
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  private static final String DEFAULT_MODEL_NAME = "gemini-3-flash-preview";
  private static final int MAX_ATTEMPTS = 2;

  @NonNull private final Context appContext;
  @NonNull private final String apiKey;
  @NonNull private final String modelName;
  @NonNull private final OkHttpClient client;
  @NonNull private final Gson gson;
  @NonNull private final Handler mainHandler;

  public MinefieldGenerateManager(
      @NonNull Context context, @Nullable String apiKey, @Nullable String modelName) {
    this.appContext = context.getApplicationContext();
    this.apiKey = normalizeOrEmpty(apiKey);
    this.modelName = normalizeOrDefault(modelName, DEFAULT_MODEL_NAME);
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    this.gson = new Gson();
    this.mainHandler = new Handler(Looper.getMainLooper());
  }

  @Override
  public void initializeCache(@NonNull InitCallback callback) {
    if (isBlank(apiKey)) {
      mainHandler.post(() -> callback.onError("API key is missing"));
      return;
    }
    mainHandler.post(callback::onReady);
  }

  @Override
  public void generateQuestionAsync(
      @NonNull MinefieldDifficulty difficulty,
      @NonNull Set<String> excludedSignatures,
      @NonNull QuestionCallback callback) {
    if (isBlank(apiKey)) {
      mainHandler.post(() -> callback.onFailure("API key is missing"));
      return;
    }

    String userPrompt = buildQuestionPrompt(difficulty, excludedSignatures);
    new Thread(
            () -> {
              String lastError = "";
              Set<String> safeExcluded = new HashSet<>(excludedSignatures);
              for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                  String rawJson = generateRawJson(getGenerationSystemPrompt(), userPrompt);
                  MinefieldQuestion question = parseQuestionPayload(rawJson, difficulty);
                  if (question == null || !question.isValidForV1()) {
                    lastError = "invalid_question_payload";
                    logDebug("invalid question payload. attempt=" + attempt);
                    continue;
                  }
                  if (safeExcluded.contains(question.signature())) {
                    lastError = "duplicated_question_payload";
                    logDebug("duplicated question payload. attempt=" + attempt);
                    continue;
                  }
                  MinefieldQuestion success = question;
                  mainHandler.post(() -> callback.onSuccess(success));
                  return;
                } catch (Exception e) {
                  lastError = safeMessage(e);
                  logDebug("question generation error. attempt=" + attempt + ", error=" + lastError);
                }
              }
              String finalError =
                  lastError.isEmpty()
                      ? "문항을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요."
                      : lastError;
              mainHandler.post(() -> callback.onFailure(finalError));
            })
        .start();
  }

  @Override
  public void evaluateAnswerAsync(
      @NonNull MinefieldQuestion question,
      @NonNull String userSentence,
      @NonNull EvaluationCallback callback) {
    if (isBlank(apiKey)) {
      mainHandler.post(() -> callback.onFailure("API key is missing"));
      return;
    }

    String prompt = buildEvaluationPrompt(question, userSentence);
    new Thread(
            () -> {
              String lastError = "";
              for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                  String rawJson = generateRawJson(getEvaluationSystemPrompt(), prompt);
                  MinefieldEvaluation evaluation = parseEvaluationPayload(rawJson);
                  if (evaluation == null || !evaluation.isValidForV1()) {
                    lastError = "invalid_evaluation_payload";
                    logDebug("invalid evaluation payload. attempt=" + attempt);
                    continue;
                  }
                  MinefieldEvaluation success = evaluation;
                  mainHandler.post(() -> callback.onSuccess(success));
                  return;
                } catch (Exception e) {
                  lastError = safeMessage(e);
                  logDebug("evaluation error. attempt=" + attempt + ", error=" + lastError);
                }
              }
              String finalError =
                  lastError.isEmpty()
                      ? "채점에 실패했습니다. 잠시 후 다시 시도해 주세요."
                      : lastError;
              mainHandler.post(() -> callback.onFailure(finalError));
            })
        .start();
  }

  @NonNull
  private String generateRawJson(@NonNull String systemPrompt, @NonNull String userPrompt)
      throws IOException {
    JsonObject requestBody = new JsonObject();

    JsonObject systemInstruction = new JsonObject();
    JsonArray systemParts = new JsonArray();
    JsonObject systemPart = new JsonObject();
    systemPart.addProperty("text", systemPrompt);
    systemParts.add(systemPart);
    systemInstruction.add("parts", systemParts);
    requestBody.add("systemInstruction", systemInstruction);

    JsonArray contents = new JsonArray();
    JsonObject userContent = new JsonObject();
    userContent.addProperty("role", "user");
    JsonArray userParts = new JsonArray();
    JsonObject userPart = new JsonObject();
    userPart.addProperty("text", userPrompt);
    userParts.add(userPart);
    userContent.add("parts", userParts);
    contents.add(userContent);
    requestBody.add("contents", contents);

    JsonObject generationConfig = new JsonObject();
    generationConfig.addProperty("responseMimeType", "application/json");
    requestBody.add("generationConfig", generationConfig);

    String url = BASE_URL + "/models/" + modelName + ":generateContent?key=" + apiKey;
    Request request =
        new Request.Builder()
            .url(url)
            .post(RequestBody.create(gson.toJson(requestBody), MediaType.parse("application/json")))
            .build();

    try (Response response = client.newCall(request).execute()) {
      String body = response.body() != null ? response.body().string() : "";
      if (!response.isSuccessful()) {
        throw new IOException("HTTP " + response.code());
      }
      String text = extractFirstTextPart(body);
      if (isBlank(text)) {
        throw new IOException("empty_response_text");
      }
      return text;
    }
  }

  @NonNull
  private String buildQuestionPrompt(
      @NonNull MinefieldDifficulty difficulty, @NonNull Set<String> excludedSignatures) {
    StringBuilder builder = new StringBuilder();
    builder.append("Generate ONE Minefield question.");
    builder.append("\nDifficulty: ").append(difficulty.name());
    builder.append("\nRequired mine words count: ").append(difficulty.requiredMineWordCount());
    builder.append("\nRules:");
    builder.append("\n- words must contain 6 to 8 items.");
    builder.append("\n- requiredWordIndices must contain distinct valid indices.");
    builder.append("\n- Ensure at least 3 possible valid learner sentence variants.");
    appendExcludedSignatures(builder, excludedSignatures);
    return builder.toString();
  }

  @NonNull
  private String buildEvaluationPrompt(
      @NonNull MinefieldQuestion question, @NonNull String userSentence) {
    StringBuilder builder = new StringBuilder();
    builder.append("Evaluate learner sentence for Minefield game.");
    builder.append("\nDifficulty: ").append(question.getDifficulty().name());
    builder.append("\nSituation: ").append(question.getSituation());
    builder.append("\nQuestion: ").append(question.getQuestion());
    builder.append("\nWords: ").append(question.getWords());
    builder.append("\nRequiredWordIndices: ").append(question.getRequiredWordIndices());
    builder.append("\nLearner sentence: ").append(userSentence);
    builder.append("\nReturn scores and Korean comments.");
    builder.append("\nExamples must be English sentences.");
    return builder.toString();
  }

  private void appendExcludedSignatures(
      @NonNull StringBuilder builder, @NonNull Set<String> excludedSignatures) {
    if (excludedSignatures.isEmpty()) {
      return;
    }
    builder.append("\nDo not generate duplicates of these signatures:");
    int count = 0;
    for (String signature : excludedSignatures) {
      if (count >= 20) {
        break;
      }
      builder.append("\n- ").append(signature);
      count++;
    }
  }

  @NonNull
  private String getGenerationSystemPrompt() {
    String prompt = readAssetFile("prompts/minefield/generation_system_prompt.md");
    if (!prompt.isEmpty()) {
      return prompt;
    }
    return "You generate one Minefield question in strict JSON.";
  }

  @NonNull
  private String getEvaluationSystemPrompt() {
    String prompt = readAssetFile("prompts/minefield/evaluation_system_prompt.md");
    if (!prompt.isEmpty()) {
      return prompt;
    }
    return "You evaluate one learner sentence and return strict JSON.";
  }

  @NonNull
  private String readAssetFile(@NonNull String fileName) {
    try (InputStream inputStream = appContext.getAssets().open(fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      StringBuilder builder = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        builder.append(line).append('\n');
      }
      return builder.toString().trim();
    } catch (Exception e) {
      logDebug("Failed to read asset prompt: " + fileName + ", error=" + safeMessage(e));
      return "";
    }
  }

  @Nullable
  public static MinefieldQuestion parseQuestionPayload(
      @Nullable String rawPayload, @NonNull MinefieldDifficulty fallbackDifficulty) {
    String clean = stripJsonFence(rawPayload);
    if (clean.isEmpty()) {
      return null;
    }
    JsonObject root;
    try {
      JsonElement element = JsonParser.parseString(clean);
      if (!element.isJsonObject()) {
        return null;
      }
      root = element.getAsJsonObject();
    } catch (Exception ignored) {
      return null;
    }

    String situation = readString(root, "situation");
    String question = readString(root, "question");
    List<String> words = readStringArray(root, "words");
    List<Integer> requiredWordIndices = readIntArray(root, "requiredWordIndices");
    String difficultyRaw = readString(root, "difficulty");
    MinefieldDifficulty difficulty =
        MinefieldDifficulty.fromRaw(difficultyRaw, fallbackDifficulty);

    MinefieldQuestion parsed =
        new MinefieldQuestion(situation, question, words, requiredWordIndices, difficulty);
    return parsed.isValidForV1() ? parsed : null;
  }

  @Nullable
  public static MinefieldEvaluation parseEvaluationPayload(@Nullable String rawPayload) {
    String clean = stripJsonFence(rawPayload);
    if (clean.isEmpty()) {
      return null;
    }
    JsonObject root;
    try {
      JsonElement element = JsonParser.parseString(clean);
      if (!element.isJsonObject()) {
        return null;
      }
      root = element.getAsJsonObject();
    } catch (Exception ignored) {
      return null;
    }

    int grammarScore = readInt(root, "grammarScore", -1);
    int naturalnessScore = readInt(root, "naturalnessScore", -1);
    int wordUsageScore = readInt(root, "wordUsageScore", -1);
    int usedWordCount = readInt(root, "usedWordCount", -1);
    int totalWordCount = readInt(root, "totalWordCount", -1);
    List<String> usedWords = readStringArray(root, "usedWords");
    List<String> unusedWords = readStringArray(root, "unusedWords");
    List<String> missingRequiredWords = readStringArray(root, "missingRequiredWords");
    boolean advancedTransformUsed = readBoolean(root, "advancedTransformUsed", false);
    String strengthsComment = readString(root, "strengthsComment");
    String improvementComment = readString(root, "improvementComment");
    String improvedSentence = readString(root, "improvedSentence");
    String exampleBasic = readString(root, "exampleBasic");
    String exampleIntermediate = readString(root, "exampleIntermediate");
    String exampleAdvanced = readString(root, "exampleAdvanced");

    MinefieldEvaluation parsed =
        new MinefieldEvaluation(
            grammarScore,
            naturalnessScore,
            wordUsageScore,
            usedWordCount,
            totalWordCount,
            usedWords,
            unusedWords,
            missingRequiredWords,
            advancedTransformUsed,
            strengthsComment,
            improvementComment,
            improvedSentence,
            exampleBasic,
            exampleIntermediate,
            exampleAdvanced);
    return parsed.isValidForV1() ? parsed : null;
  }

  @NonNull
  private static String extractFirstTextPart(@Nullable String responseBody) {
    if (responseBody == null || responseBody.trim().isEmpty()) {
      return "";
    }
    try {
      JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
      JsonArray candidates = root.getAsJsonArray("candidates");
      if (candidates == null || candidates.size() == 0) {
        return "";
      }
      JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
      JsonObject content = firstCandidate.getAsJsonObject("content");
      if (content == null) {
        return "";
      }
      JsonArray parts = content.getAsJsonArray("parts");
      if (parts == null || parts.size() == 0) {
        return "";
      }
      JsonObject firstPart = parts.get(0).getAsJsonObject();
      return readString(firstPart, "text");
    } catch (Exception ignored) {
      return "";
    }
  }

  @NonNull
  private static String stripJsonFence(@Nullable String raw) {
    if (raw == null) {
      return "";
    }
    String trimmed = raw.trim();
    if (trimmed.startsWith("```")) {
      int firstLineEnd = trimmed.indexOf('\n');
      if (firstLineEnd >= 0) {
        trimmed = trimmed.substring(firstLineEnd + 1);
      }
      if (trimmed.endsWith("```")) {
        trimmed = trimmed.substring(0, trimmed.length() - 3);
      }
    }
    return trimmed.trim();
  }

  @NonNull
  private static List<String> readStringArray(@NonNull JsonObject object, @NonNull String key) {
    if (!object.has(key)) {
      return new ArrayList<>();
    }
    try {
      JsonArray array = object.getAsJsonArray(key);
      if (array == null || array.size() == 0) {
        return new ArrayList<>();
      }
      List<String> result = new ArrayList<>();
      Set<String> dedupe = new HashSet<>();
      for (JsonElement element : array) {
        if (element == null) {
          continue;
        }
        String raw = element.getAsString();
        if (raw == null) {
          continue;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        String keyNormalized = trimmed.toLowerCase(Locale.US);
        if (!dedupe.add(keyNormalized)) {
          continue;
        }
        result.add(trimmed);
      }
      return result;
    } catch (Exception ignored) {
      return new ArrayList<>();
    }
  }

  @NonNull
  private static List<Integer> readIntArray(@NonNull JsonObject object, @NonNull String key) {
    if (!object.has(key)) {
      return new ArrayList<>();
    }
    try {
      JsonArray array = object.getAsJsonArray(key);
      if (array == null || array.size() == 0) {
        return new ArrayList<>();
      }
      List<Integer> result = new ArrayList<>();
      Set<Integer> dedupe = new HashSet<>();
      for (JsonElement element : array) {
        if (element == null) {
          continue;
        }
        int value = element.getAsInt();
        if (!dedupe.add(value)) {
          continue;
        }
        result.add(value);
      }
      return result;
    } catch (Exception ignored) {
      return new ArrayList<>();
    }
  }

  private static int readInt(@NonNull JsonObject object, @NonNull String key, int fallback) {
    if (!object.has(key)) {
      return fallback;
    }
    try {
      return object.get(key).getAsInt();
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static boolean readBoolean(
      @NonNull JsonObject object, @NonNull String key, boolean fallback) {
    if (!object.has(key)) {
      return fallback;
    }
    try {
      return object.get(key).getAsBoolean();
    } catch (Exception ignored) {
      return fallback;
    }
  }

  @NonNull
  private static String readString(@NonNull JsonObject object, @NonNull String key) {
    if (!object.has(key)) {
      return "";
    }
    try {
      String value = object.get(key).getAsString();
      if (value == null) {
        return "";
      }
      String trimmed = value.trim();
      return trimmed.isEmpty() ? "" : trimmed;
    } catch (Exception ignored) {
      return "";
    }
  }

  @NonNull
  private static String normalizeOrDefault(@Nullable String value, @NonNull String fallback) {
    String normalized = normalizeOrEmpty(value);
    return normalized.isEmpty() ? fallback : normalized;
  }

  @NonNull
  private static String normalizeOrEmpty(@Nullable String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? "" : trimmed;
  }

  private static boolean isBlank(@Nullable String value) {
    return value == null || value.trim().isEmpty();
  }

  @NonNull
  private static String safeMessage(@Nullable Throwable throwable) {
    if (throwable == null || throwable.getMessage() == null) {
      return "unknown_error";
    }
    String message = throwable.getMessage().trim();
    return message.isEmpty() ? "unknown_error" : message;
  }

  private void logDebug(@NonNull String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }
}
