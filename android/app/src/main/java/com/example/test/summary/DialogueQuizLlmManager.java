package com.example.test.summary;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.test.BuildConfig;
import com.example.test.fragment.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.example.test.fragment.dialoguelearning.model.QuizData;
import com.example.test.fragment.dialoguelearning.model.SummaryData;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

/** One-shot LLM manager for quiz question generation from summary seed data. */
public class DialogueQuizLlmManager implements IQuizGenerationManager {
  private static final String TAG = "JOB_J-20260217-001";
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  private static final String DEFAULT_MODEL_NAME = "gemini-3-flash-preview";
  private static final int MAX_QUESTIONS = 5;

  private final OkHttpClient client;
  private final Gson gson;
  private final Handler mainHandler;
  private final String apiKey;
  private final String modelName;

  public DialogueQuizLlmManager(String apiKey, String modelName) {
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
  }

  @Override
  public void generateQuizFromSummaryAsync(
      @NonNull SummaryData summaryData, @NonNull QuizCallback callback) {
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
                addSystemInstruction(requestBody);

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty("text", buildUserPrompt(seed));
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
                  ParseResult parseResult = parseQuizQuestionsPayload(responseText);
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

  @NonNull
  static QuizData.QuizSeed buildQuizSeed(@Nullable SummaryData summaryData) {
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
  static ParseResult parseQuizQuestionsPayload(@Nullable String rawPayload) {
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

    boolean capped = validQuestions.size() > MAX_QUESTIONS;
    if (!capped) {
      return new ParseResult(validQuestions, false, validQuestions.size());
    }

    return new ParseResult(
        new ArrayList<>(validQuestions.subList(0, MAX_QUESTIONS)), true, validQuestions.size());
  }

  private void postFailure(@NonNull QuizCallback callback, @NonNull String error) {
    mainHandler.post(() -> callback.onFailure(error));
  }

  private void addSystemInstruction(@NonNull JsonObject root) {
    JsonObject systemInstruction = new JsonObject();
    JsonArray systemParts = new JsonArray();
    JsonObject systemPart = new JsonObject();
    systemPart.addProperty("text", buildSystemPrompt());
    systemParts.add(systemPart);
    systemInstruction.add("parts", systemParts);
    root.add("systemInstruction", systemInstruction);
  }

  @NonNull
  private String buildSystemPrompt() {
    return "You are an English learning quiz generator.\n"
        + "Return JSON only with this top-level shape:\n"
        + "{\n"
        + "  \"questions\": [\n"
        + "    {\"question\":\"...\",\"answer\":\"...\",\"choices\":[\"...\"],\"explanation\":\"...\"}\n"
        + "  ]\n"
        + "}\n"
        + "Rules:\n"
        + "1) Build quiz items only from provided summary seed expressions/words.\n"
        + "2) questions size must be <= "
        + MAX_QUESTIONS
        + ".\n"
        + "3) question and answer are required and non-empty.\n"
        + "4) choices and explanation are optional.\n"
        + "5) Keep wording concise for Korean English learners.\n"
        + "6) Do not include markdown code fences.";
  }

  @NonNull
  private String buildUserPrompt(@NonNull QuizData.QuizSeed seed) {
    JsonObject payload = new JsonObject();
    payload.add("expressions", gson.toJsonTree(seed.getExpressions()));
    payload.add("words", gson.toJsonTree(seed.getWords()));
    return "Generate review quiz questions from this summary seed payload.\n"
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

  static final class ParseResult {
    @NonNull private final List<QuizData.QuizQuestion> questions;
    private final boolean capped;
    private final int validQuestionCount;

    ParseResult(
        @NonNull List<QuizData.QuizQuestion> questions, boolean capped, int validQuestionCount) {
      this.questions = questions;
      this.capped = capped;
      this.validQuestionCount = validQuestionCount;
    }

    @NonNull
    static ParseResult empty() {
      return new ParseResult(new ArrayList<>(), false, 0);
    }

    @NonNull
    List<QuizData.QuizQuestion> getQuestions() {
      return questions;
    }

    boolean isCapped() {
      return capped;
    }

    int getValidQuestionCount() {
      return validQuestionCount;
    }
  }
}
