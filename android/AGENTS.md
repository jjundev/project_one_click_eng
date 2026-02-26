# AGENTS.md

## 목적
이 문서는 설정 화면의 카드 클릭 보상 기능을 수정하면서 실제로 발생한 오류를 기반으로, 같은 유형의 회귀를 방지하기 위한 개발 지침을 정의한다.

## 이번 이슈에서 확인된 핵심 오류
1. 레이아웃 id와 실제 UI 항목 의미가 어긋남.
2. XML에 id는 존재하지만 Fragment에서 리스너를 연결하지 않아 터치가 무동작.
3. 요구사항이 여러 번 변경되었는데, 이전 정책(보상 수치/대상 카드/일수 증가 여부)이 일부 코드에 잔존할 위험이 있었음.
4. 시간 보너스 전용 API와 일수 증가 포함 API를 혼용할 때 의도하지 않은 지표 변경 가능성이 있었음.
5. 토스트 문자열을 단일 키로 재사용하면 카드별 정책 분리 시 문구 불일치가 발생할 수 있음.

## 구현 지침

### 1) UI id/의미 매핑 규칙
- 카드/행 id는 반드시 텍스트 라벨 의미와 1:1로 대응한다.
- id를 다른 섹션(예: 이메일 행)에 임시로 재사용하지 않는다.
- 레이아웃 수정 후 아래 항목을 함께 점검한다.
  - XML 라벨 텍스트
  - id 이름
  - Fragment `findViewById` 대상
  - 실제 클릭 리스너 연결 여부

### 2) 클릭 이벤트 연결 규칙
- `bindViews()`에서 바인딩한 뷰는 `setupListeners()`에서 연결 여부를 명시적으로 처리한다.
- 리스너 대상 뷰가 `null`이면 디버그 로그를 남긴다.
- 카드별 동작이 다르면 리스너/핸들러/상태값을 분리한다.

### 3) 연타(멀티탭) 로직 규칙
- 시간 기준은 `SystemClock.elapsedRealtime()`만 사용한다.
- 카드별로 아래 상태를 독립 관리한다.
  - `tapCount`
  - `windowStartElapsedMs`
- 발동 즉시 해당 카드 상태를 초기화한다.
- 카드 간 탭 수 합산을 금지한다.

### 4) 보상 정책 분리 규칙
- 카드마다 상수를 분리해 선언한다.
  - 학습 시간(ms)
  - XP
  - modeId
  - session prefix
- 시간만 증가하는 정책은 `applyTimeBonus*` 계열 사용.
- 시간 + 학습일수 증가 정책은 `applyManualBonus*` 계열 사용.
- 학습일수 증가가 필요하면 `bonusDayKey`를 매회 유니크하게 생성한다.

### 5) 로컬/클라우드 동기화 규칙
- 로컬 반영을 먼저 수행하고, 클라우드 실패는 허용한다(로컬 유지).
- 포인트 지급 후에는 pending flush를 호출한다.
- 정책과 다른 필드(`study_day_keys`, `streak_day_keys`)가 갱신되지 않도록 API 선택을 검증한다.

### 6) 문자열/사용자 메시지 규칙
- 카드별 보상이 다르면 토스트 문자열도 별도 리소스 키로 분리한다.
- 문구는 실제 지급값과 완전히 동일해야 한다.

## 테스트 지침

### 1) 필수 자동 테스트
- `LearningStudyTimeStoreTest`
  - 시간 전용 보너스: 일수 불변 검증
  - 수동 보너스: study/streak day 동시 증가 검증
- `LearningStudyTimeCloudRepositoryLogicTest`
  - same-day/day-boundary 시 total/today 계산 검증
  - manual bonus key 병합 검증

### 2) 필수 수동 시나리오
- 대상 카드 2초 내 5탭 1회 발동
- 대상 카드 10탭 2회 발동
- 카드 A 3탭 + 카드 B 2탭 미발동
- 2초 초과 시 카운트 리셋
- 비대상 카드 터치 시 미발동

### 3) 빌드/회귀 확인
- `:app:testDebugUnitTest --tests LearningStudyTimeStoreTest --tests LearningStudyTimeCloudRepositoryLogicTest`
- `:app:compileDebugJavaWithJavac`

## 변경 작업 체크리스트 (PR 전)
1. XML id와 화면 라벨 의미가 일치하는가.
2. Fragment에서 해당 id가 바인딩되고 리스너가 연결되는가.
3. 카드별 보상 상수와 토스트 문자열이 정책과 일치하는가.
4. 일수 증가 여부가 정책대로 API 선택에 반영되었는가.
5. 자동 테스트/컴파일이 통과했는가.
