# Job: J-20260216-001 - MainActivity BottomNavigation Fragment 전환 추가

## Goal
- MainActivity에 BottomNavigation을 추가해 하단 탭으로 여러 Fragment 화면을 전환할 수 있도록 한다.
- 기존 단일 진입 구조를 탭 기반 구조로 확장해 주요 화면 접근성을 개선한다.

## Scope
### Include
- [ ] `activity_main.xml`에 Fragment 컨테이너와 BottomNavigationView 배치
- [ ] 탭 전환용 Fragment 최소 3개 구성(기존 화면 재사용 우선)
- [ ] MainActivity에서 탭 선택 시 Fragment 전환 로직 구현
- [ ] 탭 선택/전환 로그를 DEBUG 가드 하에 추가

### Exclude
- [ ] 각 Fragment의 상세 비즈니스 로직 변경
- [ ] 네트워크/데이터 계층 구조 변경
- [ ] 신규 API 연동

## Acceptance Criteria (PASS 조건)
- [ ] AC1: 앱 실행 시 MainActivity 하단에 BottomNavigation이 표시된다.
- [ ] AC2: 탭 클릭 시 대응 Fragment로 즉시 전환되고 앱이 크래시하지 않는다.
- [ ] AC3: 화면 회전 또는 재진입 후에도 선택 탭 상태가 비정상 초기화되지 않는다.

## Implementation Notes
- 기존 구조/참고 파일:
  - `repo/app/src/main/java/com/example/test/activity/MainActivity.java`
  - `repo/app/src/main/res/layout/activity_main.xml`
  - `repo/app/src/main/res/navigation/nav_graph.xml`
- 금지사항(예: 특정 화면 변경 금지): DialogueLearning 관련 Fragment 내부 로직 수정 금지
- 롤백 아이디어(한 줄): MainActivity와 activity_main.xml을 기존 단일 레이아웃/초기화 방식으로 되돌린다.

## Build Gate (반드시 실행)
- Command:
  - `./gradlew :app:assembleDebug testDebugUnitTest`
- PASS 기준:
  - [ ] compile 성공
  - [ ] assemble/build 성공
  - [ ] (옵션) unit test 성공

## Logging
- Tag: `JOB_J-20260216-001`
- Rule:
  - DEBUG 가드 필수 (`if (BuildConfig.DEBUG)`)
  - 민감정보 로깅 금지(마스킹 필수)

## QA Checklist
### UX
- [ ] 시나리오 1: 앱 실행 후 BottomNavigation의 기본 선택 탭과 해당 Fragment가 정상 표시된다.
- [ ] 시나리오 2: 모든 탭을 1회 이상 왕복 전환해도 화면 깨짐/중복 표시/크래시가 없다.
- [ ] 시나리오 3: 백그라운드 전환 후 복귀 시 현재 탭과 화면이 일치한다.

### Logcat
- Filter:
  - `adb logcat | grep JOB_J-20260216-001`
- [ ] 기대 로그 1: 탭 선택 시 선택 탭 id 또는 name 로그 출력
- [ ] 기대 로그 2: Fragment 전환 완료 로그 출력

## Risk
- Level: Medium
- Rollback idea (1줄): MainActivity의 BottomNavigation wiring 제거 후 기존 초기 화면 바인딩으로 복원.
