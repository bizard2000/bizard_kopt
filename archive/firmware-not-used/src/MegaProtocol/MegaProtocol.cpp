#include "MegaProtocol.h"
#include "AppConfig/AppConfig.h"
#include <WiFi.h> 

// Инициализация глобальных переменных
SystemStatus megaStatus;
String currentMode = "Unknown";
SemaphoreHandle_t serial1Mutex = NULL;

void megaProtocol_init() {
    Serial1.begin(MEGA_BAUD, SERIAL_8N1, RXD2, TXD2);
    serial1Mutex = xSemaphoreCreateMutex();
    Serial.println("MegaProtocol: Serial1 and Mutex initialized.");
}

// Отправка любого JSON документа
void megaProtocol_sendCommand(const JsonDocument& doc) {
    if (xSemaphoreTake(serial1Mutex, portMAX_DELAY) == pdTRUE) {
        // Отправка в Mega
        serializeJson(doc, Serial1);
        Serial1.write('\n'); 

        // Отладка
        Serial.print("[TX -> Mega]: ");
        serializeJson(doc, Serial);
        Serial.println();
        
        xSemaphoreGive(serial1Mutex);
    }
}

void megaProtocol_sendHandshake() {
    JsonDocument doc;
    doc["hello"] = 1; 
    megaProtocol_sendCommand(doc);
}

static void parseMegaMessage(String& json) {
    JsonDocument doc; 
    DeserializationError error = deserializeJson(doc, json);

    if (error) {
        Serial.printf("[RX Error]: JSON parse failed: %s\n", error.c_str());
        return;
    }

    // Новый стиль ArduinoJson 7: проверяем наличие ключа через тип
    if (doc["status"].is<JsonObject>()) {
        JsonObject status = doc["status"];
        // Обновление глобального статуса
        megaStatus.T_air = status["T_air"] | 0.0;
        megaStatus.T1 = status["T1"] | 0.0;
        megaStatus.T2 = status["T2"] | 0.0;
        megaStatus.heater = status["heater"] | 0;
        megaStatus.fan = status["fan"] | 0;
        megaStatus.inlet = status["inlet"] | 0;
        megaStatus.outlet = status["outlet"] | 0;
        megaStatus.error = status["error"] | 0;
        megaStatus.state = status["state"].as<String>();

        // Новые поля (если отправляются Mega)
        megaStatus.target_T_air = status["target_T_air"] | megaStatus.target_T_air;
        megaStatus.target_T1 = status["target_T1"] | megaStatus.target_T1;
        megaStatus.heater_pwm = status["heater_pwm"] | 0;
        megaStatus.fan_pwm = status["fan_pwm"] | 0;
        megaStatus.smoke_damper = status["smoke_damper"] | 0;
        megaStatus.vent_damper = status["vent_damper"] | 0;
        megaStatus.current_stage = status["current_stage"] | 0;
        megaStatus.program_name = status["program_name"] | "";
        
        // Если Mega не отправляет этапы, генерируем локально
        if (megaStatus.current_stage > 0 && megaStatus.stage_time_total == 0) {
            megaStatus.stage_time_total = 3600; // 1 час по умолчанию
        }
        
        Serial.printf("[STATUS] %s | Камера: %.1f/%.1f | Продукт: %.1f/%.1f\n", 
            megaStatus.state.c_str(), 
            megaStatus.T_air, megaStatus.target_T_air,
            megaStatus.T1, megaStatus.target_T1);
    }
    else if (!doc["pong"].isNull()) {
        // Игнорируем pong
    }
    else if (doc["program_data"].is<JsonObject>()) {
        // Обработка данных программы
        JsonObject program = doc["program_data"];
        // Сохранить программу в LittleFS
    }
    else {
        Serial.print("[RX]: ");
        Serial.println(json);
    }
}

// Чтение Serial1 (вызывается из loop)
void megaProtocol_readSerial() {
    static String inputBuffer = "";
    while (Serial1.available()) {
        char c = (char)Serial1.read();
        if (c == '\n') {
            parseMegaMessage(inputBuffer);
            inputBuffer = "";
        } else {
            inputBuffer += c;
        }
    }
}