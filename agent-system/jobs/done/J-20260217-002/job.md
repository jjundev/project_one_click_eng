# Job: J-20260217-002 - Replace DialogueSummaryFragment flow with DialogueQuizFragment

## Goal
- Switch post-learning destination from `DialogueSummaryFragment` to `DialogueQuizFragment`.
- Present review quiz questions generated from summary signals (`새롭게 발견한 표현`, `새롭게 발견한 단어`) and let users solve up to 5 questions.

## Scope
### Include
- [ ] Add `DialogueQuizFragment` (and supporting ViewModel/binder/layout as needed) for quiz solving UX.
- [ ] Change learning completion navigation path from summary screen entry to quiz screen entry.
- [ ] Trigger quiz generation API using summary-derived seeds and render returned questions/answers.
- [ ] Implement quiz progression UI (question index/progress, submit/check, next/finish) for 최대 5문제.
- [ ] Handle failure/empty states (retry or graceful fallback) without app crash.
- [ ] Update activity back behavior to support quiz fragment stack transitions.

### Exclude
- [ ] 장기 학습 이력/통계 저장 기능 추가.
- [ ] 서버 스키마 변경 또는 외부 신규 API provider 도입.
- [ ] Summary 전 섹션의 콘텐츠 생성 알고리즘 변경(하이라이트/표현/단어 생성 규칙 변경 금지).

## Acceptance Criteria (PASS 조건)
- [ ] AC1: 학습 종료 후 기존 Summary 진입 대신 `DialogueQuizFragment`로 전환된다.
- [ ] AC2: Quiz 화면에서 summary 기반 API 호출이 수행되고 문제/정답 데이터가 로딩된다.
- [ ] AC3: 사용자에게 표시되는 문항은 최대 5개이며, 초과 데이터는 노출되지 않는다.
- [ ] AC4: 문제 풀이 진행(정답 확인/다음 문제/완료) 흐름이 끊김 없이 동작한다.
- [ ] AC5: 뒤로가기/종료 동작이 기존 학습 액티비티 플로우를 깨지 않는다.
- [ ] AC6: Build gate passes without formatting regression.

## Implementation Notes
- 기존 구조/참고 파일:
  - `repo/app/src/main/java/com/example/test/fragment/DialogueLearningFragment.java`
  - `repo/app/src/main/java/com/example/test/activity/DialogueLearningActivity.java`
  - `repo/app/src/main/java/com/example/test/fragment/DialogueSummaryFragment.java`
  - `repo/app/src/main/java/com/example/test/fragment/DialogueSummaryViewModel.java`
  - `repo/app/src/main/res/layout/fragment_dialogue_summary.xml`
  - `repo/app/src/main/res/layout/activity_dialogue_learning.xml`
- 선행 의존:
  - `J-20260217-001` 완료(quiz API contract/manager 준비) 후 진행
- 금지사항(예: 특정 화면 변경 금지):
  - 영어 쇼츠/설정/다른 탭 기능 변경 금지
  - 기존 Dialogue 학습 턴 처리 로직 변경 최소화(네비게이션 경계만 수정)
  - 민감정보 로그 금지
- 롤백 아이디어(한 줄):
  - 네비게이션 대상과 신규 quiz fragment 관련 변경을 revert해 기존 summary 진입으로 복귀

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
- Tag: `JOB_J-20260217-002`
- Rule:
  - DEBUG 가드 필수 (`if (BuildConfig.DEBUG)`)
  - 민감정보 로깅 금지(마스킹 필수)

## QA Checklist
### UX
- [ ] 시나리오 1: 학습 완료 후 Quiz 화면으로 이동하고 첫 문제가 표시된다.
- [ ] 시나리오 2: 정답 확인 후 다음 문제로 진행 가능하며 진행률이 업데이트된다.
- [ ] 시나리오 3: 문제 수는 최대 5개로 제한되고 마지막 문제 이후 완료 처리된다.
- [ ] 시나리오 4: API 실패/빈 응답에서도 오류 UI 또는 재시도 UI가 표시되고 크래시가 없다.
- [ ] 시나리오 5: 뒤로가기 동작 시 학습 화면 스택 전환이 정상 동작한다.

### Logcat
- Filter:
  - `adb logcat | grep JOB_J-20260217-002`
- [ ] 기대 로그 1: quiz 화면 진입 및 로드 시작 이벤트 로그
- [ ] 기대 로그 2: 문제 인덱스 전환/완료 또는 실패 이벤트 로그

## Risk
- Level: High
- Rollback idea (1줄): quiz fragment 도입부를 제거하고 summary fragment 경로로 되돌림
