# Job: J-20260216-006 - Build EnglishShortsFragment YouTube Shorts-like UI

## Goal
- Replace `EnglishShortsEmptyFragment` placeholder with an immersive `EnglishShortsFragment` UI that feels similar to YouTube Shorts.
- Deliver a swipe-first shorts screen shell that is visually complete enough for UX validation without backend/video service coupling.

## Scope
### Include
- [ ] Replace empty screen with a full-screen vertical shorts feed UI in the English Shorts tab.
- [ ] Implement core shorts visual structure: top progress indicator area, center media area, right action rail, bottom caption/metadata block.
- [ ] Provide local mock shorts items (at least 3) and bind vertical swipe navigation between items.
- [ ] Update fragment/layout/resource wiring while preserving bottom-nav destination compatibility (`englishShortsEmptyFragment` id may remain).
- [ ] Apply edge-to-edge safe spacing and basic transition polish consistent with current app style.

### Exclude
- [ ] YouTube API or external content fetching integration.
- [ ] Real streaming/download pipeline (ExoPlayer tuning, CDN, cache strategy).
- [ ] Backend persistence for like/bookmark/follow actions.
- [ ] Recommendation/ranking logic and analytics pipeline expansion.

## Acceptance Criteria (PASS 조건)
- [ ] AC1: `영어학습 쇼츠` 탭 진입 시 empty 화면이 아닌 Shorts 스타일 전체 화면 UI가 표시된다.
- [ ] AC2: 최소 3개의 mock shorts 항목이 세로 스와이프로 전환된다.
- [ ] AC3: 우측 액션 레일 + 하단 메타/자막 + 상단 진행 요소가 동시에 보이는 Shorts 패턴이 구현된다.
- [ ] AC4: 하단 탭 전환 및 기존 다른 탭 동작에 회귀(regression)가 없다.
- [ ] AC5: Build gate passes without formatting regression.

## Implementation Notes
- 기존 구조/참고 파일:
  - `repo/app/src/main/java/com/example/test/fragment/EnglishShortsEmptyFragment.java`
  - `repo/app/src/main/res/layout/fragment_english_shorts_empty.xml`
  - `repo/app/src/main/res/navigation/nav_graph.xml`
  - `repo/app/src/main/res/menu/menu_main_bottom_nav.xml`
  - `repo/app/src/main/java/com/example/test/activity/MainActivity.java`
- 금지사항(예: 특정 화면 변경 금지):
  - `studyModeSelectFragment`, `settingFragment`, `dialogue` 학습 플로우 로직 변경 금지
  - 민감정보/개인식별정보 로그 금지
  - 외부 SDK/신규 네트워크 권한 추가 금지 (UI shell 범위 유지)
- 롤백 아이디어(한 줄):
  - English Shorts 전용 fragment/layout/resource 변경만 부분 revert해 기존 empty 화면으로 즉시 복원

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
- Tag: `JOB_J-20260216-006`
- Rule:
  - DEBUG 가드 필수 (`if (BuildConfig.DEBUG)`)
  - 민감정보 로깅 금지(마스킹 필수)

## QA Checklist
### UX
- [ ] 시나리오 1: `영어학습 쇼츠` 탭 진입 시 Shorts 스타일 메인 카드가 즉시 렌더링된다.
- [ ] 시나리오 2: 위/아래 스와이프로 shorts 카드 전환이 자연스럽게 동작한다.
- [ ] 시나리오 3: 우측 액션 버튼 영역 탭(placeholder 포함) 시 앱이 크래시 없이 반응한다.
- [ ] 시나리오 4: 다른 하단 탭 이동 후 복귀 시 상태/레이아웃이 깨지지 않는다.

### Logcat
- Filter:
  - `adb logcat | grep JOB_J-20260216-006`
- [ ] 기대 로그 1: 탭 진입 및 초기 카드 렌더 이벤트가 DEBUG 가드 하에 출력된다.
- [ ] 기대 로그 2: 카드 전환(인덱스 변경) 이벤트가 민감정보 없이 출력된다.

## Risk
- Level: Medium
- Rollback idea (1줄): fragment/layout 단위로 되돌려 네비게이션 영향 없이 기능 철회 가능
