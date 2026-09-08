# HomeSmoke 2.6.13 — Kotlin field-test diagnostics

## Scope

This version introduces Kotlin into the main HomeSmoke Android application without changing Arduino firmware, Arduino commands, confirmed telemetry fields, Remote protocol fields, Auto-program v1/v2 format, or Remote 2.1.3.

Android 4 / Legacy remains excluded. HomeSmoke remains Android 6+ (`minSdk 23`).

## What is new

- Kotlin Gradle plugin is enabled only for the main `app` module.
- `FieldTestRecorder.kt` writes a JSONL timeline while a field test is active.
- `FieldTestActivity.kt` is a separate launcher entry named `HomeSmoke · Полевой тест` so diagnostics can be used without changing the validated main UI.
- `FieldTestLifecycleProvider.kt` records process/activity lifecycle events while a test is active.
- Remote setpoint tracking records `applied`, `controller_ack_timeout`, and `bluetooth_disconnected` results in the same test timeline.
- The field-test console observes Bluetooth, MQTT, network transport, Auto state/stage/hold/chamberReady, controller mode and telemetry age.
- A telemetry gap is marked after more than 8 seconds without fresh telemetry and recovery is marked when fresh telemetry returns.
- Export creates one ZIP containing the JSONL diagnostic timeline, metadata and Auto CSV history files created/updated during the test.

## How to run the real-device test

1. Install HomeSmoke 2.6.13 and open the normal HomeSmoke launcher. Connect Bluetooth/MQTT and prepare PID or Auto as required.
2. Open the second launcher `HomeSmoke · Полевой тест`.
3. Press `Начать новый журнал`.
4. Before each physical scenario press the matching marker button, then perform the action:
   - Bluetooth disconnect/reconnect during PID or Auto.
   - Remove Internet/Wi-Fi for 1–3 minutes and restore it.
   - Send a Remote setpoint while telemetry is absent; expected final ACK is `controller_ack_timeout` unless Bluetooth disconnect produces `bluetooth_disconnected` first.
   - Mark notification scenario, background HomeSmoke, then open it from its foreground notification.
   - In Auto with `AFTER_CHAMBER_READY`, mark the scenario and observe a controlled excursion outside chamber tolerance after stabilization.
5. Press `Завершить и сохранить ZIP` and save the generated archive.
6. Upload that ZIP to the HomeSmoke project chat for analysis.

Do not force unsafe temperature changes merely to create a test condition. Use only normal, controlled operation of the smoker.
