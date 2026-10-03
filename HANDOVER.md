# HomeSmoke — Handover / Передача проекта

Дата: 03.10.2026
Репозиторий: https://github.com/bizard2000/bizard_kopt
Рабочая ветка: homesmoke-native

## Назначение

HomeSmoke — Android-система управления домашней коптильней.

Активные модули:
- app/ — HomeSmoke: Bluetooth с Arduino, PID, Android Auto, история, графики и диагностика.
- remote/ — HomeSmoke Remote: удалённый мониторинг и команды через MQTT; Kotlin + Jetpack Compose.
- core/ — общее ядро: телеметрия, протокол и логика Android Auto.

ESP32 в текущем проекте нет. Исторические ESP32/PlatformIO и неиспользуемая Arduino rev2 находятся только в archive/firmware-not-used/ и не относятся к активной системе.

## Текущее состояние

HomeSmoke: 2.6.18 (28) — CI/APK прошли; предварительный полевой тест 10.09.2026 успешен по наблюдению владельца.

HomeSmoke Remote: 2.4.8 (46) — Kotlin/Compose, MQTT health-watchdog/reconnect; полный CI/APK прошли; нужен повторный длительный полевой тест в фоне.

Arduino: установленная старая прошивка; не перепрошивать.

Последний зафиксированный полный CI: GitHub Actions run 34462925296 (#308), success.
Source commit: c48d9e475f9d9046552f61e110239302d53721c0.
Remote 2.4.8 SHA-256: bf0a6e0f023251dc19bfd87b4b8e6ba8c3b4e09b33e0c52ff2c9172220309703.

## Обязательное чтение

1. AGENTS.md
2. PROJECT_STATUS.md
3. CHANGELOG.md
4. docs/DECISIONS.md
5. docs/protocol/COMMAND_PROTOCOL_HOMESMOKE.md
6. docs/AUDIT_2026-09-09.md — если задача связана с аудитом/рисками.

PROJECT_STATUS.md — источник текущих версий, приоритетов и блокеров. Код/CI имеют приоритет над старой историей чатов.

## Архитектура

HomeSmoke Remote
    |
   MQTT
    |
    v
HomeSmoke Android
    |
 Bluetooth
    |
    v
Installed Arduino

Remote не должен напрямую придумывать или изменять Arduino-команды. HomeSmoke — компонент, непосредственно работающий с Arduino.

## Arduino-протокол — НЕ МЕНЯТЬ

Установленная Arduino сейчас не перепрошивается.

Подтверждённые команды:
- a0\0 — ручной режим.
- a1\0 — PID.
- a2\0 — старый встроенный Arduino Auto; Android Auto его не использует.
- a3\0 — STOP.
- kNN\0 — целая уставка камеры 0–100 °C.
- vNN\0 — целая ручная мощность 0–100%.
- pNNN\0, iNNN\0, dNNN\0, zNNN\0 — PID-параметры с масштабом ×100.

Терминатор \0 — два буквальных символа backslash + zero, не байт NUL.
Телеметрия: кадр |...|end.
Команды x1, x0, h относятся к неустановленной ревизии и не отправляются.

Новые команды, изменение формата или прошивки — только по отдельному разрешению владельца.

## Android Auto

Auto выполняется телефоном:
- рецепт, до 4 этапов, время, допуск и K/T-условия вычисляются Android;
- при старте один раз отправляется a1\0;
- затем отправляется только kNN\0;
- при смене этапа меняется kNN\0;
- таймер этапа считается подтверждённым только после свежей телеметрии mode=1 и нужной уставки;
- a2\0 не используется.

Существующие Auto-программы должны оставаться совместимыми.

## Критическое ограничение безопасности

Старая Arduino не имеет Android-watchdog. При физической потере Bluetooth телефон может не доставить a3\0, и контроллер способен сохранить последнюю PID-уставку.

Поэтому Auto нельзя считать безопасным без наблюдения до отдельного аппаратного решения.

## Android

Минимум: Android 6+ (minSdk 23). Android 4/5 и Legacy исключены.

Инструchain:
- JDK 17
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- Kotlin 2.2.21

Remote полностью Kotlin + Compose. HomeSmoke пока гибрид Java/Kotlin; Kotlin-миграцию продолжать постепенно после стабилизации.

## Сборка

gradle :core:test :app:testDebugUnitTest :remote:testDebugUnitTest
gradle :app:assembleDebug :remote:assembleDebug

Для release/cross-cutting validation обязательно проверить core/app/remote tests и обе APK-сборки, затем GitHub Actions и APK.

CI не заменяет реальный Bluetooth/MQTT/Arduino/нагревательный тест.

## Git и ветки

Рабочая ветка: homesmoke-native.
main не изменять и не merge без прямого разрешения владельца.

Не удалять файлы и не публиковать новые release/версии без разрешения. Любое функциональное/UI-изменение требует versionCode/versionName bump и записи в CHANGELOG.md. Документация/CI-only изменения версии APK не повышают.

HomeSmoke 2.5.0 сохраняется как rollback.

## Результат последнего полевого испытания HomeSmoke 2.6.18

03.10.2026 обработан журнал длительностью около 3 ч 24 мин 46 с: 2529 диагностических событий. Bluetooth-разрывов в журнале нет; PID и смена уставок подтверждены; 3/3 remote-setpoint получили ACK `applied`; временные MQTT/Internet-сбои пережиты с восстановлением; финальный `a3\0` подтверждён `mode=3` и `heater_power=0`.

Полный отчёт: `docs/testing/FIELD_TEST_HOMESMOKE_2_6_18_2026-10-03.md`.

Оговорка: `auto_state=STOPPED` и `auto_stage_index=-1` весь журнал, поэтому полноценный Android Auto этим испытанием не подтверждён.

## Первый следующий шаг

Повторный длительный полевой тест HomeSmoke Remote 2.4.8 в фоне.

Проверить:
- телеметрию;
- MQTT reconnect;
- потерю/возврат Интернета;
- удалённую уставку;
- финальный ACK applied;
- STOP;
- длительный deep-background/Doze.

Если подтверждается ограничение Android background lifecycle, отдельно рассмотреть foreground service. Не совмещать это с UI redesign или большой Kotlin-миграцией.

## Диагностика

При проблеме локализовать весь путь:

UI
↓
Business logic
↓
MQTT publish
↓
HomeSmoke receive
↓
Bluetooth command
↓
Arduino
↓
Arduino telemetry
↓
HomeSmoke parser
↓
MQTT response
↓
Remote

Не предполагать причину без воспроизведения.

## Правила для нового разработчика

НЕЛЬЗЯ:
- перепрошивать Arduino;
- менять Arduino protocol;
- добавлять ESP32;
- использовать a2 для Android Auto;
- отправлять x1/x0/h;
- ломать существующие Auto JSON;
- менять application IDs;
- снижать minSdk;
- менять main;
- смешивать protocol change, UI redesign и большой refactor в один release.

МОЖНО:
- анализировать код;
- исправлять воспроизводимые дефекты;
- добавлять тесты;
- делать локальный рефакторинг;
- собирать APK;
- обновлять документацию;
- выполнять CI и field validation по правилам проекта.

## Ссылки

Repository: https://github.com/bizard2000/bizard_kopt
Working branch: https://github.com/bizard2000/bizard_kopt/tree/homesmoke-native
PROJECT_STATUS: https://github.com/bizard2000/bizard_kopt/blob/homesmoke-native/PROJECT_STATUS.md
AGENTS: https://github.com/bizard2000/bizard_kopt/blob/homesmoke-native/AGENTS.md
CHANGELOG: https://github.com/bizard2000/bizard_kopt/blob/homesmoke-native/CHANGELOG.md
Decisions: https://github.com/bizard2000/bizard_kopt/blob/homesmoke-native/docs/DECISIONS.md
Arduino protocol: https://github.com/bizard2000/bizard_kopt/blob/homesmoke-native/docs/protocol/COMMAND_PROTOCOL_HOMESMOKE.md
HomeSmoke: https://github.com/bizard2000/bizard_kopt/tree/homesmoke-native/app
HomeSmoke Remote: https://github.com/bizard2000/bizard_kopt/tree/homesmoke-native/remote
Core: https://github.com/bizard2000/bizard_kopt/tree/homesmoke-native/core
CI: https://github.com/bizard2000/bizard_kopt/tree/homesmoke-native/.github/workflows

## Стартовая команда

Открой AGENTS.md и PROJECT_STATUS.md. Проверь текущую ветку homesmoke-native. Затем продолжай с первого незавершённого пункта, не изменяя Arduino protocol и не трогая main.
