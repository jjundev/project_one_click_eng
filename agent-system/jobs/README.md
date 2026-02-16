# Workflow 3.0 (Strict Serial) SOP

## 0) 목표와 철학
1인 개발자가 여러 작업을 병렬로 하지 않고, 오케스트레이터-코딩-QA-사용자 역할 체계로
항상 1개의 Job만 순차 처리한다.

- Active Job은 1개만 존재한다.
- merge + post-merge smoke가 완료되어야 다음 Job으로 넘어간다.
- 판단/핸드오프는 파일 아티팩트로만 한다(대화 메모 의존 금지).

## 1) 역할

### 1-1 오케스트레이터
- 사용자와 대화해 Job 정의 및 우선순위 조정
- `jobs/queue`에서 작업 축적
- `jobs/active` 승격 및 `jobs/LOCK.json` 생성/비상 해제 수행
- `LOCK` 해제 조건을 엄격히 집행

### 1-2 코딩 에이전트
- `jobs/active` 1개만 처리
- 구현 + 로그 태그 + compile/build 게이트 통과
- 완료 시 `/jobs/reports/<job_id>-report.md` 작성
- Build Gate PASS + report 확인 후 `jobs/LOCK.json`를 `coding -> qa`로 승격

### 1-3 QA 에이전트
- Report 기반으로 사용자가 바로 실행 가능한 QA 항목 제시
- 사용자 PASS/FAIL 수집
- PASS 시 integrate + post-merge smoke 수행 후 최종 리뷰/완료 처리(최종 보고/lock 정리 포함)
- FAIL 시 사용자 QA 반영 항목 정리/실패 사유 기록 후 재실행 지시

### 1-4 사용자(너)
- QA 단계에서 앱 실행 및 UX/Logcat 확인
- 최종 PASS/FAIL 판단 (Safety Gate)

## 2) 폴더 구조
```
/jobs
  README.md
  templates/
    job.md
    report.md
    final-review.md
    lock.json
  prompts/
    orchestrator.md
    coding-agent.md
    qa-agent.md
  queue/
  active/
  reports/
  review/
  done/
  failed/
  ops/
    migration-notes.md
    branch-and-id-guide.md
    checklist.md
    promote_to_qa.ps1
    close_job.ps1
    fail_job.ps1
```

`LOCK.json`은 필요 시 생성되는 전역 잠금 파일이며, idle 상태에서는 파일이 없어야 한다.

## 3) 상태 머신
- `queue -> active -> reports -> review -> done`
- `failed`는 재시도/보류 처리시 사용

기본 전이:
1. queue: Job 생성
2. active: `orchestrator`가 승격, `LOCK.json` 생성 (`stage: "coding"`)
3. reports: 코딩 완료 후 report 작성
4. review: QA 결과 보고 산출(`jobs/review/<job_id>-final.md`)
5. done: QA PASS + merge + smoke 완료 + lock 삭제

실패:
1. qa: QA FAIL
2. failed: 실패 원인 보관 (`jobs/failed/<job_id>/qa-fail.md`)
3. queue: `revN`으로 재발행

## 4) 전역 잠금 규칙
- `jobs/LOCK.json` 존재: 작업 파이프라인 점유 중
- `jobs/LOCK.json` 없음: 다음 job을 active로 올릴 수 있음
- 오케스트레이터:
  - `jobs/LOCK.json` 생성 및 비상 해제(emergency unlock)만 수행
  - 비상 해제 시 사유를 로그/메모로 남김
- 코딩 에이전트:
  - `jobs/LOCK.json` stage를 `coding -> qa`로 전환 가능
  - 전이 게이트: `LOCK.json` 존재, `stage == coding`, `jobs/active/<job_id>/` 존재, `/jobs/reports/<job_id>-report.md` 존재, report 내 Build Gate PASS
  - 변경 가능 필드: `stage` 1개
- QA 에이전트:
  - PASS 시 `qa -> done` 수동 전이(최종 보고서 작성 후 done 이동, lock 삭제)
  - FAIL 시 failed 수동 이동(qa-fail 보고서 작성 후 lock 삭제)
- 코딩/QA 에이전트는 lock가 없으면 즉시 중단
- `jobs/LOCK.json` 삭제 후 idle 상태에서 LOCK 파일이 없어야 함

### `LOCK.json`
- `job_id`: `J-YYYYMMDD-###`
- `title`: 짧은 제목
- `branch`: 작업 브랜치
- `stage`: `coding` | `qa` | `done`
- `created_at`: ISO-8601(KST/UTC+09:00)

### `jobs/queue/<job_id>/job.md`
- Goal, Scope(Include/Exclude), AC, Build Gate, Logging, QA Checklist 포함 필수

### `jobs/reports/<job_id>-report.md`
- 변경 요약, 실제 파일, 테스트, Build 결과, Logcat, QA Checklist 관찰, rollback

### `jobs/review/<job_id>-final.md`
- 최종 판정(PASS), Merge/Smoke 결과, 산출물, 리스크/다음 액션

## 6) 실행 규칙(엄격)
### Stage 0: Job 생성
- 오케스트레이터가 `/jobs/templates/job.md` 기준으로 생성
- Build Gate와 QA 체크리스트는 고정값으로 기입

### Stage 1: Queue → Active
- `LOCK`가 없고 queue에 항목이 있을 때만 승격
- `jobs/active/<job_id>/`로 이동 후 `LOCK` 생성(`stage=coding`)

### Stage 2: 구현
- `/jobs/active/`의 job만 열기
- Build Gate 실패 시 report 작성 금지
- Build Gate pass 후 report 작성
- 완료 후 `jobs/LOCK.json`의 `stage`를 수동으로 `qa`로 전이
  - 전이 전후 `jobs/LOCK.json`, `jobs/active/<job_id>/`, `jobs/reports/<job_id>-report.md`를 직접 검증한다.

### Stage 3: QA
- QA는 report 기반 체크리스트로 사용자 검증 요청
- 사용자 PASS/FAIL만으로 판정 진행

### Stage 4: Merge + Smoke + Done
- PASS 시 feature branch → `implement` merge
- smoke: 앱 부팅, 변경 기능 1회 실행, crash 로그 없음 확인
- PASS 시 `qa -> done` 수동 전이 (`jobs/review/<job_id>-final.md` 작성, `jobs/active/<job_id>/`→`jobs/done/<job_id>/` 이동, `jobs/LOCK.json` 삭제)

### Stage 5: 실패
- FAIL 시 `qa -> failed` 수동 전이 (`jobs/active/<job_id>/`→`jobs/failed/<job_id>/` 이동, `qa-fail.md` 작성, `jobs/LOCK.json` 삭제)
- 수정 반영 후 `revN` 재발행

## 7) 브랜치 정책
- feature/fix branch: `feat/J-YYYYMMDD-XXX-short-title`
- 대상 기준 브랜치: `implement`
- 안정 브랜치 반영은 배포/릴리스 시점 `main` 또는 trunk 반영

## 8) 운영 체크리스트
- Job은 작게 분할
- Build Gate는 job에 고정 기록
- Smoke 항목은 매번 동일 템플릿 사용
- 민감정보(토큰, 이메일, 식별자) 로그에 기록 금지


