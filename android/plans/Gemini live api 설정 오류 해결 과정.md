# Gemini Live API 구현 분석 보고서

## 1. 실패 원인 분석 (Why the previous attempt failed)

기존 [GeminiLiveApiManager.java](file:///E:/AndroidLab/project_one_click_eng/android/app/src/main/java/com/example/test/GeminiLiveApiManager.java) 코드에서 `Gemini-2.5-Flash-Native-Audio` 모델과의 WebSocket 연결이 실패하거나 정상 동작하지 않았을 가능성이 높은 주요 원인은 다음과 같습니다:

1.  **잘못된 [setup](file:///E:/AndroidLab/project_one_click_eng/android/app/src/main/java/com/example/test/ScriptChatFragment.java#123-180) 메시지 포맷**:
    -   기존 코드에서는 [setup](file:///E:/AndroidLab/project_one_click_eng/android/app/src/main/java/com/example/test/ScriptChatFragment.java#123-180) 메시지 내에 `inputAudioTranscription` 및 `outputAudioTranscription` 필드를 포함했습니다.
    -   그러나 제공된 [gemini_audio_live_api_guide.md](file:///E:/AndroidLab/project_one_click_eng/android/gemini_audio_live_api_guide.md) 가이드에 따르면, Native Audio 모델의 [setup](file:///E:/AndroidLab/project_one_click_eng/android/app/src/main/java/com/example/test/ScriptChatFragment.java#123-180) 메시지는 `model`, `generationConfig`, `systemInstruction` 필드만을 요구합니다.
    -   API 서버가 정의되지 않은 추가 필드를 수신했을 때, 이를 처리하지 못하고 연결을 종료했을 가능성이 높습니다. 특히 `v1alpha` 버전의 API는 스키마를 엄격하게 검증할 수 있습니다.

2.  **모델 식별자 처리**:
    -   기존 코드에서는 모델 ID가 `"gemini-2.5-flash-native-audio-preview-12-2025"`로 설정되고 `"models/"` 접두사를 붙여 전송했으나, 이 과정에서 변수가 혼용되거나 잘못된 문자열이 전송될 여지가 있었습니다.
    -   새로운 구현에서는 가이드에 명시된 `"models/gemini-2.5-flash-native-audio-preview-12-2025"` 포맷을 명확하고 안전하게 생성하도록 변경했습니다.

3.  **메시지 파싱 로직**:
    -   기존 [handleMessage](file:///E:/AndroidLab/project_one_click_eng/android/app/src/main/java/com/example/test/GeminiLiveApiManager.java#362-431)는 `inputTranscription`과 `outputTranscription`을 기대하고 처리하려 했지만, Native Audio 모델의 응답 구조는 `serverContent` -> `modelTurn` -> `parts` 내의 `text`와 `inlineData` (오디오)에 집중되어 있습니다.
    -   불필요한 필드 파싱 시도로 인한 잠재적 오류나 예외가 발생했을 수 있습니다.

## 2. 변경된 구조 및 개선 사항 (Changes and Improvements)

새롭게 구현된 [GeminiLiveApiManager.java](file:///E:/AndroidLab/project_one_click_eng/android/app/src/main/java/com/example/test/GeminiLiveApiManager.java)는 가이드 문서를 철저히 준수하도록 다음과 같이 변경되었습니다:

### A. 네트워크 계층 (Strict Protocol Compliance)
-   **간소화된 Setup 메시지**: `inputAudioTranscription` 등의 실험적이거나 불확실한 필드를 제거하고, 가이드에서 요구하는 필수 필드(`generationConfig` 내 `responseModalities`, `speechConfig`)만 정확히 구성하여 전송합니다.
-   **명확한 JSON 구조**: `Gson`을 활용하여 수동으로 JSON 객체를 구성하는 과정에서 가이드의 예시 구조와 1:1로 매핑되도록 코드를 재작성했습니다.

### B. 메시지 처리 (Robust Message Handling)
-   **응답 파싱 최적화**: 서버로부터 수신되는 메시지 중 `serverContent` 내의 `modelTurn`에 집중하도록 로직을 단순화했습니다.
    -   `text`: 텍스트 트랜스크립트 수신 시 즉시 UI 업데이트 콜백 호출.
    -   `inlineData`: 오디오 데이터 수신 시 `Base64` 디코딩 후 즉시 재생 콜백 호출.
-   **상태 관리**: `setupComplete` 메시지(또는 첫 유효 콘텐츠) 수신 시 연결 상태를 명확히 `true`로 설정하여 안정적인 세션 시작을 보장합니다.

### C. 오디오 데이터 전송 (Realtime Input)
-   **정확한 청크 포맷**: `realtimeInput` 메시지 내 `mediaChunks` 배열 구조를 가이드에 명시된 대로 정확히 구현했습니다.
    -   MIME 타입: `audio/pcm;rate=16000`
    -   데이터: Base64 인코딩된 PCM 청크

이러한 변경을 통해 불필요한 오류 가능성을 제거하고, Gemini Live API 서버와의 호환성을 극대화했습니다.

