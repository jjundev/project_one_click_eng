package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISessionSummaryLlmManager;
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

/** One-shot LLM manager for summary section refinement. */
public class SessionSummaryManager implements ISessionSummaryLlmManager {
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  private static final String DEFAULT_MODEL_NAME = "gemini-3-flash-preview";
  private static final int MAX_WORDS = 12;

  private final OkHttpClient client;
  private final OkHttpClient streamingClient;
  private final Gson gson;
  private final Handler mainHandler;
  private final String apiKey;
  private final String modelName;

  public SessionSummaryManager(String apiKey, String modelName) {
    this.apiKey = normalizeOrDefault(apiKey, "");
    this.modelName = normalizeOrDefault(modelName, DEFAULT_MODEL_NAME);
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    this.streamingClient =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    this.gson = new Gson();
    this.mainHandler = new Handler(Looper.getMainLooper());
  }

  @Override
  public void filterExpressionsAsync(
      @NonNull SummaryFeatureBundle bundle,
      @NonNull ISessionSummaryLlmManager.ExpressionFilterCallback callback) {
    if (bundle == null) {
      mainHandler.post(() -> callback.onFailure("Feature bundle is null"));
      return;
    }
    if (apiKey == null || apiKey.trim().isEmpty()) {
      mainHandler.post(() -> callback.onFailure("API key is missing"));
      return;
    }

    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                addExpressionFilterSystemInstruction(requestBody);

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty("text", buildExpressionFilterUserPrompt(bundle));
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

                try (Response response = streamingClient.newCall(request).execute()) {
                  if (!response.isSuccessful()) {
                    mainHandler.post(
                        () ->
                            callback.onFailure(
                                "Expression filter request failed: " + response.code()));
                    return;
                  }

                  StringBuilder accumulated = new StringBuilder();
                  int emittedCount = 0;

                  BufferedSource source = response.body().source();
                  while (!source.exhausted()) {
                    String line = source.readUtf8Line();
                    if (line == null || !line.startsWith("data: ")) {
                      continue;
                    }
                    String json = line.substring(6).trim();
                    if (json.equals("[DONE]")) {
                      break;
                    }

                    String chunkText = extractTextFromSseChunk(json);
                    if (chunkText.isEmpty()) {
                      continue;
                    }
                    accumulated.append(chunkText);

                    // Try to parse newly completed expression objects
                    List<ISessionSummaryLlmManager.FilteredExpression> parsed =
                        tryParseExpressions(accumulated.toString());
                    for (int i = emittedCount; i < parsed.size(); i++) {
                      ISessionSummaryLlmManager.FilteredExpression expr = parsed.get(i);
                      mainHandler.post(() -> callback.onExpressionReceived(expr));
                    }
                    emittedCount = parsed.size();
                  }

                  // Final parse of the complete accumulated text
                  List<ISessionSummaryLlmManager.FilteredExpression> finalParsed =
                      tryParseExpressions(accumulated.toString());
                  for (int i = emittedCount; i < finalParsed.size(); i++) {
                    ISessionSummaryLlmManager.FilteredExpression expr = finalParsed.get(i);
                    mainHandler.post(() -> callback.onExpressionReceived(expr));
                  }

                  mainHandler.post(callback::onComplete);
                }
              } catch (Exception e) {
                mainHandler.post(
                    () -> callback.onFailure("Expression filter error: " + e.getMessage()));
              }
            })
        .start();
  }

  @Override
  public void extractWordsFromSentencesAsync(
      @NonNull List<String> words,
      @NonNull List<String> sentences,
      @NonNull List<String> userOriginalSentences,
      @NonNull ISessionSummaryLlmManager.WordExtractionCallback callback) {
    if (callback == null) {
      return;
    }
    if (words == null || words.isEmpty() || sentences == null || sentences.isEmpty()) {
      mainHandler.post(() -> callback.onFailure("Word extraction input is empty"));
      return;
    }
    if (apiKey == null || apiKey.trim().isEmpty()) {
      mainHandler.post(() -> callback.onFailure("API key is missing"));
      return;
    }

    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                JsonObject systemInstruction = new JsonObject();
                JsonArray systemParts = new JsonArray();
                JsonObject systemPart = new JsonObject();
                systemPart.addProperty("text", buildWordExtractionSystemPrompt());
                systemParts.add(systemPart);
                systemInstruction.add("parts", systemParts);
                requestBody.add("systemInstruction", systemInstruction);

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty(
                    "text", buildWordExtractionUserPrompt(words, sentences, userOriginalSentences));
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
                    mainHandler.post(
                        () -> callback.onFailure("Summary LLM request failed: " + response.code()));
                    return;
                  }

                  String responseText = extractFirstTextPart(body);
                  List<ISessionSummaryLlmManager.ExtractedWord> extractedWords =
                      parseExtractedWordsPayload(responseText);
                  mainHandler.post(() -> callback.onSuccess(extractedWords));
                }
              } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure("Summary LLM error: " + e.getMessage()));
              }
            })
        .start();
  }

  private void addExpressionFilterSystemInstruction(JsonObject requestBody) {
    String systemPrompt = buildExpressionFilterSystemPrompt();
    JsonObject systemInstruction = new JsonObject();
    JsonArray systemParts = new JsonArray();
    JsonObject systemPart = new JsonObject();
    systemPart.addProperty("text", systemPrompt);
    systemParts.add(systemPart);
    systemInstruction.add("parts", systemParts);
    requestBody.add("systemInstruction", systemInstruction);
  }

  private String buildExpressionFilterSystemPrompt() {
    return "You are an English learning expression filter for Korean learners.\n"
        + "Return JSON only with this exact top-level shape:\n"
        + "{\n"
        + "  \"expressions\": [{\"type\":\"...\",\"koreanPrompt\":\"...\",\"before\":\"...\",\"after\":\"...\",\"explanation\":\"...\"}]\n"
        + "}\n"
        + "Rules:\n"
        + "1) Select ONLY expressions that are genuinely useful for the learner.\n"
        + "2) Remove trivial or redundant corrections (e.g. minor capitalisation, article-only fixes).\n"
        + "3) Prioritise expressions that teach new grammar patterns, natural phrasing, or idiomatic usage.\n"
        + "4) Re-order selected expressions from most educational to least.\n"
        + "5) expressions.before must exactly reuse the user's original English sentence.\n"
        + "6) In expressions.after, wrap the key improved phrase with [[...]] (one or more allowed).\n"
        + "7) Keep Korean fields natural and concise.\n"
        + "8) Do not include markdown code fences.\n"
        + "9) Do not invent facts outside input.";
  }

  private String buildExpressionFilterUserPrompt(SummaryFeatureBundle bundle) {
    JsonObject payload = new JsonObject();
    payload.addProperty("totalScore", bundle.getTotalScore());
    payload.add("expressionCandidates", gson.toJsonTree(bundle.getExpressionCandidates()));

    return "Filter and reorder the expression candidates. Keep only those genuinely useful for learning.\n"
        + "Input data:\n"
        + gson.toJson(payload);
  }

  private String buildWordExtractionSystemPrompt() {
    return "You are an English vocabulary extractor for Korean learners.\n"
        + "Your goal: identify words the learner likely encountered for the FIRST TIME.\n"
        + "Focus on:\n"
        + "- Words in corrected/natural sentences that do NOT appear in the user's original writing\n"
        + "- Words with nuanced meanings a Korean speaker might confuse\n"
        + "- Semi-formal or academic vocabulary that expands the learner's range\n"
        + "Exclude:\n"
        + "- Basic/common words (go, make, think, want, get, etc.)\n"
        + "- Words the user already used correctly in their original sentences\n"
        + "Return JSON only with this exact top-level shape:\n"
        + "{\n"
        + "  \"items\": [\n"
        + "    {\"en\":\"...\",\"ko\":\"...\",\"example\":{\"en\":\"...\",\"ko\":\"...\"}}\n"
        + "  ]\n"
        + "}\n"
        + "Rules:\n"
        + "1) Use only words supported by provided words/sentences context.\n"
        + "2) en and ko are required and non-empty.\n"
        + "3) example.en and example.ko are required and non-empty.\n"
        + "4) example.en should be concise English sentence and example.ko should be Korean translation.\n"
        + "5) Do not include markdown code fences.";
  }

  private String buildWordExtractionUserPrompt(
      List<String> words, List<String> sentences, List<String> userOriginalSentences) {
    JsonObject payload = new JsonObject();
    payload.add("words", gson.toJsonTree(words));
    payload.add("sentences", gson.toJsonTree(sentences));
    payload.add("userOriginalSentences", gson.toJsonTree(userOriginalSentences));
    return "Extract words the learner likely learned NEW from corrected/natural versions.\n"
        + "Compare userOriginalSentences with sentences to find words the user didn't know.\n"
        + "Input data:\n"
        + gson.toJson(payload);
  }

  private String extractFirstTextPart(String body) {
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

  private String extractTextFromSseChunk(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      JsonArray candidates = root.getAsJsonArray("candidates");
      if (candidates == null || candidates.size() == 0) {
        return "";
      }
      JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
      JsonArray partsArr = content.getAsJsonArray("parts");
      if (partsArr == null || partsArr.size() == 0) {
        return "";
      }
      return partsArr.get(0).getAsJsonObject().get("text").getAsString();
    } catch (Exception e) {
      return "";
    }
  }

  /**
   * Best-effort incremental parser: attempts to parse completed expression objects from accumulated
   * JSON text. Returns all fully-parsed expressions found so far.
   */
  private List<ISessionSummaryLlmManager.FilteredExpression> tryParseExpressions(
      String accumulatedText) {
    List<ISessionSummaryLlmManager.FilteredExpression> result = new ArrayList<>();
    String cleanJson = accumulatedText.replace("```json", "").replace("```", "").trim();

    // Find the start of the expressions array
    int arrStart = cleanJson.indexOf('[');
    if (arrStart < 0) {
      return result;
    }
    String fromArray = cleanJson.substring(arrStart);

    // Walk through the string and try to extract complete JSON objects
    int depth = 0;
    int objStart = -1;
    for (int i = 0; i < fromArray.length(); i++) {
      char c = fromArray.charAt(i);
      if (c == '{') {
        if (depth == 0) {
          objStart = i;
        }
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0 && objStart >= 0) {
          String objStr = fromArray.substring(objStart, i + 1);
          ISessionSummaryLlmManager.FilteredExpression expr = tryParseSingleExpression(objStr);
          if (expr != null) {
            result.add(expr);
          }
          objStart = -1;
        }
      }
    }
    return result;
  }

  private ISessionSummaryLlmManager.FilteredExpression tryParseSingleExpression(String jsonStr) {
    try {
      JsonObject item = JsonParser.parseString(jsonStr).getAsJsonObject();
      String type = trimToNull(readAsString(item, "type"));
      String prompt = trimToNull(readAsString(item, "koreanPrompt"));
      String before = trimToNull(readAsString(item, "before"));
      String after = trimToNull(readAsString(item, "after"));
      String explanation = trimToNull(readAsString(item, "explanation"));
      if (type == null
          || prompt == null
          || before == null
          || after == null
          || explanation == null) {
        return null;
      }
      return new ISessionSummaryLlmManager.FilteredExpression(
          type, prompt, before, after, explanation);
    } catch (Exception e) {
      return null;
    }
  }

  private List<HighlightSection> limitAndSanitizeHighlights(
      List<HighlightSection> input, int maxCount) {
    List<HighlightSection> result = new ArrayList<>();
    if (input == null) {
      return result;
    }
    for (HighlightSection item : input) {
      if (item == null) {
        continue;
      }
      String english = trimToNull(item.getEnglish());
      String korean = trimToNull(item.getKorean());
      String reason = trimToNull(item.getReason());
      if (english == null || korean == null || reason == null) {
        continue;
      }
      result.add(new HighlightSection(english, korean, reason));
      if (result.size() >= maxCount) {
        break;
      }
    }
    return result;
  }

  private List<ExpressionSection> limitAndSanitizeExpressions(
      List<ExpressionSection> input, int maxCount) {
    List<ExpressionSection> result = new ArrayList<>();
    if (input == null) {
      return result;
    }
    for (ExpressionSection item : input) {
      if (item == null) {
        continue;
      }
      String type = trimToNull(item.getType());
      String prompt = trimToNull(item.getKoreanPrompt());
      String before = trimToNull(item.getBefore());
      String after = trimToNull(item.getAfter());
      String explanation = trimToNull(item.getExplanation());
      if (type == null
          || prompt == null
          || before == null
          || after == null
          || explanation == null) {
        continue;
      }
      if (normalize(before).equals(normalize(after))) {
        continue;
      }
      result.add(new ExpressionSection(type, prompt, before, after, explanation));
      if (result.size() >= maxCount) {
        break;
      }
    }
    return result;
  }

  private List<WordSection> limitAndSanitizeWords(List<WordSection> input, int maxCount) {
    List<WordSection> result = new ArrayList<>();
    if (input == null) {
      return result;
    }
    for (WordSection item : input) {
      if (item == null) {
        continue;
      }
      String english = trimToNull(item.getEnglish());
      String korean = trimToNull(item.getKorean());
      String exampleEnglish = trimToNull(item.getExampleEnglish());
      String exampleKorean = trimToNull(item.getExampleKorean());
      if (english == null || korean == null) {
        continue;
      }
      if (exampleEnglish == null) {
        exampleEnglish = english;
      }
      if (exampleKorean == null) {
        exampleKorean = korean;
      }
      result.add(new WordSection(english, korean, exampleEnglish, exampleKorean));
      if (result.size() >= maxCount) {
        break;
      }
    }
    return result;
  }

  static List<ISessionSummaryLlmManager.ExtractedWord> parseExtractedWordsPayload(
      String rawPayload) {
    List<ISessionSummaryLlmManager.ExtractedWord> result = new ArrayList<>();
    String cleanJson = stripJsonFence(rawPayload);
    if (cleanJson.isEmpty()) {
      return result;
    }

    JsonArray items = null;
    try {
      if (cleanJson.trim().startsWith("[")) {
        items = JsonParser.parseString(cleanJson).getAsJsonArray();
      } else {
        JsonObject root = JsonParser.parseString(cleanJson).getAsJsonObject();
        if (root.has("items") && root.get("items").isJsonArray()) {
          items = root.getAsJsonArray("items");
        }
      }
    } catch (Exception ignored) {
      return result;
    }

    if (items == null) {
      return result;
    }

    Set<String> seen = new HashSet<>();
    for (int i = 0; i < items.size(); i++) {
      if (!items.get(i).isJsonObject()) {
        continue;
      }
      JsonObject item = items.get(i).getAsJsonObject();
      String en = trimToNullStatic(readAsString(item, "en"));
      String ko = trimToNullStatic(readAsString(item, "ko"));
      JsonObject exampleObject = readAsObject(item, "example");
      String exampleEn = trimToNullStatic(readAsString(exampleObject, "en"));
      String exampleKo = trimToNullStatic(readAsString(exampleObject, "ko"));
      if (en == null
          || ko == null
          || exampleObject == null
          || exampleEn == null
          || exampleKo == null) {
        continue;
      }
      String key = normalizeStatic(en);
      if (seen.contains(key)) {
        continue;
      }
      seen.add(key);
      result.add(new ISessionSummaryLlmManager.ExtractedWord(en, ko, exampleEn, exampleKo));
    }
    return result;
  }

  private static String stripJsonFence(String rawPayload) {
    if (rawPayload == null) {
      return "";
    }
    return rawPayload.replace("```json", "").replace("```", "").trim();
  }

  private static String readAsString(JsonObject obj, String key) {
    try {
      if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
        return obj.get(key).getAsString();
      }
    } catch (Exception ignored) {
      // No-op
    }
    return null;
  }

  private static JsonObject readAsObject(JsonObject obj, String key) {
    try {
      if (obj != null && obj.has(key) && obj.get(key).isJsonObject()) {
        return obj.getAsJsonObject(key);
      }
    } catch (Exception ignored) {
      // No-op
    }
    return null;
  }

  private static String trimToNullStatic(String text) {
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String normalizeStatic(String text) {
    return text == null ? "" : text.trim().toLowerCase();
  }

  private String trimToNull(String text) {
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String normalize(String text) {
    return text == null ? "" : text.trim().toLowerCase();
  }

  private static String normalizeOrDefault(String value, String defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }

  public static class LlmSections {
    private List<HighlightSection> highlights;
    private List<ExpressionSection> expressions;
    private List<WordSection> words;

    public List<HighlightSection> getHighlights() {
      return highlights;
    }

    public void setHighlights(List<HighlightSection> highlights) {
      this.highlights = highlights;
    }

    public List<ExpressionSection> getExpressions() {
      return expressions;
    }

    public void setExpressions(List<ExpressionSection> expressions) {
      this.expressions = expressions;
    }

    public List<WordSection> getWords() {
      return words;
    }

    public void setWords(List<WordSection> words) {
      this.words = words;
    }
  }

  public static class HighlightSection {
    private String english;
    private String korean;
    private String reason;

    public HighlightSection() {}

    public HighlightSection(String english, String korean, String reason) {
      this.english = english;
      this.korean = korean;
      this.reason = reason;
    }

    public String getEnglish() {
      return english;
    }

    public String getKorean() {
      return korean;
    }

    public String getReason() {
      return reason;
    }
  }

  public static class ExpressionSection {
    private String type;
    private String koreanPrompt;
    private String before;
    private String after;
    private String explanation;

    public ExpressionSection() {}

    public ExpressionSection(
        String type, String koreanPrompt, String before, String after, String explanation) {
      this.type = type;
      this.koreanPrompt = koreanPrompt;
      this.before = before;
      this.after = after;
      this.explanation = explanation;
    }

    public String getType() {
      return type;
    }

    public String getKoreanPrompt() {
      return koreanPrompt;
    }

    public String getBefore() {
      return before;
    }

    public String getAfter() {
      return after;
    }

    public String getExplanation() {
      return explanation;
    }
  }

  public static class WordSection {
    private String english;
    private String korean;
    private String exampleEnglish;
    private String exampleKorean;

    public WordSection() {}

    public WordSection(String english, String korean, String exampleEnglish, String exampleKorean) {
      this.english = english;
      this.korean = korean;
      this.exampleEnglish = exampleEnglish;
      this.exampleKorean = exampleKorean;
    }

    public String getEnglish() {
      return english;
    }

    public String getKorean() {
      return korean;
    }

    public String getExampleEnglish() {
      return exampleEnglish;
    }

    public String getExampleKorean() {
      return exampleKorean;
    }
  }
}
