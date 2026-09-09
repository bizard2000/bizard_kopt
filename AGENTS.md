# HomeSmoke repository instructions

Before changing anything, read `PROJECT_STATUS.md`, `docs/DECISIONS.md`, and `CHANGELOG.md`.

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

## Change discipline

- Work on `homesmoke-native`. Do not change or merge `main` without explicit owner approval.
- Preserve application IDs, current signing compatibility, Android 6+ minimum, Auto-program JSON compatibility, and HomeSmoke 2.5.0 rollback.
- Functional/UI changes require a version bump for the affected app and a `CHANGELOG.md` entry. Documentation/CI-only changes do not.
- Do not mix a protocol change, UI redesign, and large refactor in one release.
- Run core, app, and remote tests plus both APK builds. Real Bluetooth/MQTT/Arduino verification remains a separate field test.
- Update `PROJECT_STATUS.md` in the same commit whenever completed work, pending work, versions, or constraints change.

## Current priority

Do not start another redesign. First complete real-device testing of HomeSmoke 2.6.18 and Remote 2.4.6, then fix only reproduced issues in priority order from `PROJECT_STATUS.md`.
