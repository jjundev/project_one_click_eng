package com.example.test.manager_gemini;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.test.fragment.dialoguelearning.manager_contracts.ISentenceFeedbackManager;
import com.example.test.fragment.dialoguelearning.model.ConceptualBridge;
import com.example.test.fragment.dialoguelearning.model.GrammarFeedback;
import com.example.test.fragment.dialoguelearning.model.NaturalnessFeedback;
import com.example.test.fragment.dialoguelearning.model.ParaphrasingLevel;
import com.example.test.fragment.dialoguelearning.model.SentenceFeedback;
import com.example.test.fragment.dialoguelearning.model.ToneStyle;
import com.example.test.fragment.dialoguelearning.model.WritingScore;
import com.example.test.tool.IncrementalJsonSectionParser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

/**
 * Gemini Feedback Manager (Gemini 2 Flash Preview) Integrated with Prompt Caching and Streaming
 * support.
 */
public class SentenceFeedbackManager
    implements com.example.test.fragment.dialoguelearning.manager_contracts
        .ISentenceFeedbackManager {

  private static final String TAG = "SentenceFeedbackManager";
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  private static final String DEFAULT_MODEL_NAME = "gemini-3-flash-preview";
  private static final int CACHE_TTL_SECONDS = 3600; // 1 hour

  private static final String PREF_NAME = "gemini_cache_prefs";
  private static final String KEY_CACHE_NAME = "gemini_cache_name_v2";
  private static final String KEY_CACHE_CREATED = "gemini_cache_created_at";
  private static final String KEY_CACHE_TTL = "gemini_cache_ttl_seconds";
  private static final int MIN_REMAINING_TTL_SECONDS = 300; // 5 minutes

  private final OkHttpClient client;
  private final Gson gson;
  private final String apiKey;
  private final String modelName;
  private final Handler mainHandler;
  private final SharedPreferences prefs;

  private final Context context;
  private String cachedContentName;
  private boolean cacheReady = false;

  public interface FeedbackCallback {
    void onSuccess(SentenceFeedback feedback, boolean usedCache);

    void onError(String error);
  }

  interface ValidationCallback {
    void onValid(String cacheName, long remainingSeconds);

    void onInvalid();

    void onError(String error);
  }

  public SentenceFeedbackManager(Context context, String apiKey, String modelName) {
    this.context = context;
    this.apiKey = normalizeOrDefault(apiKey, "");
    this.modelName = normalizeOrDefault(modelName, DEFAULT_MODEL_NAME);
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    this.gson = new Gson();
    this.mainHandler = new Handler(Looper.getMainLooper());
    this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
  }

  private static final OkHttpClient streamingClient =
      new OkHttpClient.Builder()
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(0, TimeUnit.SECONDS) // No read timeout for streaming
          .writeTimeout(30, TimeUnit.SECONDS)
          .build();

  /** Cache initialization - Caches system prompt and context. */
  @Override
  public void initializeCache(ISentenceFeedbackManager.InitCallback callback) {
    String savedCacheName = prefs.getString(KEY_CACHE_NAME, null);

    if (savedCacheName != null) {
      long createdAt = prefs.getLong(KEY_CACHE_CREATED, 0);
      int ttl = prefs.getInt(KEY_CACHE_TTL, CACHE_TTL_SECONDS);
      long elapsedSeconds = (System.currentTimeMillis() - createdAt) / 1000;

      if (elapsedSeconds > ttl) {
        Log.d(TAG, "[GeminiCache] Local cache expired, creating new one");
        clearLocalCacheData();
        createCache(callback);
        return;
      }

      Log.d(TAG, "[GeminiCache] Found local cache info, verifying with server...");
      validateCacheFromServer(
          savedCacheName,
          new ValidationCallback() {
            @Override
            public void onValid(String name, long remainingSeconds) {
              if (remainingSeconds > MIN_REMAINING_TTL_SECONDS) {
                Log.i(TAG, "[GeminiCache] REUSING EXISTING CACHE: " + name);
                cachedContentName = name;
                cacheReady = true;
                mainHandler.post(callback::onReady);
              } else {
                Log.i(TAG, "[GeminiCache] Cache expiring soon. CREATING NEW CACHE.");
                createCache(callback);
              }
            }

            @Override
            public void onInvalid() {
              Log.i(TAG, "[GeminiCache] Cache invalid on server. CREATING NEW CACHE.");
              clearLocalCacheData();
              createCache(callback);
            }

            @Override
            public void onError(String error) {
              Log.w(TAG, "[GeminiCache] Validation error: " + error + ". CREATING NEW CACHE.");
              createCache(callback);
            }
          });
    } else {
      Log.i(TAG, "[GeminiCache] No local cache found. CREATING NEW CACHE.");
      createCache(callback);
    }
  }

  private void createCache(ISentenceFeedbackManager.InitCallback callback) {
    new Thread(
            () -> {
              try {
                String systemPrompt = buildSystemPrompt();
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", "models/" + modelName);

                JsonArray contents = new JsonArray();
                JsonObject contextContent = new JsonObject();
                contextContent.addProperty("role", "user");
                JsonArray contextParts = new JsonArray();
                JsonObject contextPart = new JsonObject();
                contextPart.addProperty("text", buildContextMaterial());
                contextParts.add(contextPart);
                contextContent.add("parts", contextParts);
                contents.add(contextContent);

                JsonObject modelResponse = new JsonObject();
                modelResponse.addProperty("role", "model");
                JsonArray modelParts = new JsonArray();
                JsonObject modelPart = new JsonObject();
                modelPart.addProperty("text", "I understand. I am ready to evaluate sentences.");
                modelParts.add(modelPart);
                modelResponse.add("parts", modelParts);
                contents.add(modelResponse);

                requestBody.add("contents", contents);

                JsonObject sysInstruction = new JsonObject();
                JsonArray sysParts = new JsonArray();
                JsonObject sysPart = new JsonObject();
                sysPart.addProperty("text", systemPrompt);
                sysParts.add(sysPart);
                sysInstruction.add("parts", sysParts);
                requestBody.add("systemInstruction", sysInstruction);

                requestBody.addProperty("ttl", CACHE_TTL_SECONDS + "s");
                requestBody.addProperty("displayName", "SentenceFeedbackContext");

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

                  Log.i(TAG, "[GeminiCache] NEW CACHE CREATED: " + cachedContentName);
                  mainHandler.post(callback::onReady);
                }
              } catch (Exception e) {
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
                  String responseBody = response.body().string();
                  JsonObject result = JsonParser.parseString(responseBody).getAsJsonObject();
                  if (result.has("expireTime")) {
                    Instant expireTime = Instant.parse(result.get("expireTime").getAsString());
                    long remainingSeconds =
                        expireTime.getEpochSecond() - Instant.now().getEpochSecond();
                    callback.onValid(cacheName, remainingSeconds);
                  } else {
                    callback.onError("No expireTime in response");
                  }
                }
              } catch (Exception e) {
                callback.onError(e.getMessage());
              }
            })
        .start();
  }

  private void clearLocalCacheData() {
    prefs.edit().remove(KEY_CACHE_NAME).remove(KEY_CACHE_CREATED).remove(KEY_CACHE_TTL).apply();
  }

  @Override
  public void analyzeSentenceStreaming(
      String originalSentence,
      String userSentence,
      ISentenceFeedbackManager.StreamingFeedbackCallback callback) {
    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                if (cacheReady && cachedContentName != null) {
                  requestBody.addProperty("cachedContent", cachedContentName);
                } else {
                  addSystemInstruction(requestBody);
                }

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();

                JsonObject textPart = new JsonObject();
                textPart.addProperty(
                    "text",
                    "Original Korean Sentence: \""
                        + originalSentence
                        + "\"\nUser's English Translation: \""
                        + userSentence
                        + "\"\nAnalyze the translation and provide JSON feedback.");
                parts.add(textPart);

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
                        + ":streamGenerateContent?key="
                        + apiKey
                        + "&alt=sse";
                String jsonBody = gson.toJson(requestBody);

                Request request =
                    new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                        .build();

                try (Response response = streamingClient.newCall(request).execute()) {
                  if (!response.isSuccessful()) {
                    mainHandler.post(
                        () -> callback.onError("Streaming failed: " + response.code()));
                    return;
                  }

                  BufferedSource source = response.body().source();
                  StringBuilder fullResponse = new StringBuilder();
                  SentenceFeedback accumulatedFeedback = new SentenceFeedback();
                  accumulatedFeedback.setUserSentence(trimToNull(userSentence));
                  IncrementalJsonSectionParser sectionParser = new IncrementalJsonSectionParser();

                  while (!source.exhausted()) {
                    String line = source.readUtf8Line();
                    if (line != null && line.startsWith("data: ")) {
                      String data = line.substring(6);
                      JsonObject root = JsonParser.parseString(data).getAsJsonObject();
                      JsonArray candidates = root.getAsJsonArray("candidates");
                      if (candidates != null && !candidates.isEmpty()) {
                        JsonObject content =
                            candidates.get(0).getAsJsonObject().getAsJsonObject("content");
                        JsonArray responseParts = content.getAsJsonArray("parts");
                        String text =
                            responseParts.get(0).getAsJsonObject().get("text").getAsString();

                        fullResponse.append(text);

                        // Use IncrementalJsonSectionParser to detect completed sections
                        java.util.List<IncrementalJsonSectionParser.SectionResult> sections =
                            sectionParser.addChunk(text);
                        for (IncrementalJsonSectionParser.SectionResult section : sections) {
                          processSectionResult(section, accumulatedFeedback, callback);
                        }
                      }
                    }
                  }
                  mainHandler.post(() -> callback.onComplete(accumulatedFeedback));
                }
              } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Streaming Error: " + e.getMessage()));
              }
            })
        .start();
  }

  private void processSectionResult(
      IncrementalJsonSectionParser.SectionResult section,
      SentenceFeedback accumulated,
      ISentenceFeedbackManager.StreamingFeedbackCallback callback) {
    try {
      switch (section.key) {
        case "writingScore":
          if (accumulated.getWritingScore() == null) {
            accumulated.setWritingScore(gson.fromJson(section.jsonValue, WritingScore.class));
            mainHandler.post(() -> callback.onSectionReady("writingScore", accumulated));
          }
          break;
        case "grammar":
          if (accumulated.getGrammar() == null) {
            accumulated.setGrammar(gson.fromJson(section.jsonValue, GrammarFeedback.class));
            mainHandler.post(() -> callback.onSectionReady("grammar", accumulated));
          }
          break;
        case "conceptualBridge":
          if (accumulated.getConceptualBridge() == null) {
            accumulated.setConceptualBridge(
                gson.fromJson(section.jsonValue, ConceptualBridge.class));
            mainHandler.post(() -> callback.onSectionReady("conceptualBridge", accumulated));
          }
          break;
        case "naturalness":
          if (accumulated.getNaturalness() == null) {
            accumulated.setNaturalness(gson.fromJson(section.jsonValue, NaturalnessFeedback.class));
            mainHandler.post(() -> callback.onSectionReady("naturalness", accumulated));
          }
          break;
        case "toneStyle":
          if (accumulated.getToneStyle() == null) {
            accumulated.setToneStyle(gson.fromJson(section.jsonValue, ToneStyle.class));
            mainHandler.post(() -> callback.onSectionReady("toneStyle", accumulated));
          }
          break;
        case "paraphrasing":
          if (accumulated.getParaphrasing() == null) {
            java.lang.reflect.Type type =
                new com.google.gson.reflect.TypeToken<
                    java.util.List<ParaphrasingLevel>>() {}.getType();
            accumulated.setParaphrasing(gson.fromJson(section.jsonValue, type));
            mainHandler.post(() -> callback.onSectionReady("paraphrasing", accumulated));
          }
          break;
      }
    } catch (Exception e) {
      Log.w(TAG, "Failed to parse section '" + section.key + "': " + e.getMessage());
    }
  }

  private void sendAndParseRequest(
      JsonObject requestBody, boolean usedCache, FeedbackCallback callback) {
    try {
      String url = BASE_URL + "/models/" + modelName + ":generateContent?key=" + apiKey;
      String jsonBody = gson.toJson(requestBody);
      Request request =
          new Request.Builder()
              .url(url)
              .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
              .build();

      try (Response response = client.newCall(request).execute()) {
        String responseBody = response.body() != null ? response.body().string() : "";
        if (!response.isSuccessful()) {
          if (usedCache && (response.code() == 400 || response.code() == 404)) {
            cacheReady = false;
            cachedContentName = null;
            clearLocalCacheData();
          }
          mainHandler.post(() -> callback.onError("Request failed: " + response.code()));
          return;
        }
        parseResponse(responseBody, usedCache, callback);
      }
    } catch (Exception e) {
      mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
    }
  }

  private void parseResponse(String responseBody, boolean usedCache, FeedbackCallback callback) {
    try {
      JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
      JsonArray candidates = root.getAsJsonArray("candidates");
      if (candidates != null && candidates.size() > 0) {
        String text =
            candidates
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0)
                .getAsJsonObject()
                .get("text")
                .getAsString();
        String cleanJson = text.replace("```json", "").replace("```", "").trim();
        SentenceFeedback feedback = gson.fromJson(cleanJson, SentenceFeedback.class);
        mainHandler.post(() -> callback.onSuccess(feedback, usedCache));
      } else {
        mainHandler.post(() -> callback.onError("No response generated"));
      }
    } catch (Exception e) {
      mainHandler.post(() -> callback.onError("Failed to parse response"));
    }
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public void clearCache() {
    if (cachedContentName == null) return;
    String nameToDelete = cachedContentName;
    cachedContentName = null;
    cacheReady = false;
    clearLocalCacheData();
    new Thread(
            () -> {
              try {
                String url = BASE_URL + "/" + nameToDelete + "?key=" + apiKey;
                Request request = new Request.Builder().url(url).delete().build();
                client.newCall(request).execute();
              } catch (Exception ignored) {
              }
            })
        .start();
  }

  private void addSystemInstruction(JsonObject root) {
    JsonObject sysInstruction = new JsonObject();
    JsonArray sysParts = new JsonArray();
    JsonObject sysPart = new JsonObject();
    sysPart.addProperty("text", buildSystemPrompt());
    sysParts.add(sysPart);
    sysInstruction.add("parts", sysParts);
    root.add("systemInstruction", sysInstruction);
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

  private String buildSystemPrompt() {
    String prompt = readAssetFile("prompts/sentence_feedback/system_prompt.md");
    if (prompt.isEmpty()) {
      return buildSystemPrompt_Dummy();
    }
    return prompt;
  }

  private String buildContextMaterial() {
    String material = readAssetFile("prompts/sentence_feedback/context_material.md");
    if (material.isEmpty()) {
      return buildContextMaterial_Dummy();
    }
    return material;
  }

  private String buildSystemPrompt_Dummy() {
    return "You are an expert English tutor for Korean students. Your task is to analyze the user's English translation (text or audio) of a Korean sentence and provide detailed feedback in a structured JSON format.\n"
        + "\n"
        + "The JSON format must strictly follow this structure:\n"
        + "{\n"
        + "  \"writingScore\": {\n"
        + "    \"score\": integer (0-100),\n"
        + "    \"encouragementMessage\": \"string (Korean)\",\n"
        + "    \"scoreColor\": integer (Color Int, e.g., -16711936 for Green, -65536 for Red)\n"
        + "  },\n"
        + "  \"grammar\": {\n"
        + "    \"correctedSentence\": {\n"
        + "      \"segments\": [\n"
        + "        { \"text\": \"string\", \"type\": \"normal\" | \"incorrect\" | \"correction\" | \"highlight\" }\n"
        + "      ]\n"
        + "    },\n"
        + "    \"explanation\": \"string (Korean explanation of grammar errors)\"\n"
        + "  },\n"
        + "  \"conceptualBridge\": {\n"
        + "    \"literalTranslation\": \"string (Korean literal translation of the user's sentence)\",\n"
        + "    \"explanation\": \"string (Korean explanation)\",\n"
        + "    \"vennDiagramGuide\": \"string (Korean guide message)\",\n"
        + "    \"vennDiagram\": {\n"
        + "      \"leftCircle\": { \"word\": \"string\", \"color\": \"#HexColor\", \"items\": [\"string (Korean)\"] },\n"
        + "      \"rightCircle\": { \"word\": \"string\", \"color\": \"#HexColor\", \"items\": [\"string (Korean)\"] },\n"
        + "      \"intersection\": { \"color\": \"#HexColor\", \"items\": [\"string (Korean)\"] }\n"
        + "    }\n"
        + "  },\n"
        + "  \"naturalness\": {\n"
        + "    \"naturalSentence\": {\n"
        + "      \"segments\": [\n"
        + "        { \"text\": \"string\", \"type\": \"normal\" | \"highlight\" }\n"
        + "      ]\n"
        + "    },\n"
        + "    \"naturalSentenceTranslation\": \"string (Korean translation of the natural sentence)\",\n"
        + "    \"explanation\": \"string (Korean)\",\n"
        + "    \"reasons\": [\n"
        + "      { \"keyword\": \"string\", \"description\": \"string (Korean)\" },\n"
        + "      { \"keyword\": \"string\", \"description\": \"string (Korean)\" }\n"
        + "    ] // Provide exactly 2 reasons\n"
        + "  },\n"
        + "  \"toneStyle\": {\n"
        + "    \"defaultLevel\": 2,\n"
        + "    \"levels\": [\n"
        + "      { \"level\": 0, \"sentence\": \"string (Very Formal)\", \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\" },\n"
        + "      { \"level\": 1, \"sentence\": \"string (Formal)\", \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\" },\n"
        + "      { \"level\": 2, \"sentence\": \"string (Neutral)\", \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\" },\n"
        + "      { \"level\": 3, \"sentence\": \"string (Casual)\", \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\" },\n"
        + "      { \"level\": 4, \"sentence\": \"string (Very Casual/Slang)\", \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\" }\n"
        + "    ]\n"
        + "  },\n"
        + "  \"paraphrasing\": [\n"
        + "    { \"level\": 1, \"label\": \"Beginner\", \"sentence\": \"string\", \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\" },\n"
        + "    { \"level\": 2, \"label\": \"Intermediate\", \"sentence\": \"string\", \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\" },\n"
        + "    { \"level\": 3, \"label\": \"Advanced\", \"sentence\": \"string\", \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\" }\n"
        + "  ]\n"
        + "}\n"
        + "\n"
        + "Respond ONLY with valid JSON. Do not include markdown code blocks.";
  }

  private String buildContextMaterial_Dummy() {
    return "# English Translation Feedback Guidelines\nScoring: 0-100. Color: Green >= 70, Orange 50-69, Red < 50. Grammar: normal, incorrect, correction, highlight. Common Issues: SOV to SVO, Articles, Plurals, Tense. Venn: Left, Right, Intersection items.";
  }

  private byte[] pcmToWav(byte[] pcmData) throws IOException {
    int sampleRate = 16000;
    int channels = 1;
    int byteRate = 16 * sampleRate * channels / 8;
    int totalDataLen = pcmData.length + 36;
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    writeString(baos, "RIFF");
    writeInt(baos, totalDataLen);
    writeString(baos, "WAVE");
    writeString(baos, "fmt ");
    writeInt(baos, 16);
    writeShort(baos, (short) 1);
    writeShort(baos, (short) channels);
    writeInt(baos, sampleRate);
    writeInt(baos, byteRate);
    writeShort(baos, (short) 2);
    writeShort(baos, (short) 16);
    writeString(baos, "data");
    writeInt(baos, pcmData.length);
    baos.write(pcmData);
    return baos.toByteArray();
  }

  private static String normalizeOrDefault(String value, String defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }

  private void writeString(java.io.ByteArrayOutputStream baos, String str) throws IOException {
    baos.write(str.getBytes());
  }

  private void writeInt(java.io.ByteArrayOutputStream baos, int val) throws IOException {
    baos.write(val & 0xFF);
    baos.write((val >> 8) & 0xFF);
    baos.write((val >> 16) & 0xFF);
    baos.write((val >> 24) & 0xFF);
  }

  private void writeShort(java.io.ByteArrayOutputStream baos, short val) throws IOException {
    baos.write(val & 0xFF);
    baos.write((val >> 8) & 0xFF);
  }
}
