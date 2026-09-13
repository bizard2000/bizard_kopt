# HomeSmoke repository instructions

## Context

- Always follow this file.
- At the start of a new work session, after context loss, or when resuming the project, read `PROJECT_STATUS.md`.
- Read `docs/DECISIONS.md` when the task touches architecture, protocol, compatibility, or previously fixed design decisions.
- Read `CHANGELOG.md` when the task touches versions, releases, or user-visible change history.
- Read `docs/AUDIT_2026-09-09.md` only when the task is related to findings or risks from that audit.
- Repository code and CI are authoritative if they conflict with chat memory or historical documentation. `PROJECT_STATUS.md` is authoritative for current versions, priorities, and known blockers.

## Scope

- Active products are the Android modules `app` (HomeSmoke), `remote` (HomeSmoke Remote), and shared `core`.
- This HomeSmoke Android project has no ESP32. Files under `archive/firmware-not-used` are historical references from another/unused firmware effort and must not be built, flashed, or treated as the active controller.
- The installed Arduino cannot currently be reflashed. Do not modify controller behavior unless the owner explicitly starts a separate firmware task.

## Protocol constraints

- Preserve the literal `\\0` command terminator and `|...|end` telemetry format.
- Confirmed modes: `a0` manual, `a1` PID, `a2` old Arduino Auto, `a3` STOP.
- Android Auto owns recipe stages, timing, tolerances, and K/T conditions. It must not send `a2`.
- Android Auto sends `a1` once at start, then only integer chamber setpoints `kNN`; STOP is `a3`.
- Do not send `x1`, `x0`, or `h` to the installed controller.
- Do not invent commands. Chamber/power values are integers 0–100.

## Execution and approval boundaries

- Work on `homesmoke-native`. Do not change or merge `main` without explicit owner approval.
- Preserve application IDs, current signing compatibility, Android 6+ minimum, Auto-program JSON compatibility, and HomeSmoke 2.5.0 rollback.
- For non-trivial or multi-file changes, briefly state the intended files and plan before editing, then continue without waiting unless an explicit approval boundary is reached.
- Local reversible edits, inspection, builds, and relevant tests may proceed without asking for approval.
- Explicit owner approval is required before changing or merging `main`, deleting files, publishing changes/releases to GitHub, changing application versions, or flashing/changing Arduino firmware.
- Functional/UI changes require a version bump for the affected app and a `CHANGELOG.md` entry. Documentation/CI-only changes do not require an application version bump.
- Do not mix a protocol change, UI redesign, and large refactor in one release.
- Run the smallest relevant test/build set for the files changed. Run core/app/remote tests plus both APK builds for release validation or cross-cutting changes that can affect all modules. Real Bluetooth/MQTT/Arduino verification remains a separate field test.
- Continue until the requested change is implemented and relevant checks pass, or until a genuine blocker or explicit approval boundary is reached. Fix failures caused by the change without asking for confirmation unless doing so crosses an approval boundary.

## Documentation updates

- Update `PROJECT_STATUS.md` when project state, milestones, known blockers, versions, constraints, or the next unfinished task materially change.
- Update `CHANGELOG.md` for completed user-visible changes, versioned releases, or material repository-maintenance changes that future work needs to know.
- Do not update either file for read-only analysis, no-op investigation, unchanged test reruns, or trivial wording changes that do not affect project state.

## Skills

- There are currently no project-specific `SKILL.md` files. Do not create a catch-all HomeSmoke skill merely to duplicate these repository rules.
- If a future skill is added, keep its trigger/description narrow, do not duplicate `AGENTS.md`, and load detailed references only when the task requires them.
- A skill must not impose unconditional document reads, full test matrices, or approval stops unrelated to its specific workflow.

## Current priority

Do not start another redesign. First complete the repeat real-device/background test of HomeSmoke Remote 2.4.8 described in `PROJECT_STATUS.md`, then fix only reproduced issues in that priority order. HomeSmoke 2.6.18 has already passed its preliminary field test.
