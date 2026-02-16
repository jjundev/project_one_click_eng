# Job: J-20260216-005 - Migrate mute/timer/TTS controls into DialogueLearningSettingDialog

## Goal
- Move `음소거`, `학습 타이머`, `TTS 설정` controls from `SettingFragment` into `DialogueLearningSettingDialog` shown in learning screen.
- Keep setting persistence/runtime behavior consistent while removing duplicate controls from the global settings screen.

## Scope
### Include
- [ ] Move mute section UI+binding from `SettingFragment` to `DialogueLearningSettingDialog`.
- [ ] Move study timer section UI (current placeholder semantics) to `DialogueLearningSettingDialog`.
- [ ] Move TTS section UI+binding (speech rate/locale/provider text) to `DialogueLearningSettingDialog`.
- [ ] Keep existing `AppSettingsStore` key usage and runtime application compatibility.
- [ ] Remove migrated sections from `fragment_setting.xml` and trim corresponding `SettingFragment` bindings/listeners.

### Exclude
- [ ] New timer runtime enforcement logic (placeholder status 유지).
- [ ] LLM/API key/model settings section migration.
- [ ] Changes outside settings migration scope (dialogue turn logic, summary logic, networking).

## Acceptance Criteria (PASS 조건)
- [ ] AC1: `DialogueLearningSettingDialog`에서 mute/timer/TTS 섹션이 표시되고 값 변경이 저장된다.
- [ ] AC2: 저장된 mute 값이 학습 화면 재생 차단 동작에 동일하게 반영된다.
- [ ] AC3: 저장된 TTS 속도/로케일이 학습 화면 재생 시 적용된다.
- [ ] AC4: `SettingFragment`에는 LLM/API 관련 설정만 남고, 이동 대상 3개 섹션은 제거된다.
- [ ] AC5: Build gate passes without formatting regression.

## Implementation Notes
- 기존 구조/참고 파일:
  - `repo/app/src/main/java/com/example/test/fragment/SettingFragment.java`
  - `repo/app/src/main/res/layout/fragment_setting.xml`
  - `repo/app/src/main/java/com/example/test/settings/AppSettingsStore.java`
  - `repo/app/src/main/java/com/example/test/settings/AppSettings.java`
  - `repo/app/src/main/java/com/example/test/fragment/DialogueLearningFragment.java`
  - `repo/app/src/main/res/values/strings.xml`
- 금지사항(예: 특정 화면 변경 금지):
  - SharedPreferences key/기본값 스키마 변경 금지
  - 타이머를 실제 학습 제어 로직에 연결하는 범위 확장 금지
- 롤백 아이디어(한 줄):
  - migrated section 관련 변경만 부분 revert해 `SettingFragment` 원상 복귀

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
- Tag: `JOB_J-20260216-005`
- Rule:
  - DEBUG 가드 필수 (`if (BuildConfig.DEBUG)`)
  - 민감정보 로깅 금지(마스킹 필수)

## QA Checklist
### UX
- [ ] 시나리오 1: 학습 화면 세팅 다이얼로그에서 mute ON 시 재생이 차단되고 OFF 시 정상 재생된다.
- [ ] 시나리오 2: TTS 속도/로케일 변경 후 학습 화면 재생에 변경값이 적용된다.
- [ ] 시나리오 3: 타이머 섹션은 기존과 동일하게 placeholder/비활성 상태 의미를 유지한다.
- [ ] 시나리오 4: 기존 설정 탭(`SettingFragment`)에서 이동된 3개 섹션이 제거되어 중복 노출되지 않는다.

### Logcat
- Filter:
  - `adb logcat | grep JOB_J-20260216-005`
- [ ] 기대 로그 1: 설정 저장 이벤트가 DEBUG 가드 하에 출력된다.
- [ ] 기대 로그 2: 민감정보 없이 적용/차단 이벤트 로그가 출력된다.

## Risk
- Level: Medium
- Rollback idea (1줄): section 단위 커밋으로 되돌려 화면/저장소 불일치 최소화
