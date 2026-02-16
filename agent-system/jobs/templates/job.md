# Job: J-YYYYMMDD-001 - <title>

## Goal
- (1~3줄) 이 작업이 무엇이며 왜 필요한지

## Scope
### Include
- [ ] ...

### Exclude
- [ ] ...

## Acceptance Criteria (PASS 조건)
- [ ] AC1: ...
- [ ] AC2: ...

## Implementation Notes
- 기존 구조/참고 파일:
- 금지사항(예: 특정 화면 변경 금지):
- 롤백 아이디어(한 줄):

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
- Tag: `JOB_J-YYYYMMDD-001`
- Rule:
  - DEBUG 가드 필수 (`if (BuildConfig.DEBUG)`)
  - 민감정보 로깅 금지(마스킹 필수)

## QA Checklist
### UX
- [ ] 시나리오 1: ...
- [ ] 시나리오 2: ...

### Logcat
- Filter:
  - `adb logcat | grep JOB_J-YYYYMMDD-001`
- [ ] 기대 로그 1: ...
- [ ] 기대 로그 2: ...

## Risk
- Level: Low / Medium / High
- Rollback idea (1줄): ...
