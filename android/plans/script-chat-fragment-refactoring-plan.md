# ScriptChatFragment 리팩토링 계획안

> 작성일: 2026-02-11
> 대상 파일: `ScriptChatFragment.java` (2,115줄)

---

## 1. 현황 분석

### 1.1 현재 ScriptChatFragment가 담당하는 책임 (7개)

| # | 책임 영역 | 관련 라인 수 (추정) | 설명 |
|---|----------|-------------------|------|
| 1 | **채팅 UI 관리** | ~200줄 | RecyclerView, ChatAdapter, ChatMessage, 메시지 추가/스켈레톤 |
| 2 | **BottomSheet 관리** | ~150줄 | setup, 콘텐츠 교체 애니메이션, 높이 계산, 슬라이드 콜백 |
| 3 | **오디오 녹음 흐름** | ~250줄 | AudioRecorderManager 제어, Waveform, 권한 요청, 4단계 UI 상태 전환 |
| 4 | **TTS / 오디오 재생** | ~150줄 | TTS 초기화, playTts, playScriptTts (Watchdog), RecordingAudioPlayer |
| 5 | **Gemini API 피드백** | ~300줄 | Fluency 분석, Sentence Feedback 스트리밍, Extra AI 질문, 캐시 초기화 |
| 6 | **스크립트 진행 관리** | ~200줄 | JSON 파싱, 턴 기반 진행(processNextScriptStep), 시퀀스 처리 |
| 7 | **BottomSheet 콘텐츠별 UI** | ~500줄 | showDefaultContent, showBeforeSpeaking, showWhileSpeaking, showFeedbackContent 등 |

### 1.2 핵심 문제점

1. **God Fragment**: 한 클래스가 7가지 독립된 책임을 모두 수행
2. **상태 폭발**: `isSpeaking`, `isWaitingForAnalysis`, `isSentenceAnalysisRunning`, `isAutoScrollingToBottom`, `hasScrolledToSentenceFeedback` 등 플래그가 Fragment 전역에 산재
3. **내부 클래스 의존**: `ChatAdapter`, `ChatMessage`, `ScriptItem`이 Fragment 내부에 정의되어 재사용 불가
4. **테스트 불가**: 비즈니스 로직이 Android View에 직접 결합되어 단위 테스트 작성 불가
5. **메모리 누수 위험**: `Handler` + `Runnable` 콜백 체인이 Fragment 라이프사이클과 분리되지 않음

---

## 2. 리팩토링 전략

### 2.1 아키텍처 방향

**현재**: Fragment = UI + 로직 + 상태 (MVC 혼재)
**목표**: Fragment는 순수 UI 바인딩만 담당, 로직과 상태는 분리된 클래스로 이동

```
[Before]
ScriptChatFragment (2,115줄)
  ├── 채팅 UI
  ├── BottomSheet UI
  ├── 녹음 UI & 제어
  ├── TTS 제어
  ├── Gemini API 호출
  ├── 스크립트 진행 상태
  └── 피드백 표시 로직

[After]
ScriptChatFragment (~400줄)          ← UI 바인딩 & 이벤트 위임만
  ├── ScriptFlowController           ← 스크립트 진행 상태 머신
  ├── BottomSheetContentManager      ← BottomSheet 콘텐츠 전환 관리
  ├── AudioPlaybackController        ← TTS + 녹음 재생 통합
  ├── ChatMessageManager             ← 채팅 메시지 CRUD
  └── model/
      ├── ChatMessage.java           ← 독립 모델 클래스로 추출
      └── ScriptItem.java            ← 독립 모델 클래스로 추출
```

### 2.2 ViewModel 도입 여부

현재 프로젝트에 ViewModel이 사용되지 않고 있으므로, **급격한 아키텍처 전환보다는 단계적 분리를 우선**한다. 각 분리된 클래스는 나중에 ViewModel로 마이그레이션하기 용이한 형태로 설계한다.

---

## 3. 단계별 실행 계획

### Phase 1: 모델 클래스 추출 (안전, 사이드이펙트 없음)

> 내부 클래스를 독립 파일로 추출하여 재사용성 확보

#### 1-1. `ChatMessage` 클래스 추출
- **위치**: `ScriptChatFragment.ChatMessage` → `model/ChatMessage.java`
- **내용**: TYPE 상수, 필드, 생성자, getter를 그대로 이동
- **영향**: `ChatAdapter`에서의 참조 경로만 변경

#### 1-2. `ScriptItem` 클래스 추출
- **위치**: `ScriptChatFragment.ScriptItem` → `model/ScriptItem.java`
- **내용**: 이미 `ko`, `en`, `role` 필드가 구현되어 있으므로, 내부 클래스를 그대로 독립 파일로 이동
- **영향**: `parseScriptData()`, `processNextScriptStep()`의 참조 경로만 변경

