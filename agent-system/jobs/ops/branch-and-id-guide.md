# ID & Branch 운영 가이드

## Job ID 정책
- 형식: `J-YYYYMMDD-###`
- 시간대: KST(UTC+09:00)
- 일자 내 연속 번호는 001, 002, 003...
- 예시: `J-20260216-001`

## Branch 정책
- 작업 브랜치: `feat/<job_id>-<short-title>`
- 기준 기준브랜치: `implement`
- 통합 원칙:
  - coding 완료 + QA PASS + post-merge smoke PASS 후에만 `implement`로 merge
  - 배포 시점에 `implement` -> `main` 반영

## 재발행 규칙
- 같은 Job의 재작업은 기존 ID 유지 + `-rev2`, `-rev3` suffix 사용
- 실패 작업은 `failed/`에 아카이브 후 새 queue 항목을 발행
