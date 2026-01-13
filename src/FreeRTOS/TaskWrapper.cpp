#include "TaskWrapper.h"
#include "AppConfig/AppConfig.h"
#include "MegaProtocol/MegaProtocol.h"
#include <ArduinoJson.h>
#include "freertos/task.h"

const unsigned long PING_INTERVAL_MS = 4000;

static void sendPing() {
    JsonDocument doc;
    doc["ping"] = 1; 
    megaProtocol_sendCommand(doc);
}

static void keepaliveTask(void *pvParameters) {
    Serial.println("[Task] Keepalive task started.");
    sendPing();

    for (;;) {
        vTaskDelay(PING_INTERVAL_MS / portTICK_PERIOD_MS);
        sendPing();
        
        // Мигаем только в режиме AP для индикации
        if (currentMode == "AP (Hotspot)") {
           digitalWrite(BLUE_LED_PIN, !digitalRead(BLUE_LED_PIN));
        }
    }
}

void startKeepaliveTask() {
    // ВАЖНО: ESP32-C3 имеет только Core 0.
    // Увеличим стек до 4096, так как JSON операции требуют памяти.
    xTaskCreatePinnedToCore(
        keepaliveTask,      
        "KeepaliveTask",    
        4096,               
        NULL,               
        1,                  
        NULL,               
        1
    );
}