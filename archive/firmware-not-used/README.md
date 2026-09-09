# Неактивные firmware-файлы

Эта папка исключена из активного Android-проекта.

- `platformio.ini`, `src/`, `data/`, `include/`, `lib/` и `custom.csv` относятся к старой/другой ESP32-работе. В фактическом HomeSmoke ESP32 нет.
- `arduino/HomeSmoke_Controller_rev_2_0_AndroidAuto.ino` — подготовленная rev2, которая **не прошита** в установленную Arduino.
- Эти файлы сохранены только для истории. Их нельзя автоматически собирать, применять как описание реального протокола или прошивать без отдельной задачи и прямого разрешения владельца.

Фактический протокол приложений описан в [`../../docs/protocol/COMMAND_PROTOCOL_HOMESMOKE.md`](../../docs/protocol/COMMAND_PROTOCOL_HOMESMOKE.md).
