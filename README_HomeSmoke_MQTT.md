# HomeSmoke MQTT native build

Native Android rebuild for Android 16 based on the original `Homesmoke.aia`.

Restored from the AIA source:
- Bluetooth Classic SPP transport and incoming `|...|end` parsing;
- original modes `a0`, `a1`, `a2`;
- original setpoint/power commands `k...` and `v...`;
- original PID coefficient commands `p...`, `i...`, `d...`, `z...` with x100 scaling;
- monitor/settings/PID screens and separate send button for every PID coefficient;
- probe K/T visibility and keep-screen-on settings;
- file logging and MQTT publishing added in the native rebuild.

Build trigger: full HomeSmoke 2.0.0 PR artifact.
