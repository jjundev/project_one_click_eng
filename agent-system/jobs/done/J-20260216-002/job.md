# Job: J-20260216-002 - Main Navigation Empty Fragment Wiring

## Goal
- MainActivity 하단 Navigation을 5개 탭(영어학습 쇼츠, 게임 모드, 학습 모드, 학습한 내용 기록, 설정)으로 정렬한다.
- `학습 모드`를 기본 탭(start destination)으로 유지하고, 미구현 탭 4개는 Empty Fragment로 연결해 앱이 안전하게 전환되도록 한다.

## Scope
### Include
- [ ] `MainActivity` + bottom navigation 메뉴를 5개 항목 구조로 정렬
- [ ] `학습 모드` 기본 진입이 `LearningModeSelectFragment`인지 보장
- [ ] 미구현 4개 탭용 Empty Fragment 클래스/레이아웃 생성 및 nav graph 연결
- [ ] 탭 전환 시 크래시 없이 각 목적지로 이동되는지 검증 로그 반영

### Exclude
- [ ] `LearningModeSelectFragment` 내부 학습 플로우(대본 선택/학습 실행) 로직 변경
- [ ] Empty Fragment 내 실제 기능 구현(데이터 연동/비즈니스 로직)
- [ ] 디자인 시스템 전면 개편

## Acceptance Criteria (PASS 조건)
- [ ] AC1: 하단 Navigation에 5개 탭이 노출되고, 라벨이 요청 명칭과 일치한다.
- [ ] AC2: 앱 시작 시 기본 탭은 `학습 모드`이며 첫 화면은 `LearningModeSelectFragment`다.
- [ ] AC3: `학습 모드` 제외 4개 탭 선택 시 각 Empty Fragment로 이동하고 앱이 크래시하지 않는다.
- [ ] AC4: Empty Fragment는 "준비 중" 상태를 사용자에게 명확히 보여준다.

## Implementation Notes
- 기존 구조/참고 파일:
  - `repo/app/src/main/java/com/example/test/activity/MainActivity.java`
  - `repo/app/src/main/res/menu/menu_main_bottom_nav.xml`
  - `repo/app/src/main/res/navigation/nav_graph.xml`
  - `repo/app/src/main/java/com/example/test/fragment/LearningModeSelectFragment.java`
- 금지사항(예: 특정 화면 변경 금지):
  - `DialogueLearningFragment` 및 요약/학습 핵심 비즈니스 로직 변경 금지
- 롤백 아이디어(한 줄):
  - nav/menu 변경과 신규 Empty Fragment 파일을 되돌리고 기존 3탭 구성으로 복원

## Build Gate (반드시 실행)
- Command:
  - `.\gradlew.bat :app:spotlessApply :app:spotlessCheck :app:assembleDebug :app:testDebugUnitTest`
- PASS 기준:
  - [ ] spotlessApply 성공
  - [ ] spotlessCheck 성공
  - [ ] compile 성공
  - [ ] assemble/build 성공
  - [ ] (옵션) unit test 성공

## Logging
- Tag: `JOB_J-20260216-002`
- Rule:
  - DEBUG 가드 필수 (`if (BuildConfig.DEBUG)`)
  - 민감정보 로깅 금지(마스킹 필수)

## QA Checklist
### UX
- [ ] 시나리오 1: 앱 실행 직후 `학습 모드` 탭이 기본 선택되고 `LearningModeSelectFragment`가 보인다.
- [ ] 시나리오 2: `영어학습 쇼츠`, `게임 모드`, `학습한 내용 기록`, `설정` 탭 각각 진입 시 Empty 상태 문구가 정상 노출된다.
- [ ] 시나리오 3: 5개 탭을 연속 전환해도 화면 멈춤/크래시가 없다.

### Logcat
- Filter:
  - `adb logcat | grep JOB_J-20260216-002`
- [ ] 기대 로그 1: 탭 선택 로그가 각 메뉴 ID로 출력된다.
- [ ] 기대 로그 2: 목적지 전환 로그가 각 Empty Fragment destination ID로 출력된다.

## Risk
- Level: Medium
- Rollback idea (1줄): nav_graph/menu_main_bottom_nav/MainActivity와 신규 fragment 파일만 선택 롤백
