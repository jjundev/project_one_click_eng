package com.jjundev.oneclickeng.manager_gemini;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IDialogueGenerateManager;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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

  public String getPredefinedScript(String title) {
    String opponentName = "AI Coach";
    String opponentRole = "Tutor";
    String opponentGender = "female";
    List<ScriptLine> lines = new ArrayList<>();

    if (title.equals("카페에서 주문하기")) {
      opponentName = "바리스타";
      opponentRole = "Barista";
      opponentGender = "female";
      lines.add(new ScriptLine("안녕하세요! 주문 도와드릴까요?", "Hello! How can I help you today?", "model"));
      lines.add(
          new ScriptLine(
              "따뜻한 아메리카노 한 잔이랑 초콜릿 머핀 하나 주세요.",
              "One hot americano and a chocolate muffin, please.",
              "user"));
      lines.add(
          new ScriptLine(
              "아메리카노 사이즈는 어떤 걸로 드릴까요?", "What size would you like for your americano?", "model"));
      lines.add(
          new ScriptLine(
              "톨 사이즈로 주세요. 그리고 우유는 저지방으로 변경 가능한가요?",
              "Tall size, please. And is it possible to change to low-fat milk?",
              "user"));
      lines.add(
          new ScriptLine(
              "네, 가능합니다. 머핀은 데워 드릴까요?",
              "Yes, we can do that. Would you like me to warm up the muffin?",
              "model"));
      lines.add(new ScriptLine("네, 부탁드려요. 카드로 결제하겠습니다.", "Yes, please. I'll pay by card.", "user"));
      lines.add(new ScriptLine("알겠습니다. 잠시만 기다려 주세요.", "Got it. Please wait a moment.", "model"));

    } else if (title.equals("회사에서 자기소개")) {
      opponentName = "팀장님";
      opponentRole = "Team Manager";
      opponentGender = "female";
      lines.add(
          new ScriptLine(
              "현준 씨, 만나서 반갑습니다. 저희 팀에 오신 걸 환영해요.",
              "Nice to meet you, Hyunjun. Welcome to our team.",
              "model"));
      lines.add(
          new ScriptLine(
              "감사합니다, 팀장님. 마케팅 팀의 일원이 되어 정말 기쁩니다.",
              "Thank you. I'm very excited to be part of the marketing team.",
              "user"));
      lines.add(
          new ScriptLine(
              "이전 직장에서는 어떤 업무를 주로 담당하셨나요?",
              "What were your main responsibilities at your previous job?",
              "model"));
      lines.add(
          new ScriptLine(
              "주로 브랜드 SNS 채널 관리와 유료 광고 캠페인 기획을 담당했습니다.",
              "I was mainly in charge of managing brand SNS channels and planning paid ad campaigns.",
              "user"));
      lines.add(
          new ScriptLine(
              "그렇군요. 우리 팀에서는 이번 분기 브랜드 리뉴얼 프로젝트를 맡게 되실 거예요.",
              "I see. In our team, you'll be working on the brand renewal project for this quarter.",
              "model"));
      lines.add(
          new ScriptLine(
              "알겠습니다. 최선을 다해 프로젝트를 성공적으로 이끌어 보겠습니다.",
              "Understood. I'll do my best to make the project a success.",
              "user"));

    } else if (title.equals("공항 입국 심사")) {
      opponentName = "입국 심사관";
      opponentRole = "Immigration Officer";
      opponentGender = "male";
      lines.add(
          new ScriptLine(
              "여권 좀 보여주시겠어요? 방문 목적이 무엇입니까?",
              "May I see your passport, please? What is the purpose of your visit?",
              "model"));
      lines.add(
          new ScriptLine(
              "여기 있습니다. 방문 목적은 관광입니다. 일주일 동안 머무를 예정이에요.",
              "Here it is. I'm here for sightseeing. I'll be staying for a week.",
              "user"));
      lines.add(new ScriptLine("어디에서 머무르실 계획인가요?", "Where are you planning to stay?", "model"));
      lines.add(
          new ScriptLine(
              "시내에 있는 힐튼 호텔에서 예약했습니다. 여기 예약 확인증입니다.",
              "I've booked a room at the Hilton Hotel downtown. Here is my reservation confirmation.",
              "user"));
      lines.add(
          new ScriptLine("귀국 항공권도 가지고 계신가요?", "Do you have a return ticket as well?", "model"));
      lines.add(
          new ScriptLine(
              "네, 여기 있습니다. 다음 주 일요일 오후 비행기입니다.",
              "Yes, here it is. My flight is for next Sunday afternoon.",
              "user"));
      lines.add(
          new ScriptLine(
              "네, 확인되었습니다. 즐거운 여행 되세요.",
              "Okay, everything looks good. Have a nice trip.",
              "model"));

    } else if (title.equals("택시 목적지 말하기")) {
      opponentName = "택시 기사";
      opponentRole = "Taxi Driver";
      opponentGender = "male";
      lines.add(new ScriptLine("어서 오세요. 어디로 모실까요?", "Hello. Where would you like to go?", "model"));
      lines.add(
          new ScriptLine(
              "기사님, 강남역 11번 출구로 가주세요. 얼마나 걸릴까요?",
              "Please take me to Gangnam Station, exit 11. How long will it take?",
              "user"));
      lines.add(
          new ScriptLine(
              "지금 퇴근 시간이라 길이 좀 막히네요. 40분 정도 예상됩니다.",
              "It's rush hour, so traffic is heavy. I expect it'll take about 40 minutes.",
              "model"));
      lines.add(
          new ScriptLine(
              "생각보다 오래 걸리네요. 혹시 빠른 길로 가주실 수 있나요?",
              "That's longer than I thought. Could you take a faster route if possible?",
              "user"));
      lines.add(
          new ScriptLine(
              "네, 고속도로로 우회해서 최대한 빨리 가보겠습니다.",
              "Sure, I'll take a detour via the highway to get there as fast as I can.",
              "model"));
      lines.add(
          new ScriptLine(
              "감사합니다. 저기 건물 앞에서 내려주시면 됩니다.",
              "Thank you. You can drop me off in front of that building over there.",
              "user"));

    } else {
      return createMockScript(
          title, opponentName, opponentRole, "male", "안녕하세요! [" + title + "] 대본입니다.");
    }

    ScriptData data = new ScriptData(title, opponentName, opponentRole, opponentGender, lines);
    return gson.toJson(data);
  }

  private String createMockScript(
      String title,
      String opponentName,
      String opponentRole,
      String opponentGender,
      String initialKo) {
    List<ScriptLine> lines = new ArrayList<>();
    lines.add(
        new ScriptLine(
            initialKo, "Hello! I've prepared a script for the situation you mentioned.", "model"));
    lines.add(
        new ScriptLine(
            "정말 감사합니다. 이 대화가 제 학습에 큰 도움이 될 것 같아요.",
            "Thank you so much. I think this conversation will be a great help for my learning.",
            "user"));
    lines.add(
        new ScriptLine(
            "천만예요. 그럼 첫 번째 문장부터 시작해볼까요?",
            "You're welcome. Shall we start from the first sentence then?",
            "model"));
    lines.add(
        new ScriptLine("네, 준비되었습니다. 제가 먼저 읽어볼게요.", "Yes, I'm ready. I'll read first.", "user"));

    ScriptData data = new ScriptData(title, opponentName, opponentRole, opponentGender, lines);
    return gson.toJson(data);
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
        + "  \"topic\": \"A short description of the conversation topic in Korean\",\n"
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
        + "9. Strictly adhere to the vocabulary, grammar, and sentence length constraints of the specified level.\n"
        + "10. Do NOT include any text outside the JSON object.\n"
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

  private static String normalizeOrDefault(String value, String defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }
}
