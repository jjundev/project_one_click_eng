# Project: Agent Orchestration 3.0 (Strict Serial)

## Core rule
- Always follow Workflow 3.0 SOP: @E:\AndroidLab\project_one_click_eng\agent-system\jobs\README.md
- The current role is determined by the user prompt (or the terminal’s profile). Do NOT assume other roles.
- Handoff must be done only via `E:\AndroidLab\project_one_click_eng\agent-system\jobs` artifacts (no “chat memory” handoff).
- On every role/session start, read `E:\AndroidLab\project_one_click_eng\agent-system\jobs\LOCK.json` first and record the current stage.
- Before taking action, synchronize state from `jobs/queue`, `jobs/active`, `jobs/reports`, `jobs/done`, `jobs/failed` in that order.
- Never resume from memory: if handoff artifacts are not verifiable, do not continue work.

## Mandatory agent handoff refresh protocol
- Within 10 seconds after role/session activation, execute:
  1. Read `E:\AndroidLab\project_one_click_eng\agent-system\jobs\LOCK.json`, `agent-system/jobs/active`, `agent-system/jobs/queue`, `agent-system/jobs/reports`, `agent-system/jobs/done`, `agent-system/jobs/failed`.
  2. Verify expected `stage` for the current role (`orchestrator`: any allowed stage, `coding`: `coding`, `qa`: `qa`).
  3. If `active` has a job, verify `agent-system/jobs/active/<job_id>/job.md` exists.
  4. Check role-required artifacts:
     - coding: `agent-system/jobs/active/<job_id>/job.md`
     - qa: `agent-system/jobs/reports/<job_id>-report.md` and (if applicable) `agent-system/jobs/failed/<job_id>/qa-fail.md`
     - orchestrator: `agent-system/jobs/active/<job_id>/job.md` or readiness of queue state
  5. Print one-line restart summary: `active_job_id`, `lock_stage`, `next_action`.
- If validation fails:
  - coding/qa: stop immediately when `LOCK.json` is missing.
  - coding/qa: stop immediately when stage mismatch or required artifact missing.
  - orchestrator: reconcile `active/queue` consistency first, then continue with stage transitions only.
- If lock or active state is invalid, pause and ask for explicit re-sync action before modifying files.

## Where to look
- Jobs: E:\AndroidLab\project_one_click_eng\agent-system\jobs\queue, E:\AndroidLab\project_one_click_eng\agent-system\jobs\active, E:\AndroidLab\project_one_click_eng\agent-system\jobs\reports, E:\AndroidLab\project_one_click_eng\agent-system\jobs\done, E:\AndroidLab\project_one_click_eng\agent-system\jobs\failed
- Role playbooks:
  - Orchestrator: @E:\AndroidLab\project_one_click_eng\agent-system\jobs\prompts\orchestrator.md
  - Coding agent: @E:\AndroidLab\project_one_click_eng\agent-system\jobs\prompts\coding-agent.md
  - QA agent: @E:\AndroidLab\project_one_click_eng\agent-system\jobs\prompts\qa-agent.md

## Safety/guardrails
- Never edit files outside the job scope.
- If LOCK.json is missing while you are a coding/QA agent, stop immediately.
- A role switch without successful handoff refresh is forbidden. No file changes or role actions may proceed until sync validates lock/active/report consistency.


