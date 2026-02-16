# Project: Agent Orchestration 3.0 (Strict Serial)

## Core rule
- Always follow Workflow 3.0 SOP: @E:\Antigravity Projects\project_one_click_eng\agent-system\jobs\README.md
- The current role is determined by the user prompt (or the terminal’s profile). Do NOT assume other roles.
- Handoff must be done only via `E:\Antigravity Projects\project_one_click_eng\agent-system\jobs` artifacts (no “chat memory” handoff).

## Where to look
- Jobs: E:\Antigravity Projects\project_one_click_eng\agent-system\jobs\queue, E:\Antigravity Projects\project_one_click_eng\agent-system\jobs\active, E:\Antigravity Projects\project_one_click_eng\agent-system\jobs\reports, E:\Antigravity Projects\project_one_click_eng\agent-system\jobs\done, E:\Antigravity Projects\project_one_click_eng\agent-system\jobs\failed
- Role playbooks:
  - Orchestrator: @E:\Antigravity Projects\project_one_click_eng\agent-system\jobs\prompts\orchestrator.md
  - Coding agent: @E:\Antigravity Projects\project_one_click_eng\agent-system\jobs\prompts\coding-agent.md
  - QA agent: @E:\Antigravity Projects\project_one_click_eng\agent-system\jobs\prompts\qa-agent.md

## Safety/guardrails
- Never edit files outside the job scope.
- If LOCK.json is missing while you are a coding/QA agent, stop immediately.