#### 1-3. `ChatAdapter` 추출
- **위치**: `ScriptChatFragment.ChatAdapter` → `ChatAdapter.java`
- **내용**: 현재 inner class를 top-level로 이동
- **변경점**: `playTts()`, `playRecordedAudio()` 호출을 콜백 인터페이스로 대체
  ```java
  public interface ChatActionListener {
      void onPlayTts(String text, ImageView speakerBtn);
      void onPlayRecordedAudio(byte[] audioData, ImageView speakerBtn);
  }
  ```

### Phase 2: AudioPlaybackController 분리

> TTS 초기화/재생과 녹음 재생을 하나의 컨트롤러로 통합

#### 2-1. 새 클래스: `AudioPlaybackController`

**이동 대상 필드/메서드**:
| 출처 (ScriptChatFragment) | 대상 (AudioPlaybackController) |
|---------------------------|-------------------------------|
| `tts`, `isTtsReady` | 내부 상태 |
| `recordingAudioPlayer` | 내부 상태 |
| `currentlyPlayingSpeakerBtn` | 내부 상태 |
| `currentTtsOnDoneCallback`, `ttsWatchdogRunnable` | 내부 상태 |
| `playTts()` | `playTts(text, speakerBtn)` |
| `playRecordedAudio()` | `playRecordedAudio(audioData, speakerBtn)` |
| `playScriptTts()` | `playScriptTts(text, onComplete)` |
| `stopAllPlayback()` | `stopAll()` |
| `resetSpeakerButton()` | `resetCurrentSpeaker()` |

**인터페이스**:
```java
public class AudioPlaybackController {
    interface Callback {
        void onTtsReady();
        void onSpeakerReset(ImageView btn);
    }

    void init(Context context);
    void playTts(String text, ImageView speakerBtn);
    void playRecordedAudio(byte[] data, ImageView speakerBtn);
    void playScriptTts(String text, Runnable onComplete);
    void stopAll();
    void release();  // onDestroyView에서 호출
}
```

### Phase 3: ScriptFlowController 분리

> 스크립트 진행 상태 머신과 턴 관리를 독립 클래스로 분리

#### 3-1. 새 클래스: `ScriptFlowController`

**이동 대상**:
| 출처 (ScriptChatFragment) | 대상 (ScriptFlowController) |
|--------------------------|----------------------------|
| `scriptItems`, `currentScriptIndex` | 내부 상태 |
| `topic`, `opponentName`, `opponentRole` | 내부 상태 |
| `parseScriptData()` | `parseScript(jsonString)` |
| `processNextScriptStep()` | `advanceToNext()` |
| 턴 판별 로직 (`isOpponentTurn`) | `getCurrentTurn()` |

**콜백 인터페이스** (Fragment가 구현):
```java
public class ScriptFlowController {
    interface FlowCallback {
        void onOpponentTurn(ScriptItem item);    // 상대방 차례
        void onUserTurn(ScriptItem item);        // 사용자 차례
        void onScriptComplete();                 // 학습 완료
        void onProgressUpdate(int current, int total);
        void onMetadataLoaded(String topic, String opponentName);
    }

    void loadScript(String json);
    void advance();
    ScriptItem getCurrentItem();
    int getProgress();
    int getTotalSteps();
}
```

### Phase 4: BottomSheetContentManager 분리

> BottomSheet의 다양한 콘텐츠 전환 로직을 관리

#### 4-1. 새 클래스: `BottomSheetContentManager`

**이동 대상**:
| 출처 (ScriptChatFragment) | 대상 (BottomSheetContentManager) |
|--------------------------|----------------------------------|
| `setBottomSheetContent()` | `replaceContent(layoutResId)` |
| `changeBottomSheetContent()` | `animateContentChange(action)` |
| `showDefaultContent()` | `showInput(sentenceToTranslate, listener)` |
| `showBeforeSpeakingContent()` | `showBeforeSpeaking(sentence, listener)` |
| `showWhileSpeakingContent()` | `showWhileSpeaking(sentence, listener)` |
| `showSpeakingFeedbackContent()` | `showSpeakingFeedback(result, ...)` |
| `showFeedbackContent()` | `showWritingFeedback(...)` |
| 애니메이션 관련 (`ripple`, `progress`) | 내부 관리 |

**핵심 이벤트 콜백**:
```java
public class BottomSheetContentManager {
    interface ContentActionListener {
        void onSendClicked(String sentenceToTranslate, String userInput);
        void onMicClicked(String sentenceToTranslate);
        void onRecordingStarted(String sentenceToTranslate);
        void onRecordingStopped(String sentenceToTranslate);
        void onRetryClicked(String sentenceToTranslate);
        void onNextClicked(String displayText, byte[] audioData);
    }
}
```

### Phase 5: ChatMessageManager 분리

> 채팅 메시지 관리 및 RecyclerView 조작 캡슐화

