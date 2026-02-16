# Job: J-20260216-003 - SettingFragment implementation for runtime configuration

## Goal
- Replace the empty settings screen with a functional `SettingFragment` that can configure mute behavior, LLM API/model settings (with test), and Android TTS settings.
- Provide timer-related UI placeholders only (no runtime enforcement yet).
- Handoff a decision-complete implementation scope to coding agent under Strict Serial workflow.

## Scope
### Include
- [ ] Rename navigation destination/tab id/class from `settingsEmptyFragment`/`SettingsEmptyFragment` to `settingFragment`/`SettingFragment`.
- [ ] Implement settings UI (`fragment_setting.xml`) with sections: mute, timer placeholder, LLM, TTS.
- [ ] Add `AppSettingsStore` and settings DTO using `SharedPreferences` (`app_settings`).
- [ ] Implement runtime API key override with fallback to `BuildConfig.GEMINI_API_KEY`.
- [ ] Implement per-feature LLM model selection (sentence/speaking/script/summary/extra).
- [ ] Add lightweight LLM connection test button in settings screen.
- [ ] Wire mute-all-playback to block TTS + recorded-audio playback in dialogue flow.
- [ ] Wire Android TTS settings (provider fixed to Android, speech rate, locale).
- [ ] Keep API-based TTS as disabled "coming soon" UI only.
- [ ] Update manager/provider constructors/interfaces to accept runtime model name and api key.

### Exclude
- [ ] Study timer runtime enforcement logic in learning input flow.
- [ ] API-based TTS request/execution implementation.
- [ ] New backend/service integration beyond existing Gemini REST usage.
- [ ] UI/flow refactors outside settings + required integration points.

## Acceptance Criteria (PASS 조건)
- [ ] AC1: Settings tab opens `SettingFragment` and persists changed values across app restart.
- [ ] AC2: Mute-all mode blocks both TTS playback and recorded audio playback in dialogue learning.
- [ ] AC3: LLM API key supports override value and fallback logic; missing override still uses BuildConfig key.
- [ ] AC4: LLM per-feature model selections are consumed by managers when learning/summary features initialize.
- [ ] AC5: "API test" executes one lightweight request and shows success/failure state to user.
- [ ] AC6: TTS settings (speech rate/locale) affect Android TextToSpeech playback behavior.
- [ ] AC7: Timer section is visible but clearly non-functional placeholder ("준비 중"/disabled state).
- [ ] AC8: Build gate passes with no formatting regression.

## Implementation Notes
- 기존 구조/참고 파일:
  - `repo/app/src/main/java/com/example/test/fragment/SettingsEmptyFragment.java`
  - `repo/app/src/main/res/layout/fragment_settings_empty.xml`
  - `repo/app/src/main/res/navigation/nav_graph.xml`
  - `repo/app/src/main/res/menu/menu_main_bottom_nav.xml`
  - `repo/app/src/main/java/com/example/test/fragment/DialogueLearningFragment.java`
  - `repo/app/src/main/java/com/example/test/fragment/dialoguelearning/di/LearningDependencyProvider.java`
  - `repo/app/src/main/java/com/example/test/manager_gemini/*.java`
  - `repo/app/src/main/java/com/example/test/summary/SessionSummaryLlmManager.java`
  - `repo/app/src/main/java/com/example/test/fragment/dialoguelearning/coordinator/DialoguePlaybackCoordinator.java`
- 금지사항(예: 특정 화면 변경 금지):
  - Settings와 직접 연동되지 않은 다른 기능 UX/레이아웃 대규모 변경 금지
  - 민감정보(API key) 로그 출력 금지
- 롤백 아이디어(한 줄):
  - `SettingFragment`/settings-store 추가분과 provider 시그니처 변경을 revert하고 `SettingsEmptyFragment` 경로로 복원

## Build Gate (반드시 실행)
- Command:
  - `./gradlew :app:spotlessApply :app:spotlessCheck :app:assembleDebug :app:testDebugUnitTest`
- PASS 기준:
  - [ ] spotlessApply 성공
  - [ ] spotlessCheck 성공
  - [ ] compile 성공
  - [ ] assemble/build 성공
  - [ ] (옵션) unit test 성공

## Logging
- Tag: `JOB_J-20260216-003`
- Rule:
  - DEBUG 가드 필수 (`if (BuildConfig.DEBUG)`)
  - 민감정보 로깅 금지(마스킹 필수)

## QA Checklist
### UX
- [ ] 시나리오 1: 설정 탭 진입 후 무음 모드 ON/OFF 전환 시 학습 화면 재생 동작이 즉시 반영된다.
- [ ] 시나리오 2: API key/모델 변경 후 API 테스트 버튼 결과가 성공/실패 상태로 표시된다.
- [ ] 시나리오 3: TTS 속도/로케일 변경 후 메시지 재생 음성이 변경값으로 동작한다.
- [ ] 시나리오 4: 타이머 설정은 표시되지만 비활성/준비중 상태임을 확인한다.

### Logcat
- Filter:
  - `adb logcat | grep JOB_J-20260216-003`
- [ ] 기대 로그 1: 설정 저장/적용 이벤트 로그가 DEBUG 가드 하에 출력된다.
- [ ] 기대 로그 2: mute 차단 또는 API 테스트 결과 로그가 민감정보 없이 출력된다.

## Risk
- Level: Medium
- Rollback idea (1줄): 설정 저장소 및 manager constructor 변경을 중심으로 커밋 단위 롤백
