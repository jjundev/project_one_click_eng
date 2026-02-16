# 오퍼레이션 체크리스트

## 오케스트레이터 체크
- [ ] job_id/job title/branch가 규칙에 맞는가?
- [ ] queue에 최소 1개 이상이 있는가?
- [ ] active 승격 시 LOCK가 없는가?
- [ ] report 수신 후 `jobs/LOCK.json` 검증 후 `stage: coding -> qa` 전이를 직접 완료했는가?
   - `jobs/active/<job_id>/`, `jobs/reports/<job_id>-report.md` 존재 확인
   - report `Build Result`가 `PASS`인지 확인
   - `jobs/LOCK.json`에서 `stage`만 `qa`로 변경했는가?
- [ ] PASS 처리 시 `jobs/review/<job_id>-final.md` 작성, `jobs/active/<job_id>/` 이동, `jobs/LOCK.json` 삭제를 모두 수행했는가?
- [ ] FAIL 처리 시 `jobs/active/<job_id>/`→`jobs/failed/<job_id>/` 이동, `qa-fail.md` 작성, `jobs/LOCK.json` 삭제를 모두 수행했는가?
- [ ] 비상 해제(emergency unlock)가 필요한 경우 사유를 기록했는가?

## 코딩 체크
- [ ] active job 1개만 열었는가?
- [ ] Build Gate를 job.md의 고정 커맨드로 실행했는가?
- [ ] Build Gate에서 `:app:spotlessApply`와 `:app:spotlessCheck`를 실행했는가?
- [ ] compile/assemble PASS 전 report를 만들지 않았는가?
- [ ] promote_to_qa 게이트 5개를 모두 확인했는가?
- [ ] 로그 태그 + `BuildConfig.DEBUG` 적용했는가?
- [ ] 민감정보 마스킹했는가?

## QA 체크
- [ ] 사용자 UX 시나리오 1개 이상 수행했는가?
- [ ] Logcat 필터 로그가 남아 있는가?
- [ ] AC 매핑으로 PASS/FAIL 판단했는가?
- [ ] Merge 후 smoke를 수행했는가?
- [ ] PASS면 final 보고서 작성/`done` 이동/`LOCK` 삭제를 직접 수행했는가?
- [ ] FAIL이면 `jobs/failed/<job_id>/qa-fail.md` 작성/`failed` 이동/`LOCK` 삭제를 직접 수행했는가?
