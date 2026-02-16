# Job: J-20260216-004 - DialogueLearning settings dialog entry integration

## Goal
- Add a dedicated `DialogueLearningSettingDialog` entry flow from the toolbar right settings button in `DialogueLearningActivity`.
- Ensure the settings dialog can be opened safely from the learning screen without changing existing learning flow behavior.

## Scope
### Include
- [ ] Add a new `DialogueLearningSettingDialog` UI skeleton/dialog class for Dialogue Learning settings.
- [ ] Wire toolbar right-end settings button (`btn_right`) click in `DialogueLearningActivity` to open the dialog.
- [ ] Define dialog open/close behavior and basic lifecycle-safe show guard (duplicate show prevention).
- [ ] Add/adjust minimal resources required for the new dialog surface (layout/style/string if needed).

### Exclude
- [ ] Moving detailed mute/timer/TTS controls from `SettingFragment` (handled in next job).
- [ ] Behavioral changes to dialogue progression, summary flow, or back-stack policy.
- [ ] Settings persistence model/schema changes.

## Acceptance Criteria (PASS 조건)
- [ ] AC1: In `DialogueLearningActivity`, tapping toolbar `btn_right` opens `DialogueLearningSettingDialog`.
- [ ] AC2: Repeated fast taps do not create duplicated dialog instances or crash.
- [ ] AC3: Existing back navigation and learning flow remain unchanged.
- [ ] AC4: Build gate passes without formatting regression.

## Implementation Notes
- 기존 구조/참고 파일:
  - `repo/app/src/main/java/com/example/test/activity/DialogueLearningActivity.java`
  - `repo/app/src/main/res/layout/activity_dialogue_learning.xml`
  - `repo/app/src/main/java/com/example/test/dialog/ExitConfirmDialog.java`
  - `repo/app/src/main/java/com/example/test/fragment/DialogueLearningFragment.java`
- 금지사항(예: 특정 화면 변경 금지):
  - 학습 입력/피드백/요약 코디네이터 로직 변경 금지
  - 사용자 설정 값 저장 키 변경 금지
- 롤백 아이디어(한 줄):
  - `btn_right` 클릭 연결과 신규 다이얼로그 클래스를 제거해 기존 상태로 복귀

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
- Tag: `JOB_J-20260216-004`
- Rule:
  - DEBUG 가드 필수 (`if (BuildConfig.DEBUG)`)
  - 민감정보 로깅 금지(마스킹 필수)

## QA Checklist
### UX
- [ ] 시나리오 1: `DialogueLearningActivity` 진입 후 우측 세팅 버튼 탭 시 설정 다이얼로그가 정상 표시된다.
- [ ] 시나리오 2: 다이얼로그 닫은 뒤 다시 열기/빠른 연타 시에도 중복 표시나 크래시가 없다.

### Logcat
- Filter:
  - `adb logcat | grep JOB_J-20260216-004`
- [ ] 기대 로그 1: 다이얼로그 open/close 이벤트가 DEBUG 가드 하에 기록된다.
- [ ] 기대 로그 2: 예외 없이 정상 dismiss/재오픈이 확인된다.

## Risk
- Level: Low
- Rollback idea (1줄): 신규 다이얼로그 진입 코드만 커밋 단위로 revert
