# Gemini Streaming API + 점진적 렌더링 구현 계획

## Context

현재 Sentence Feedback API는 `generateContent` (일괄 응답) 엔드포인트를 사용 중이다. 전체 JSON (~6개 섹션)이 완성될 때까지 기다린 후 한 번에 렌더링하므로, 수 초~십수 초 동안 스켈레톤만 표시된다.

`streamGenerateContent` 엔드포인트로 전환하면, 응답이 토큰 단위로 스트리밍되어 완성된 섹션부터 즉시 UI에 렌더링할 수 있다.

**JSON 응답 구조 (6개 섹션, 순서대로 생성됨):**
```
writingScore → grammar → conceptualBridge → naturalness → toneStyle → paraphrasing
```

---

## 구현 전략: 섹션 단위 점진적 렌더링

### 핵심 아이디어

1. `streamGenerateContent` SSE 스트림에서 텍스트 청크를 누적
2. 누적 텍스트에서 완성된 JSON 섹션을 중괄호/대괄호 깊이 추적으로 감지
3. 완성된 섹션마다 콜백을 발생시켜 UI에 즉시 반영
4. UI는 스켈레톤 → 실제 콘텐츠를 섹션 단위로 전환

---

## 수정 파일 목록

| 파일 | 변경 내용 |
|---|---|
| `IncrementalJsonSectionParser.java` | **신규** - JSON 스트림에서 섹션 단위 파싱 |
| `GeminiCachedFeedbackManager.java` | 스트리밍 메서드 추가 (`analyzeSentenceStreaming`) |
| `GeminiFeedbackManager.java` | 스트리밍 메서드 추가 (폴백용) |
| `SentenceFeedbackBinder.java` | 섹션별 개별 바인딩 + visibility 제어 메서드 추가 |
| `ScriptChatFragment.java` | 스트리밍 콜백 연동 + 점진적 UI 업데이트 로직 |
| `bottom_sheet_content_feedback.xml` | 각 섹션 초기 visibility=gone 설정 |

---

## 상세 구현

### 1. IncrementalJsonSectionParser (신규)

**파일**: `app/src/main/java/com/example/test/IncrementalJsonSectionParser.java`

스트리밍으로 수신되는 JSON 텍스트에서 완성된 top-level 섹션을 감지하는 파서.

**동작 원리:**
- 누적 버퍼에 텍스트 청크를 추가
- 미리 정의된 6개 섹션 키를 순서대로 탐색
- 각 키의 값 시작(`{` 또는 `[`) 이후 중괄호/대괄호 깊이를 추적
- 깊이가 0이 되면 해당 섹션이 완성된 것으로 판단
- 완성된 섹션의 JSON 값 문자열을 반환

```java
public class IncrementalJsonSectionParser {
    private static final String[] SECTION_KEYS = {
        "writingScore", "grammar", "conceptualBridge",
        "naturalness", "toneStyle", "paraphrasing"
    };

    private StringBuilder buffer = new StringBuilder();
    private Set<String> emittedSections = new HashSet<>();

    // 청크 추가 후 새로 완성된 섹션 목록 반환
    public List<SectionResult> addChunk(String chunk);

    // 섹션 결과: 키 + JSON 값 문자열
    public static class SectionResult {
        public final String key;
        public final String jsonValue; // 예: {"score":85,"encouragementMessage":"잘했어요!"}
    }
}
```

**JSON 문자열 리터럴 처리:**
- `"` 안의 `{`, `}`, `[`, `]`는 깊이 계산에서 제외
- `\"` 이스케이프 처리 고려

### 2. GeminiCachedFeedbackManager 수정

**파일**: `app/src/main/java/com/example/test/GeminiCachedFeedbackManager.java`

#### 새 콜백 인터페이스 추가

```java
public interface StreamingFeedbackCallback {
    void onSectionReady(String sectionName, Object sectionData);
    void onComplete(SentenceFeedback fullFeedback);
    void onError(String error);
}
```

