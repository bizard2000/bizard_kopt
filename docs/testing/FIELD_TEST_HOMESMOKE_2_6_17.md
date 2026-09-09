# HomeSmoke 2.6.17 — installed Arduino protocol field test

## Scope

This version keeps the real Arduino firmware unchanged and restricts Android Auto to the installed controller protocol. Auto remains on Android and uses `a1` once at start plus `k<setpoint>` for the current chamber target.

## History and chart checks

1. Install HomeSmoke 2.6.17 and connect to the real Arduino over Bluetooth.
2. Select manual mode, wait for several telemetry packets, press STOP, and open `История`. A `MANUAL` CSV and a non-empty graph must be present.
3. Select PID mode, send a chamber setpoint, wait for telemetry, press STOP, and open `История`. A `PID` CSV and a non-empty graph must be present.
4. Start an Android Auto program. Verify its CSV contains stage events and telemetry, and that each stage still waits for fresh `mode=1` telemetry with the requested setpoint.
5. Verify that STOP, changing mode, and Bluetooth disconnect close the current session without deleting earlier CSV files.

## Safety and protocol checks

- Do not flash or modify the Arduino.
- Do not send `a2`, `x1`, `x0` or `h`; STOP is `a3`.
- Use only controlled temperatures and normal operation of the smoker.
- Android 6+ remains supported (`minSdk 23`).

## Diagnostic run

1. Open the separate `HomeSmoke · Полевой тест` launcher.
2. Press `Начать новый журнал` before the scenario.
3. Test Bluetooth disconnect/reconnect during PID or Auto, network loss and recovery for MQTT, and a multi-stage Auto program.
4. Press `Завершить и сохранить ZIP` and upload the archive to the project chat for analysis.
