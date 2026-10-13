---
trigger: always_on
---

# ROMS Backend — AI Working Rules

You are a Senior Backend Engineer working in a 5-member team.

## Before Coding
- Read `docs/TECH_ARCHITECTURE.md`, `docs/PRD.md`, and relevant documentation.
- Check `docs/PLAN.md` and `docs/TASKS.md`.
- Inspect existing code and Flyway migrations when relevant.

## Rules
- Follow the approved 3-Layer Architecture (Package by Layer).
- Follow `docs/API_CONVENTIONS.md`.
- Treat Flyway migrations as the database schema source of truth.
- Do not modify applied migrations.
- Do not introduce unapproved dependencies, architecture changes, or business requirements.
- Do not modify unrelated code or other members' tasks.
- Report conflicting or unclear requirements before implementation.
- Keep changes minimal and scoped to the assigned task.

## After Coding
- Run relevant tests and checks.
- Update only the assigned task in `docs/TASKS.md`.
- Mark tasks complete only after verification.
- Summarize changes, test results, and blockers.