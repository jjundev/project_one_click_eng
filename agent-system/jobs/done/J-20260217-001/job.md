# Job: J-20260217-001 - Define Dialogue Quiz API schema and manager

## Goal
- Create a quiz-generation API contract that uses summary outputs (`새롭게 발견한 표현`, `새롭게 발견한 단어`) as input.
- Return normalized quiz payloads (question + answer 중심) for downstream Quiz UI consumption with a hard cap of 5 questions.

## Scope
### Include
- [ ] Define quiz domain model(s) for request/response (e.g., question, choices(optional), answer, explanation(optional)).
- [ ] Build request schema from `SummaryData.expressions` + `SummaryData.words` seed data.
- [ ] Add new LLM/API manager + manager contract for quiz generation using current app API-key/model wiring.
- [ ] Implement response parsing/sanitization and enforce max `5` questions.
- [ ] Integrate dependency provider entrypoint for quiz manager injection.

### Exclude
- [ ] Fragment navigation/UI rendering changes.
- [ ] Quiz result persistence/history DB schema changes.
- [ ] Recommendation/personalization algorithms beyond request seed assembly.

## Acceptance Criteria (PASS 조건)
- [ ] AC1: Summary 기반 seed(`표현/단어`)로 quiz generation request payload가 구성된다.
- [ ] AC2: API 응답 파싱 후 유효한 문제 리스트가 생성되며 최대 5개를 넘지 않는다.
- [ ] AC3: 응답 이상/파싱 실패 시 명시적 error callback 경로가 동작하고 크래시가 없다.
- [ ] AC4: 신규 manager contract/provider 경유로 컴파일 에러 없이 연결된다.
- [ ] AC5: Build gate passes without formatting regression.

## Implementation Notes
- 기존 구조/참고 파일:
  - `repo/app/src/main/java/com/example/test/summary/SessionSummaryLlmManager.java`
  - `repo/app/src/main/java/com/example/test/fragment/dialoguelearning/manager_contracts/ISessionSummaryLlmManager.java`
  - `repo/app/src/main/java/com/example/test/fragment/dialoguelearning/di/LearningDependencyProvider.java`
  - `repo/app/src/main/java/com/example/test/fragment/dialoguelearning/model/SummaryData.java`
  - `repo/app/src/main/java/com/example/test/fragment/DialogueSummaryViewModel.java`
- 금지사항(예: 특정 화면 변경 금지):
  - UI 화면 전환/레이아웃 변경은 본 Job 범위에서 제외
  - 기존 Summary API 동작 회귀 금지
  - 민감정보(API key/원문 개인정보) 로그 출력 금지
- 롤백 아이디어(한 줄):
  - quiz manager/contract 신규 파일 및 provider wiring만 부분 revert하여 기존 summary 경로 복원

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
- Tag: `JOB_J-20260217-001`
- Rule:
  - DEBUG 가드 필수 (`if (BuildConfig.DEBUG)`)
  - 민감정보 로깅 금지(마스킹 필수)

## QA Checklist
### UX
- [ ] 시나리오 1: summary seed가 있는 세션에서 quiz generation 요청이 정상 시작된다.
- [ ] 시나리오 2: 문제 수가 5개를 초과하는 응답에서도 최종 결과는 5개 이하로 제한된다.
- [ ] 시나리오 3: 비정상 응답(JSON shape mismatch) 시 에러 경로가 동작하고 앱이 유지된다.
- [ ] 시나리오 4: 기존 summary 관련 API 호출(미래 피드백/단어 추출)이 회귀 없이 동작한다.

### Logcat
- Filter:
  - `adb logcat | grep JOB_J-20260217-001`
- [ ] 기대 로그 1: request seed 개수(표현/단어, 민감정보 제외) 및 quiz 요청 시작 로그
- [ ] 기대 로그 2: 응답 파싱 결과(문항 수 cap 적용 여부) 또는 오류 로그

## Risk
- Level: Medium
- Rollback idea (1줄): 신규 quiz manager 계층을 제거하고 provider 바인딩만 원복
