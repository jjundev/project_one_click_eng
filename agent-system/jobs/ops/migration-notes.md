# 기존 운영 이관 노트

1) `/jobs` 상태 디렉터리는 `queue`, `active`, `reports`, `review`, `done`, `failed`를
   `jobs/templates/*`와 `jobs/prompts/*`로 운영한다.
2) 운영 기준은 Strict Serial 3.0 단계만 사용하고, 구버전 오케스트레이션 자산은 폐기한다.
3) `stage`는 `coding`/`qa`/`done`을 허용한다.
4) `jobs/LOCK.json`은 런타임 파일이므로 실행 중 필요할 때만 생성하고 Idle 상태에서는 저장소에 남기지 않는다.
5) 종료/전이 시나리오를 스크립트 호출 없이 수동 체크리스트로 고정 운용한다.
   - `coding -> qa`: `jobs/LOCK.json` 상태/경로/리포트 유효성 검증 후 수동 전이
   - `qa -> done`: `jobs/review/<job_id>-final.md` 작성, `jobs/active/<job_id>/` 이동, lock 삭제
   - `qa -> failed`: `jobs/failed/<job_id>/qa-fail.md` 작성, `jobs/active/<job_id>/` 이동, lock 삭제
