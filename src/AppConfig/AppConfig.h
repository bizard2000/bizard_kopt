#pragma once
#include <Arduino.h>
#include "freertos/semphr.h" // Для SemaphoreHandle_t

// ============ НАСТРОЙКИ СВЯЗИ И ПИНОВ ============
#define RXD2 16
#define TXD2 17
#define MEGA_BAUD 115200

const int BLUE_LED_PIN = 22;

// Настройки Wi-Fi 
extern const char* WIFI_SSID;
extern const char* WIFI_PASSWORD;

// Настройки Точки Доступа (AP)
extern const char* AP_SSID;
extern const char* AP_PASS;

// ============ ГЛОБАЛЬНЫЕ ДАННЫЕ ============
// Структура для хранения последнего статуса от Mega
struct SystemStatus {
  float T_air = 0.0;
  float T1 = 0.0;
  float T2 = 0.0;
  int heater = 0; 
  int fan = 0;
  int inlet = 0; 
  int outlet = 0;
  int error = 0;
  String state = "IDLE";
  // Новые поля для расширенного макета
  float target_T_air = 0.0;      // Целевая температура камеры
  float target_T1 = 0.0;         // Целевая температура продукта 1
  float target_T2 = 0.0;         // Целевая температура продукта 2
  int heater_pwm = 0;            // ШИМ ТЭНа (0-100%)
  int fan_pwm = 0;               // ШИМ вентилятора (0-100%)
  int smoke_damper = 0;          // Заслонка дыма (0-закрыта, 1-открыта)
  int vent_damper = 0;           // Заслонка вентиляции (0-закрыта, 1-открыта)
  int current_stage = 0;         // Текущий этап программы
  int total_stages = 0;          // Всего этапов
  String stage_name = "";        // Название этапа
  unsigned long stage_time_elapsed = 0;   // Время этапа (сек)
  unsigned long stage_time_total = 0;     // Общее время этапа (сек)
  String program_name = "";      // Имя текущей программы
  unsigned long process_time = 0; // Общее время процесса (сек)
  
  // PID настройки
  float pid_Kp = 0.0;
  float pid_Ki = 0.0;
  float pid_Kd = 0.0;
  String pid_source = "Камера 1";
  
  // WiFi информация
  int wifi_rssi = 0;
  String wifi_mode = "STA";
};

// Объявление глобальных переменных (инициализация в MegaProtocol.cpp)
extern SystemStatus megaStatus;
extern String currentMode;
extern SemaphoreHandle_t serial1Mutex;