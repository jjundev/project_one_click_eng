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
import com.jjundev.oneclickeng.game.nativeornot.manager.INativeOrNotGenerationManager;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotDifficulty;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotQuestion;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotTag;
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

public final class NativeOrNotGenerateManager implements INativeOrNotGenerationManager {
  private static final String TAG = "JOB_J-20260221-001";
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  private static final String DEFAULT_MODEL_NAME = "gemini-3-flash-preview";
  private static final int MAX_ATTEMPTS = 2;

  @NonNull private final Context appContext;
  @NonNull private final String apiKey;
  @NonNull private final String modelName;
  @NonNull private final OkHttpClient client;
  @NonNull private final Gson gson;
  @NonNull private final Handler mainHandler;

  public NativeOrNotGenerateManager(
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
      @NonNull NativeOrNotDifficulty difficulty,
      @NonNull Set<String> excludedSignatures,
      @NonNull QuestionCallback callback) {
    requestQuestionWithRetry(
        buildRegularQuestionPrompt(difficulty, excludedSignatures),
        difficulty,
        excludedSignatures,
        callback);
  }

  @Override
  public void generateRelatedQuestionAsync(
      @NonNull NativeOrNotQuestion baseQuestion,
      @NonNull Set<String> excludedSignatures,
      @NonNull QuestionCallback callback) {
    requestQuestionWithRetry(
        buildRelatedQuestionPrompt(baseQuestion, excludedSignatures),
        baseQuestion.getDifficulty(),
        excludedSignatures,
        callback);
  }