#### 새 메서드: `analyzeSentenceStreaming()`

- 엔드포인트: `streamGenerateContent` (`generateContent` 대신)
- OkHttp 설정: `readTimeout(0)` (스트리밍용 타임아웃 해제)
- `enqueue()` 비동기 호출 + SSE 라인 파싱
- `IncrementalJsonSectionParser`를 사용하여 섹션 단위로 콜백 발생

```java
public void analyzeSentenceStreaming(String originalSentence, String userSentence,
                                      StreamingFeedbackCallback callback) {
    // 1. 기존과 동일한 요청 본문 구성 (cachedContent 포함)
    // 2. URL을 :streamGenerateContent?alt=sse 로 변경
    // 3. OkHttp enqueue로 비동기 SSE 스트림 수신
    // 4. BufferedSource.readUtf8Line() 루프로 "data: " 접두사 파싱
    // 5. candidates[0].content.parts[0].text 추출하여 파서에 전달
    // 6. 완성된 섹션마다 콜백 (mainHandler.post)
    // 7. 스트림 종료 시 전체 SentenceFeedback 파싱 후 onComplete
}
```

**SSE 파싱 핵심:**
```java
BufferedSource source = responseBody.source();
while (!source.exhausted()) {
    String line = source.readUtf8Line();
    if (line != null && line.startsWith("data: ")) {
        String json = line.substring(6);
        // JSON에서 text 추출 → parser.addChunk(text)
        // 새 섹션이 완성되면 콜백
    }
}
```

**스트리밍 전용 OkHttpClient:**
```java
private static final OkHttpClient streamingClient = new OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS)  // 스트리밍용
    .writeTimeout(30, TimeUnit.SECONDS)
    .build();
```

#### 기존 메서드 유지 (폴백)

`analyzeSentenceWithCache()`는 그대로 유지하여, 스트리밍 실패 시 폴백으로 사용.

### 3. GeminiFeedbackManager 수정 (폴백용)

**파일**: `app/src/main/java/com/example/test/GeminiFeedbackManager.java`

동일한 `StreamingFeedbackCallback` 지원 메서드 추가. 캐시 미사용 경로에서도 스트리밍 가능하도록.

### 4. SentenceFeedbackBinder 수정

**파일**: `app/src/main/java/com/example/test/SentenceFeedbackBinder.java`

#### 섹션별 가시성 제어 추가

```java
// 모든 섹션을 GONE으로 초기화
public void hideAllSections() {
    setSectionVisibility("writingScore", View.GONE);
    setSectionVisibility("grammar", View.GONE);
    setSectionVisibility("conceptualBridge", View.GONE);
    setSectionVisibility("naturalness", View.GONE);
    setSectionVisibility("toneStyle", View.GONE);
    setSectionVisibility("paraphrasing", View.GONE);
}

// 특정 섹션에 데이터 바인딩 + VISIBLE 전환
public void bindSection(String sectionName, SentenceFeedback partialFeedback) {
    this.feedback = partialFeedback;
    switch (sectionName) {
        case "writingScore":
            bindWritingScore();
            setSectionVisibility("writingScore", View.VISIBLE);
            break;
        case "grammar":
            bindGrammar();
            setSectionVisibility("grammar", View.VISIBLE);
            break;
        // ... 나머지 섹션
    }
}
```

#### 섹션별 View 그룹 매핑

각 섹션의 라벨 + 카드를 그룹으로 관리:

```java
private void setSectionVisibility(String section, int visibility) {
    switch (section) {
        case "writingScore":
            setViewVisibility(R.id.tv_writing_score_label, visibility);
            setViewVisibility(R.id.tv_writing_score, visibility);
            setViewVisibility(R.id.tv_writing_score_max, visibility);
            setViewVisibility(R.id.tv_encouragement_message_sentence, visibility);
            break;
        case "grammar":
            setViewVisibility(R.id.tv_grammar_label, visibility);
            setViewVisibility(R.id.card_grammar, visibility);
            break;
        // ... 나머지 섹션
    }
}
```

