# Job: J-20260217-003 - Restore Summary-first flow with optional Quiz branch

## Goal
- Change post-learning flow to `DialogueLearningFragment -> DialogueSummaryFragment` first.
- In `DialogueSummaryFragment`, let user choose either immediate finish or move to `DialogueQuizFragment`.
- Keep the existing quiz solving behavior while making Summary the decision point.

## Scope
### Include
- [ ] Adjust learning completion navigation to open `DialogueSummaryFragment` first.
- [ ] Add/enable a quiz-start CTA in `DialogueSummaryFragment` while keeping the existing finish CTA.
- [ ] Wire Summary -> Quiz navigation with required args (`summaryJson`, `featureBundleJson`).
- [ ] Update summary UI/resource bindings needed for dual actions.
- [ ] Verify/update back-stack behavior in `DialogueLearningActivity` for `Summary <-> Quiz` transitions.

### Exclude
- [ ] Quiz API contract/manager schema changes (`J-20260217-001` scope).
- [ ] Quiz scoring/history persistence or analytics expansion.
- [ ] Changes to non-dialogue screens (shorts/settings/home tabs).

## Acceptance Criteria (PASS 조건)
- [ ] AC1: 학습 완료 직후 진입 화면은 `DialogueSummaryFragment`다.
- [ ] AC2: Summary 화면에서 사용자는 `학습 종료`와 `퀴즈 진행` 중 하나를 선택할 수 있다.
- [ ] AC3: `학습 종료` 선택 시 기존 종료 동작이 유지되며 불필요한 퀴즈 호출이 없다.
- [ ] AC4: `퀴즈 진행` 선택 시 `DialogueQuizFragment`로 이동하고 기존 퀴즈 플로우가 정상 동작한다.
- [ ] AC5: 퀴즈 화면 뒤로가기 시 Summary로 복귀하고, Summary 뒤로가기는 기존 액티비티 규칙을 유지한다.
- [ ] AC6: Build gate passes without formatting regression.

## Implementation Notes
- 기존 구조/참고 파일:
  - `repo/app/src/main/java/com/example/test/fragment/DialogueLearningFragment.java`
  - `repo/app/src/main/java/com/example/test/fragment/DialogueSummaryFragment.java`
  - `repo/app/src/main/java/com/example/test/fragment/DialogueQuizFragment.java`
  - `repo/app/src/main/java/com/example/test/activity/DialogueLearningActivity.java`
  - `repo/app/src/main/res/layout/fragment_dialogue_summary.xml`
  - `repo/app/src/main/res/values/strings.xml`
- 금지사항(예: 특정 화면 변경 금지):
  - Summary 데이터 생성 로직/LLM 파싱 규칙 자체 변경 금지
  - 민감정보(API key, 개인 식별 가능 텍스트) 로그 출력 금지
  - Dialogue 기능 외 화면/네비게이션 변경 금지
- 롤백 아이디어(한 줄):
  - Summary의 quiz CTA와 네비게이션 분기만 되돌려 기존(직접 quiz 진입) 흐름으로 복원

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
- Tag: `JOB_J-20260217-003`
- Rule:
  - DEBUG 가드 필수 (`if (BuildConfig.DEBUG)`)
  - 민감정보 로깅 금지(마스킹 필수)

## QA Checklist
### UX
- [ ] 시나리오 1: 학습 완료 후 Summary 화면이 먼저 노출된다.
- [ ] 시나리오 2: Summary에서 `학습 종료` 선택 시 즉시 종료/복귀 동작이 정상 수행된다.
- [ ] 시나리오 3: Summary에서 `퀴즈 진행` 선택 시 Quiz 화면으로 이동하고 첫 문제가 표시된다.
- [ ] 시나리오 4: Quiz에서 뒤로가기 시 Summary로 돌아오며 상태 전환이 자연스럽다.
- [ ] 시나리오 5: Quiz API 실패/빈 응답에서도 오류 UI가 표시되고 크래시가 없다.

### Logcat
- Filter:
  - `adb logcat | grep JOB_J-20260217-003`
- [ ] 기대 로그 1: Summary 진입 및 사용자 액션(종료/퀴즈 선택) 이벤트 로그
- [ ] 기대 로그 2: Summary -> Quiz 네비게이션 또는 종료 분기 결과 로그

## Risk
- Level: Medium
- Rollback idea (1줄): Summary/Quiz 분기 추가분만 revert해 직전 release flow로 즉시 복귀
