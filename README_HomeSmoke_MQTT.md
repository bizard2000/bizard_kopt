# HomeSmoke MQTT native build

Native Android rebuild based on the original `Homesmoke.aia`.

Restored from the AIA source:
- Bluetooth Classic SPP transport and incoming `|...|end` parsing;
- original modes `a0`, `a1`, `a2`;
- original setpoint/power commands `k...` and `v...`;
- original PID coefficient commands `p...`, `i...`, `d...`, `z...` with x100 scaling;
- monitor/settings/PID screens and separate send button for every PID coefficient;
- probe K/T visibility and keep-screen-on settings;
- right-side sliding menu behavior restored from SidebarV2;
- Android 16 system-bar insets fixed in the modern build;
- exact Screen1.bky field mapping restored for monitor, logging and MQTT;
- MQTT telemetry and remote setpoint gateway;
- separate HomeSmoke Legacy build targeting Android 4.0+ (API 14).

Build trigger: verify HomeSmoke Android 4.x legacy compatibility.
