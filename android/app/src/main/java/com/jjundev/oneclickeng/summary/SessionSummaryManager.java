package com.jjundev.oneclickeng.summary;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjundev.oneclickeng.fragment.dialoguelearning.manager_contracts.ISessionSummaryLlmManager;
import com.jjundev.oneclickeng.fragment.dialoguelearning.model.FutureFeedbackResult;
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
  private static final int MAX_HIGHLIGHTS = 5;
  private static final int MAX_EXPRESSIONS = 8;
  private static final int MAX_WORDS = 12;

  private final OkHttpClient client;
  private final Gson gson;
  private final Handler mainHandler;
  private final String apiKey;
  private final String modelName;
  private static final OkHttpClient streamingClient =
      new OkHttpClient.Builder()
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(0, TimeUnit.SECONDS)
          .writeTimeout(30, TimeUnit.SECONDS)
          .build();

  public interface Callback {
    void onSuccess(LlmSections sections);

    void onFailure(String error);
  }

  public SessionSummaryManager(String apiKey, String modelName) {
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

  public void generateSectionsAsync(SummaryFeatureBundle bundle, Callback callback) {
    if (callback == null) {
      return;
    }
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
                addSystemInstruction(requestBody);

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty("text", buildUserPrompt(bundle));
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
                    postFailure(callback, "Summary LLM request failed: " + response.code());
                    return;
                  }

                  LlmSections sections = parseResponse(body);
                  mainHandler.post(() -> callback.onSuccess(sections));
                }
              } catch (Exception e) {
                postFailure(callback, "Summary LLM error: " + e.getMessage());
              }
            })
        .start();
  }

  @Override
  public void generateFutureFeedbackStreamingAsync(
      SummaryFeatureBundle bundle,
      @NonNull ISessionSummaryLlmManager.FutureFeedbackCallback callback) {
    if (callback == null) {
      return;
    }
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
                JsonObject systemInstruction = new JsonObject();
                JsonArray systemParts = new JsonArray();
                JsonObject systemPart = new JsonObject();
                systemPart.addProperty("text", buildFutureFeedbackSystemPrompt());
                systemParts.add(systemPart);
                systemInstruction.add("parts", systemParts);
                requestBody.add("systemInstruction", systemInstruction);

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty("text", buildFutureFeedbackUserPrompt(bundle));
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
                        () -> callback.onFailure("Summary LLM request failed: " + response.code()));
                    return;
                  }

                  if (response.body() == null) {
                    mainHandler.post(() -> callback.onFailure("Summary LLM empty response body"));
                    return;
                  }

                  BufferedSource source = response.body().source();
                  StringBuilder streamedText = new StringBuilder();
                  while (!source.exhausted()) {
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

                      JsonObject firstPart = responseParts.get(0).getAsJsonObject();
                      if (!firstPart.has("text")) {
                        continue;
                      }
                      streamedText.append(firstPart.get("text").getAsString());
                    } catch (Exception ignored) {
                      // Ignore malformed chunks and keep reading.
                    }
                  }

                  FutureSelfFeedbackSection feedback =
                      parseFutureFeedbackFromText(streamedText.toString());
                  if (feedback == null) {
                    mainHandler.post(
                        () -> callback.onFailure("Failed to parse future feedback response"));
                    return;
                  }
                  final String positive =
                      feedback.getPositive() == null ? "" : feedback.getPositive();
                  final String toImprove =
                      feedback.getToImprove() == null ? "" : feedback.getToImprove();
                  mainHandler.post(
                      () -> callback.onSuccess(new FutureFeedbackResult(positive, toImprove)));
                }
              } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure("Summary LLM error: " + e.getMessage()));
              }
            })
        .start();
  }

  @Override
  public void extractWordsFromSentencesAsync(
      @NonNull List<String> words,
      @NonNull List<String> sentences,
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
                part.addProperty("text", buildWordExtractionUserPrompt(words, sentences));
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

  private void postFailure(Callback callback, String message) {
    mainHandler.post(() -> callback.onFailure(message));
  }

  private void addSystemInstruction(JsonObject root) {
    JsonObject systemInstruction = new JsonObject();
    JsonArray systemParts = new JsonArray();
    JsonObject systemPart = new JsonObject();
    systemPart.addProperty("text", buildSystemPrompt());
    systemParts.add(systemPart);
    systemInstruction.add("parts", systemParts);
    root.add("systemInstruction", systemInstruction);
  }

  private String buildSystemPrompt() {
    return "You are an English learning summary formatter.\n"
        + "Return JSON only with this exact top-level shape:\n"
        + "{\n"
        + "  \"highlights\": [{\"english\":\"...\",\"korean\":\"...\",\"reason\":\"...\"}],\n"
        + "  \"expressions\": [{\"type\":\"...\",\"koreanPrompt\":\"...\",\"before\":\"...\",\"after\":\"...\",\"explanation\":\"...\"}],\n"
        + "  \"words\": [{\"english\":\"...\",\"korean\":\"...\",\"exampleEnglish\":\"...\",\"exampleKorean\":\"...\"}],\n"
        + "  \"futureSelfFeedback\": {\"positive\":\"...\",\"toImprove\":\"...\"}\n"
        + "}\n"
        + "Rules:\n"
        + "1) highlights <= "
        + MAX_HIGHLIGHTS
        + "\n"
        + "2) expressions <= "
        + MAX_EXPRESSIONS
        + "\n"
        + "3) words <= "
        + MAX_WORDS
        + "\n"
        + "4) Keep Korean fields natural and concise.\n"
        + "5) Do not include markdown code fences.\n"
        + "6) highlights.english must reuse a user's original English sentence from highlightCandidates.\n"
        + "7) expressions.before must exactly reuse the user's original English sentence from expressionCandidates.\n"
        + "8) In expressions.after, wrap the key improved phrase with [[...]] (one or more allowed).\n"
        + "9) Preserve meaning from candidates, do not invent facts.";
  }

  private String buildUserPrompt(SummaryFeatureBundle bundle) {
    JsonObject payload = new JsonObject();
    payload.addProperty("totalScore", bundle.getTotalScore());
    payload.add("highlightCandidates", gson.toJsonTree(bundle.getHighlightCandidates()));
    payload.add("expressionCandidates", gson.toJsonTree(bundle.getExpressionCandidates()));
    payload.add("wordCandidates", gson.toJsonTree(bundle.getWordCandidates()));
    payload.add("positiveSignals", gson.toJsonTree(bundle.getPositiveSignals()));
    payload.add("improveSignals", gson.toJsonTree(bundle.getImproveSignals()));

    return "Refine and select the best learning summary entries from these candidates.\n"
        + "Input data:\n"
        + gson.toJson(payload);
  }

  private String buildFutureFeedbackSystemPrompt() {
    return "You are an English learning coach.\n"
        + "Return JSON only with this exact top-level shape:\n"
        + "{\n"
        + "  \"futureSelfFeedback\": {\"positive\":\"...\",\"toImprove\":\"...\"}\n"
        + "}\n"
        + "Rules:\n"
        + "1) Return only futureSelfFeedback.\n"
        + "2) Both fields must be concise Korean coaching sentences.\n"
        + "3) Do not include markdown code fences.\n"
        + "4) Preserve meaning from provided signals and candidates.\n"
        + "5) Do not invent facts outside input.";
  }

  private String buildFutureFeedbackUserPrompt(SummaryFeatureBundle bundle) {
    JsonObject payload = new JsonObject();
    payload.addProperty("totalScore", bundle.getTotalScore());
    payload.add("positiveSignals", gson.toJsonTree(bundle.getPositiveSignals()));
    payload.add("improveSignals", gson.toJsonTree(bundle.getImproveSignals()));
    payload.add("highlightCandidates", gson.toJsonTree(bundle.getHighlightCandidates()));
    payload.add("expressionCandidates", gson.toJsonTree(bundle.getExpressionCandidates()));
    payload.add("wordCandidates", gson.toJsonTree(bundle.getWordCandidates()));

    return "Generate only futureSelfFeedback from this learner signal bundle.\n"
        + "Input data:\n"
        + gson.toJson(payload);
  }

  private String buildWordExtractionSystemPrompt() {
    return "You are an English vocabulary extractor for Korean learners.\n"
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

  private String buildWordExtractionUserPrompt(List<String> words, List<String> sentences) {
    JsonObject payload = new JsonObject();
    payload.add("words", gson.toJsonTree(words));
    payload.add("sentences", gson.toJsonTree(sentences));
    return "Extract difficult but useful words for this learner.\n"
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

  private LlmSections parseResponse(String body) {
    String text = extractFirstTextPart(body);
    if (text.isEmpty()) {
      return new LlmSections();
    }

    String cleanJson = text.replace("```json", "").replace("```", "").trim();
    LlmSections sections = gson.fromJson(cleanJson, LlmSections.class);
    return sanitize(sections);
  }

  private FutureSelfFeedbackSection parseFutureFeedbackFromText(String streamedText) {
    String cleanJson =
        streamedText == null ? "" : streamedText.replace("```json", "").replace("```", "").trim();
    if (cleanJson.isEmpty()) {
      return null;
    }

    try {
      JsonObject root = JsonParser.parseString(cleanJson).getAsJsonObject();
      JsonObject futureObject;
      if (root.has("futureSelfFeedback") && root.get("futureSelfFeedback").isJsonObject()) {
        futureObject = root.getAsJsonObject("futureSelfFeedback");
      } else {
        futureObject = root;
      }

      FutureSelfFeedbackSection section =
          gson.fromJson(futureObject, FutureSelfFeedbackSection.class);
      if (section == null) {
        return null;
      }
      FutureSelfFeedbackSection sanitized = new FutureSelfFeedbackSection();
      sanitized.setPositive(trimToNull(section.getPositive()));
      sanitized.setToImprove(trimToNull(section.getToImprove()));
      return sanitized;
    } catch (Exception e) {
      return null;
    }
  }

  private LlmSections sanitize(LlmSections sections) {
    if (sections == null) {
      return new LlmSections();
    }

    LlmSections sanitized = new LlmSections();
    sanitized.setHighlights(limitAndSanitizeHighlights(sections.getHighlights(), MAX_HIGHLIGHTS));
    sanitized.setExpressions(
        limitAndSanitizeExpressions(sections.getExpressions(), MAX_EXPRESSIONS));
    sanitized.setWords(limitAndSanitizeWords(sections.getWords(), MAX_WORDS));

    FutureSelfFeedbackSection feedback = sections.getFutureSelfFeedback();
    if (feedback != null) {
      FutureSelfFeedbackSection normalized = new FutureSelfFeedbackSection();
      normalized.setPositive(trimToNull(feedback.getPositive()));
      normalized.setToImprove(trimToNull(feedback.getToImprove()));
      sanitized.setFutureSelfFeedback(normalized);
    }
    return sanitized;
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
    private FutureSelfFeedbackSection futureSelfFeedback;

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

    public FutureSelfFeedbackSection getFutureSelfFeedback() {
      return futureSelfFeedback;
    }

    public void setFutureSelfFeedback(FutureSelfFeedbackSection futureSelfFeedback) {
      this.futureSelfFeedback = futureSelfFeedback;
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

  public static class FutureSelfFeedbackSection {
    private String positive;
    private String toImprove;

    public String getPositive() {
      return positive;
    }

    public void setPositive(String positive) {
      this.positive = positive;
    }

    public String getToImprove() {
      return toImprove;
    }

    public void setToImprove(String toImprove) {
      this.toImprove = toImprove;
    }
  }
}