  private void requestQuestionWithRetry(
      @NonNull String userPrompt,
      @NonNull NativeOrNotDifficulty difficulty,
      @NonNull Set<String> excludedSignatures,
      @NonNull QuestionCallback callback) {
    if (isBlank(apiKey)) {
      mainHandler.post(() -> callback.onFailure("API key is missing"));
      return;
    }

    Set<String> safeExcluded = new HashSet<>(excludedSignatures);
    new Thread(
            () -> {
              String lastError = "";
              for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                  String raw = generateRawJson(userPrompt);
                  NativeOrNotQuestion question = parseQuestionPayload(raw, difficulty);
                  if (question == null || !question.isValidForV1()) {
                    lastError = "invalid_question_payload";
                    logDebug("question parse invalid. attempt=" + attempt);
                    continue;
                  }
                  if (safeExcluded.contains(question.signature())) {
                    lastError = "duplicated_question_payload";
                    logDebug("question duplicated. attempt=" + attempt);
                    continue;
                  }
                  NativeOrNotQuestion successQuestion = question;
                  mainHandler.post(() -> callback.onSuccess(successQuestion));
                  return;
                } catch (Exception e) {
                  lastError = safeMessage(e);
                  logDebug("question request error. attempt=" + attempt + ", error=" + lastError);
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

  @NonNull
  private String generateRawJson(@NonNull String userPrompt) throws IOException {
    JsonObject requestBody = new JsonObject();

    JsonObject systemInstruction = new JsonObject();
    JsonArray systemParts = new JsonArray();
    JsonObject systemPart = new JsonObject();
    systemPart.addProperty("text", getSystemPrompt());
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

  @Nullable
  public static NativeOrNotQuestion parseQuestionPayload(
      @Nullable String rawPayload, @NonNull NativeOrNotDifficulty fallbackDifficulty) {
    String clean = stripJsonFence(rawPayload);
    if (clean.isEmpty()) {
      return null;
    }

    JsonObject root;
    try {
      JsonElement parsed = JsonParser.parseString(clean);
      if (!parsed.isJsonObject()) {
        return null;
      }
      root = parsed.getAsJsonObject();
    } catch (Exception ignored) {
      return null;
    }

    String situation = readString(root, "situation");
    List<String> options = readStringArray(root, "options");
    int correctIndex = readInt(root, "correctIndex", -1);
    int awkwardOptionIndex = readInt(root, "awkwardOptionIndex", -1);
    List<String> reasonChoices = readStringArray(root, "reasonChoices");
    int reasonAnswerIndex = readInt(root, "reasonAnswerIndex", -1);
    String explanation = readString(root, "explanation");
    String learningPoint = readString(root, "learningPoint");
    String hint = readString(root, "hint");
    String tagRaw = readString(root, "tag");
    String difficultyRaw = readString(root, "difficulty");

    NativeOrNotDifficulty difficulty =
        isBlank(difficultyRaw)
            ? fallbackDifficulty
            : resolveDifficultyOrFallback(difficultyRaw, fallbackDifficulty);

    NativeOrNotQuestion question =
        new NativeOrNotQuestion(
            situation,
            options,
            correctIndex,
            awkwardOptionIndex,
            reasonChoices,
            reasonAnswerIndex,
            explanation,
            learningPoint,
            NativeOrNotTag.fromRaw(tagRaw),
            hint,
            difficulty);

    return question.isValidForV1() ? question : null;
  }

  @NonNull
  private static NativeOrNotDifficulty resolveDifficultyOrFallback(
      @NonNull String raw, @NonNull NativeOrNotDifficulty fallback) {
    String normalized = raw.trim().toUpperCase(Locale.US);
    for (NativeOrNotDifficulty value : NativeOrNotDifficulty.values()) {
      if (value.name().equals(normalized)) {
        return value;
      }
    }
    return fallback;
  }

  @NonNull
  private String buildRegularQuestionPrompt(
      @NonNull NativeOrNotDifficulty difficulty, @NonNull Set<String> excludedSignatures) {
    StringBuilder builder = new StringBuilder();
    builder.append("Generate ONE Native or Not quiz question.");
    builder.append("\nDifficulty: ").append(difficulty.name());
    builder.append("\nRules:");
    builder.append("\n- Exactly 3 options, exactly one correct native-like sentence.");
    builder.append("\n- Include one representative awkward option index.");
    builder.append("\n- Include reasonChoices with exactly 4 Korean choices and one correct reason index.");
    builder.append("\n- Explanation and learningPoint must be Korean-first.");
    builder.append("\n- Tag must be one of: COLLOCATION, SPOKEN, LITERAL_TRANSLATION, REGISTER, REGIONAL_VARIANT, TENSE_SENSE.");
    appendExcludedSignatures(builder, excludedSignatures);
    return builder.toString();
  }

  @NonNull
  private String buildRelatedQuestionPrompt(
      @NonNull NativeOrNotQuestion baseQuestion, @NonNull Set<String> excludedSignatures) {
    StringBuilder builder = new StringBuilder();
    builder.append("Generate ONE related follow-up Native or Not question.");
    builder.append("\nKeep a similar pattern but produce different situation and sentences.");
    builder.append("\nBase situation: ").append(baseQuestion.getSituation());
    builder.append("\nBase options: ").append(baseQuestion.getOptions());
    builder.append("\nBase tag: ").append(baseQuestion.getTag().name());
    builder.append("\nDifficulty: ").append(baseQuestion.getDifficulty().name());
    builder.append("\nRules:");
    builder.append("\n- Exactly 3 options, exactly one correct option.");
    builder.append("\n- Provide reasonChoices 4 items and reasonAnswerIndex.");
    builder.append("\n- Keep Korean explanation and learningPoint concise.");
    appendExcludedSignatures(builder, excludedSignatures);
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
  private String getSystemPrompt() {
    String prompt = readAssetFile("prompts/native_or_not/system_prompt.md");
    if (!prompt.isEmpty()) {
      return prompt;
    }
    return "You are an expert English naturalness quiz generator. Return raw JSON only.";
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
        String value = element.getAsString();
        if (value == null) {
          continue;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        String normalized = trimmed.toLowerCase(Locale.US);
        if (!dedupe.add(normalized)) {
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
