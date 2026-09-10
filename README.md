# HomeSmoke Android

Репозиторий двух отдельных Android-приложений домашней коптильни:

| Приложение | Модуль | Текущая версия | Назначение |
|---|---|---:|---|
| HomeSmoke | `app/` | 2.6.18 (28) | Bluetooth-связь с установленной Arduino, Android Auto, PID/ручной режим, история и диагностика |
| HomeSmoke Remote | `remote/` | 2.4.6 (44) | Удалённый мониторинг и команды через MQTT, Kotlin + Jetpack Compose |
| Общее ядро | `core/` | — | Разбор телеметрии, правила протокола и логика Android Auto |

Обе программы поддерживают Android 6+ (`minSdk 23`). Активная ветка разработки и сборки — `homesmoke-native`; ветка `main` не изменяется без отдельного разрешения владельца.

## Важно о контроллере

В этом Android-проекте **нет ESP32**. Фактически установленная Arduino сейчас не перепрошивается. HomeSmoke работает только с её подтверждённым старым протоколом. Auto выполняется на Android: при запуске один раз включается PID командой `a1`, далее отправляются только уставки камеры `kNN`; встроенный Arduino Auto `a2` не используется. STOP — `a3`.

Подробности: [решения проекта](docs/DECISIONS.md) и [протокол](docs/protocol/COMMAND_PROTOCOL_HOMESMOKE.md).

## С чего продолжать работу

Перед любыми изменениями прочитать:

1. [PROJECT_STATUS.md](PROJECT_STATUS.md) — что готово, что проверено и что делать дальше.
2. [CHANGELOG.md](CHANGELOG.md) — история версий.
3. [docs/DECISIONS.md](docs/DECISIONS.md) — ограничения, которые нельзя потерять при переходе в новый чат.
4. [docs/AUDIT_2026-09-09.md](docs/AUDIT_2026-09-09.md) — текущая техническая ревизия.

## Сборка и тесты

Требуются JDK 17 и Gradle 8.9:

```bash
gradle :core:test :app:testDebugUnitTest :remote:testDebugUnitTest
gradle :app:assembleDebug :remote:assembleDebug
```

GitHub Actions автоматически проверяет обе программы и публикует APK после изменений в `homesmoke-native`. Текущие и резервные сборки перечислены в [dist/README.md](dist/README.md).

## Безопасность эксплуатации

Старая Arduino не имеет Android-watchdog. При физической потере Bluetooth телефон не может доставить STOP, и контроллер может сохранить последнюю PID-уставку. Реальные Auto-тесты проводить только под наблюдением.
