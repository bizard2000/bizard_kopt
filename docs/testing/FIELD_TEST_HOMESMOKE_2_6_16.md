# HomeSmoke 2.6.16 — installed Arduino protocol field test

## Scope

This version keeps the real Arduino firmware unchanged and restricts Android Auto to the installed controller protocol. It removes `x1`, `x0` and `h` from Android command traffic. Auto remains on Android and uses `a1` once at start plus `k<setpoint>` for the current chamber target.

The installed firmware has no Android heartbeat watchdog. A hard Bluetooth loss can leave the controller on its last PID setpoint, so real-device Auto testing must remain supervised.

Android 4 / Legacy remains excluded. HomeSmoke remains Android 6+ (`minSdk 23`).

## What is new

- Kotlin Gradle plugin remains enabled for the main `app` module.
- `FieldTestRecorder.kt` writes a JSONL timeline while a field test is active.
- `FieldTestActivity.kt` is a separate launcher entry named `HomeSmoke · Полевой тест` so diagnostics can be used without changing the validated main UI.
- `FieldTestLifecycleProvider.kt` records process/activity lifecycle events while a test is active.
- Remote setpoint tracking records `applied`, `controller_ack_timeout`, and `bluetooth_disconnected` results in the same test timeline.
- Android Auto records every Bluetooth command plus pending, confirmed, retried and failed stage setpoints; `a1` is sent once at Auto start and only `k<setpoint>` is retried.
- Auto hold timing remains paused until fresh Arduino telemetry confirms PID mode and the requested chamber setpoint.
- The field-test console observes Bluetooth, MQTT, network transport, Auto state/stage/hold/chamberReady, controller mode and telemetry age.
- A telemetry gap is marked after more than 8 seconds without fresh telemetry and recovery is marked when fresh telemetry returns.
- Export creates one ZIP containing the JSONL diagnostic timeline, metadata and Auto CSV history files created/updated during the test.

## How to run the real-device test

1. Install HomeSmoke 2.6.16 and open the normal HomeSmoke launcher. Connect Bluetooth/MQTT and prepare PID or Auto as required.
2. Open the second launcher `HomeSmoke · Полевой тест`.
3. Press `Начать новый журнал`.
4. Before each physical scenario press the matching marker button, then perform the action:
   - Bluetooth disconnect/reconnect during PID or Auto.
   - Remove Internet/Wi-Fi for 1–3 minutes and restore it.
   - Send a Remote setpoint while telemetry is absent; expected final ACK is `controller_ack_timeout` unless Bluetooth disconnect produces `bluetooth_disconnected` first.
   - Mark notification scenario, background HomeSmoke, then open it from its foreground notification.
   - In Auto with `AFTER_CHAMBER_READY`, mark the scenario and observe a controlled excursion outside chamber tolerance after stabilization.
   - Start a multi-stage Auto program and verify that each stage waits for the Arduino setpoint confirmation before its timer advances.
5. Press `Завершить и сохранить ZIP` and save the generated archive.
6. Upload that ZIP to the HomeSmoke project chat for analysis.

Do not force unsafe temperature changes merely to create a test condition. Use only normal, controlled operation of the smoker.