#### 5-1. 새 클래스: `ChatMessageManager`

**이동 대상**:
| 출처 (ScriptChatFragment) | 대상 (ChatMessageManager) |
|--------------------------|--------------------------|
| `messageList` | 내부 상태 |
| `addOpponentMessage()` | `addOpponentMessage(eng, ko)` |
| `addUserMessage()` | `addUserMessage(message, audioData)` |
| `addSkeletonMessage()` | `addSkeleton()` |
| `removeSkeletonMessage()` | `removeSkeleton()` |
| Footer 높이 관리 | `setFooterHeight(height)` |

---

## 4. 리팩토링 후 ScriptChatFragment 구조 (예상)

```java
public class ScriptChatFragment extends Fragment
        implements ScriptFlowController.FlowCallback,
                   BottomSheetContentManager.ContentActionListener {

    // Controllers
    private ScriptFlowController scriptFlowController;
    private BottomSheetContentManager bottomSheetManager;
    private AudioPlaybackController audioController;
    private ChatMessageManager chatManager;

    // Gemini Managers (기존 유지)
    private GeminiFluencyManager geminiFluencyManager;
    private GeminiCachedFeedbackManager cachedFeedbackManager;
    private GeminiExtraQuestionManager extraQuestionManager;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // 각 컨트롤러 초기화
        audioController = new AudioPlaybackController(requireContext(), ...);
        scriptFlowController = new ScriptFlowController(this);
        chatManager = new ChatMessageManager(recyclerViewMessages);
        bottomSheetManager = new BottomSheetContentManager(bottomSheet, this);

        // 스크립트 로드
        if (getArguments() != null) {
            scriptFlowController.loadScript(getArguments().getString("SCRIPT_DATA"));
        }
    }

    // --- ScriptFlowController.FlowCallback ---
    @Override
    public void onOpponentTurn(ScriptItem item) {
        bottomSheetManager.hide();
        chatManager.scheduleOpponentReply(item, () -> {
            audioController.playScriptTts(item.en, () -> scriptFlowController.advance());
        });
    }

    @Override
    public void onUserTurn(ScriptItem item) {
        bottomSheetManager.showAndExpand();
        bottomSheetManager.showInput(item.ko);
    }

    // --- BottomSheetContentManager.ContentActionListener ---
    @Override
    public void onSendClicked(String sentence, String userInput) {
        bottomSheetManager.showWritingFeedback(sentence, userInput);
    }

    @Override
    public void onNextClicked(String displayText, byte[] audioData) {
        chatManager.addUserMessage(displayText, audioData);
        scriptFlowController.advance();
    }

    // ... (각 이벤트에 대한 간단한 위임 코드)
}
```

---

## 5. 실행 우선순위 및 순서

| 순서 | Phase | 작업 | 위험도 | 이유 |
|------|-------|------|--------|------|
| 1 | Phase 1 | 모델 클래스 추출 | **낮음** | 순수 데이터 클래스 이동, 기능 변경 없음 |
| 2 | Phase 2 | AudioPlaybackController | **낮음** | TTS/오디오 로직이 비교적 독립적 |
| 3 | Phase 5 | ChatMessageManager | **낮음** | RecyclerView 조작이 명확하게 분리 가능 |
| 4 | Phase 3 | ScriptFlowController | **중간** | 상태 머신 로직이 UI와 얽혀있어 주의 필요 |
| 5 | Phase 4 | BottomSheetContentManager | **높음** | 가장 많은 UI 로직 포함, 콜백 체인 복잡 |

---

## 6. 주의사항

1. **각 Phase는 독립 커밋**: Phase 하나를 완료할 때마다 빌드 & 실행 테스트 후 커밋
2. **기존 동작 보존**: 리팩토링은 동작 변경이 아닌 구조 변경이므로, 기능이 달라지면 안 됨
3. **점진적 진행**: Phase 1~3까지만 해도 Fragment가 ~1,200줄로 줄어들어 충분히 관리 가능
4. **Gemini Manager는 현재 유지**: 이미 잘 분리되어 있으므로 추가 리팩토링 불필요
5. **ViewModel 마이그레이션**: 향후 필요 시 ScriptFlowController를 ViewModel 내부로 이동하면 자연스럽게 전환 가능

---

## 7. 예상 결과

| 지표 | 현재 | 리팩토링 후 |
|------|------|-----------|
| ScriptChatFragment 줄 수 | 2,115줄 | ~400줄 |
| 클래스 수 | 1개 (+ 3개 내부 클래스) | 6개 독립 클래스 |
| 책임 수 (Fragment) | 7개 | 1개 (UI 이벤트 위임) |
| 단위 테스트 가능 여부 | 불가 | ScriptFlowController, AudioPlaybackController 테스트 가능 |
| 새 기능 추가 시 수정 범위 | Fragment 전체 | 해당 Controller만 |
