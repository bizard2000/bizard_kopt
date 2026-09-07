# HomeSmoke 2.6 — stabilization scope

This phase changes Android/Remote/MQTT/CI only. Arduino firmware is frozen and must not be modified.

## Goals

1. Freeze HomeSmoke 2.5.0 artifacts for rollback.
2. Replace CI-time source patching with committed source code.
3. Make versionCode monotonic.
4. Separate protocol parsing, Auto state machine, recipe storage and MQTT protocol from Activity UI.
5. Keep Auto state across UI recreation and move long-running control to a foreground service.
6. Add chamber-temperature tolerance/hold semantics and configurable probe activation timing.
7. Confirm remote setpoint application from Arduino telemetry before publishing MQTT ACK.
8. Add automated unit tests for parser, Auto transitions and MQTT command correlation.
9. Redesign the modern HomeSmoke UI while preserving the navigation model and a permanent STOP action.
10. Improve Remote health/staleness/Auto visibility.
11. Android 4 / Legacy is excluded from further development and CI. Main HomeSmoke support starts at Android 6 (`minSdk 23`).
12. Do not modify any file under `arduino/` in this phase.

Current detailed audit and deferred decisions are recorded in `AUDIT_HOMESMOKE_2_6_12.md`.