### 5. ScriptChatFragment 수정

**파일**: `app/src/main/java/com/example/test/ScriptChatFragment.java`

#### 스트리밍 분석 호출 (텍스트 입력 경로: `showFeedbackContent`)

```java
// 기존: analyzeSentenceWithCache → onSuccess(feedback) → 한 번에 바인딩
// 변경: analyzeSentenceStreaming → onSectionReady(섹션마다) → 점진적 바인딩

cachedFeedbackManager.analyzeSentenceStreaming(sentenceToTranslate, translatedSentence,
    new StreamingFeedbackCallback() {
        @Override
        public void onSectionReady(String sectionName, Object sectionData) {
            // 첫 번째 섹션 도착 시: 스켈레톤 숨기고 실제 레이아웃 표시
            if (skeletonLayout != null) skeletonLayout.setVisibility(View.GONE);
            layoutSentenceFeedback.setVisibility(View.VISIBLE);

            // 해당 섹션만 바인딩 + 표시
            binder.bindSection(sectionName, partialFeedback);
        }

        @Override
        public void onComplete(SentenceFeedback feedback) {
            // 버튼 표시 등 최종 처리
            layoutBtns.setVisibility(View.VISIBLE);
            binder.setupTtsListeners(); // TTS 리스너 최종 설정
        }

        @Override
        public void onError(String error) {
            skeletonLayout.setVisibility(View.GONE);
        }
    });
```

#### 스트리밍 분석 호출 (음성 녹음 경로: `startSentenceFeedbackWithText`)

동일한 패턴으로 스트리밍 콜백 적용.

### 6. bottom_sheet_content_feedback.xml 수정

**파일**: `app/src/main/res/layout/bottom_sheet_content_feedback.xml`

`layout_sentence_feedback` 내부의 각 섹션별 라벨/카드를 초기 `visibility="gone"`으로 설정:
- `tv_writing_score_label`, 점수 LinearLayout, `tv_encouragement_message_sentence`
- `tv_grammar_label`, `card_grammar`
- `tv_conceptual_bridge_label`, `card_conceptual_bridge`
- `tv_naturalness_label`, `card_naturalness`
- `tv_tone_style_label`, `card_tone_style`
- `tv_paraphrasing_label`, `card_paraphrasing`

이렇게 하면 `layout_sentence_feedback`을 VISIBLE로 설정해도 실제 내용은 안 보이고, `bindSection()`이 호출될 때마다 해당 카드만 나타남.

---

## 흐름 다이어그램

```
[녹음/텍스트 입력 완료]
        ↓
[스켈레톤 표시 (기존 구현)]
        ↓
[streamGenerateContent SSE 스트림 시작]
        ↓
[텍스트 청크 수신] → [IncrementalJsonSectionParser에 전달]
        ↓
[writingScore 완성 감지]
        ↓
[스켈레톤 GONE → layout_sentence_feedback VISIBLE]
[writingScore 섹션만 VISIBLE + 데이터 바인딩]
        ↓
[grammar 완성 감지]
[grammar 섹션 VISIBLE + 데이터 바인딩]
        ↓
  ... (conceptualBridge, naturalness, toneStyle, paraphrasing 순차)
        ↓
[스트림 종료 → onComplete]
[버튼 표시, TTS 리스너 최종 설정]
```

---

## 검증 방법

1. 빌드: `./gradlew assembleDebug`
2. 텍스트 입력 후 피드백 요청 시:
   - 스켈레톤이 먼저 표시됨
   - 첫 번째 섹션(writingScore) 도착 시 스켈레톤이 사라지고 점수가 나타남
   - 이후 grammar, conceptualBridge 등 순차적으로 나타남
3. 음성 녹음 후 피드백에서도 동일하게 점진적 렌더링 확인
4. 네트워크 에러 시 스켈레톤이 정상적으로 숨겨지는지 확인
5. 스트리밍 실패 시 기존 일괄 응답 방식으로 폴백되는지 확인
