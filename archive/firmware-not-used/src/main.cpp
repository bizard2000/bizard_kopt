#include <Arduino.h>
#include "FreeRTOS/TaskWrapper.h"
#include <PsychicHttp.h>
#include <LittleFS.h>
#include "AppConfig/AppConfig.h"
#include "MegaProtocol/MegaProtocol.h"
#include "WebServer/WebServer.h"
#include <WiFi.h>

PsychicHttpServer server;

// Флаги состояния
bool wifiConnected = false;
bool apModeActive = false;
bool serverStarted = false;
unsigned long wifiStartTime = 0;
// Функция для проверки файловой системы
void checkFilesystem() {
    Serial.println("Проверка файловой системы...");
    
    // Проверяем существование ключевых файлов
    const char* requiredFiles[] = {
        "/index.html",
        "/style.css",
        "/app.js",
        "/sw.js",
        "/manifest.json",
        "/offline.html",
        "/uPlot.iife.min.js",
        "/uPlot.min.css"
    };
    
    bool allFilesExist = true;
    for (int i = 0; i < sizeof(requiredFiles)/sizeof(requiredFiles[0]); i++) {
        if (LittleFS.exists(requiredFiles[i])) {
            Serial.printf("  ✓ %s\n", requiredFiles[i]);
        } else {
            Serial.printf("  ✗ %s (не найден)\n", requiredFiles[i]);
            allFilesExist = false;
        }
    }
    
    if (!allFilesExist) {
        Serial.println("❌ Некоторые файлы не найдены в файловой системе!");
        Serial.println("   Загрузите файлы через: pio run --target uploadfs");
    } else {
        Serial.println("✅ Все файлы найдены");
    }
}

// Инициализация программ
void initPrograms() {
    if (!LittleFS.exists("/programs.json")) {
        Serial.println("Создание файла программ...");
        // Создаем пустой файл программ
        File file = LittleFS.open("/programs.json", "w");
        if (file) {
            file.print("{\"programs\":[]}");
            file.close();
            Serial.println("Файл программ создан");
        }
    }
    
    if (!LittleFS.exists("/eventlog.json")) {
        Serial.println("Создание файла журнала...");
        File file = LittleFS.open("/eventlog.json", "w");
        if (file) {
            file.print("{\"log\":[]}");
            file.close();
            Serial.println("Файл журнала создан");
        }
    }
}


void setup() {
    Serial.begin(115200);
    delay(1000);
    pinMode(BLUE_LED_PIN, OUTPUT);
    digitalWrite(BLUE_LED_PIN, HIGH);
    
    Serial.println("\n--- Modular ESP32 Controller Init ---");

    // 1. Файловая система
    if(!LittleFS.begin(true)) {
        Serial.println("❌ LittleFS Mount Failed");
        // Можно добавить светодиодную индикацию ошибки
        while(1) {
            digitalWrite(BLUE_LED_PIN, !digitalRead(BLUE_LED_PIN));
            delay(500);
        }
    }
    Serial.println("✅ LittleFS Mounted");

    // 2. Протоколы и WiFi
    megaProtocol_init();
    wifi_init();
    
    // 3. Задачи
    startKeepaliveTask();
    megaProtocol_sendHandshake();
    
    wifiStartTime = millis();
    Serial.println("System initialization complete!");
}

void loop() {
    // Основной обработчик протокола
    megaProtocol_readSerial();
    
    // === WiFi И СЕРВЕРНАЯ ЛОГИКА ===
    
    // Если ещё не подключились к WiFi и не запустили AP
    if (!wifiConnected && !apModeActive) {
        // Проверяем подключение к WiFi
        if (WiFi.status() == WL_CONNECTED) {
            wifiConnected = true;
            currentMode = "STA";
            Serial.print("✅ WiFi Connected! IP: ");
            Serial.println(WiFi.localIP());
        }
        // Таймаут 20 секунд для WiFi подключения
        else if (millis() - wifiStartTime > 20000) {
            // Fallback в AP режим
            Serial.println("\n🚫 WiFi connection timeout. Starting AP...");
            
            WiFi.disconnect(true);
            delay(100);
            WiFi.mode(WIFI_AP);
            
            if (WiFi.softAP(AP_SSID, AP_PASS)) {
                apModeActive = true;
                currentMode = "AP (Hotspot)";
                Serial.print("✅ AP Started! SSID: ");
                Serial.print(AP_SSID);
                Serial.print(", IP: ");
                Serial.println(WiFi.softAPIP());
                
                // Короткая пауза для стабилизации AP
                delay(1000);
            } else {
                Serial.println("❌ AP failed to start!");
            }
        }
    }
    
    // Запуск сервера при любом активном сетевом режиме
    if (!serverStarted && (wifiConnected || apModeActive)) {
        Serial.println("Starting PsychicHttp server...");
        webServer_init(server);
        serverStarted = true;
        Serial.println("✅ Web server ready!");
        
        // Первый вызов broadcastStatus только после инициализации сервера
        broadcastStatus();
    }
    
    // Обновление статуса WiFi
    megaStatus.wifi_rssi = WiFi.RSSI();
    
    // Периодическая рассылка статуса
    if (serverStarted) {
        broadcastStatus();
    }
    
    // Короткая пауза для планировщика FreeRTOS
    vTaskDelay(10 / portTICK_PERIOD_MS);
}