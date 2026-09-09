#include "WebServer.h"
#include "AppConfig/AppConfig.h"
#include "MegaProtocol/MegaProtocol.h"
#include <WiFi.h>
#include <ArduinoJson.h>
#include <LittleFS.h>
#include <PsychicHttp.h>

// Глобальные переменные
PsychicWebSocketHandler websocketHandler;
String currentProgramName = "Не выбрана";
unsigned long processStartTime = 0;

// Дополнительные глобальные переменные
float currentTargetTemp = 0.0;
float currentProductTarget = 0.0;
int heaterPWM = 0;
int fanPWM = 0;
int currentStage = 0;
int stageTimeRemaining = 0;

// --- Вспомогательные функции ---
static void sendBoolCommand(const char* key) {
    JsonDocument doc;
    doc[key] = true;
    megaProtocol_sendCommand(doc);
}

static esp_err_t serveFile(PsychicRequest *request, PsychicResponse *response, const char* path, const char* contentType) {
    String fullPath;
    
    if (strcmp(path, "/") == 0 || strlen(path) == 0) {
        fullPath = "/index.html";
    } else {
        if (path[0] != '/') {
            fullPath = "/" + String(path);
        } else {
            fullPath = path;
        }
    }
    
    Serial.printf("[FS] Opening file: %s\n", fullPath.c_str());
    
    File file = LittleFS.open(fullPath, "r");
    if (!file) {
        if (strstr(path, "favicon.ico") != NULL) {
            response->setCode(404);
            return response->send("");
        }
        
        Serial.printf("[FS] File not found: %s\n", fullPath.c_str());
        response->setCode(404);
        return response->send("File not found");
    }
    
    String content = file.readString();
    file.close();
    
    response->setContentType(contentType);
    response->setContent(content.c_str());
    return response->send();
}

// ИСПРАВЛЕНО: Функция readEventLog - безопасное чтение
static String readEventLog() {
    if (!LittleFS.exists("/eventlog.json")) {
        return "[]"; // Возвращаем пустой массив, если файла нет
    }
    
    File file = LittleFS.open("/eventlog.json", "r");
    if (!file) {
        return "[]";
    }
    String content = file.readString();
    file.close();
    return content;
}

// ИСПРАВЛЕНО: Функция writeEventLog - безопасная запись без ошибок при отсутствии файла
static void writeEventLog(String type, String message) {
    // Не записываем логи при подключении WebSocket, чтобы избежать ошибок
    if (type == "WS") {
        return; // Пропускаем логи WebSocket событий
    }
    
    JsonDocument doc;
    
    // Проверяем существование файла
    if (LittleFS.exists("/eventlog.json")) {
        File file = LittleFS.open("/eventlog.json", "r");
        if (file) {
            DeserializationError error = deserializeJson(doc, file);
            file.close();
            if (error) {
                // Если файл поврежден, создаем новый
                doc["log"] = JsonArray();
            }
        }
    } else {
        // Файл не существует - создаем новый
        doc["log"] = JsonArray();
    }
    
    JsonArray log = doc["log"].as<JsonArray>();
    JsonObject entry = log.add<JsonObject>();
    entry["timestamp"] = millis() / 1000;
    entry["type"] = type;
    entry["message"] = message;
    
    // Ограничиваем размер журнала (последние 100 записей)
    if (log.size() > 100) {
        // Создаем новый массив с последними 100 записями
        JsonDocument newDoc;
        JsonArray newLog = newDoc["log"].to<JsonArray>();
        for (size_t i = log.size() - 100; i < log.size(); i++) {
            newLog.add(log[i]);
        }
        doc = newDoc;
    }
    
    File file = LittleFS.open("/eventlog.json", "w");
    if (file) {
        serializeJson(doc, file);
        file.close();
    }
}

// --- HTTP обработчики ---
static esp_err_t handleRoot(PsychicRequest *request, PsychicResponse *response) {
    return serveFile(request, response, "/", "text/html");
}

static esp_err_t handleFile(PsychicRequest *request, PsychicResponse *response) {
    String path = request->uri();
    
    // ВАЖНО: Исключаем WebSocket-путь из обработки статических файлов
    if (path == "/ws") {
        response->setCode(400);
        return response->send("Bad Request");
    }
    
    const char* contentType = "text/plain";
    if (path.endsWith(".html") || path.endsWith(".htm")) {
        contentType = "text/html";
    } else if (path.endsWith(".css")) {
        contentType = "text/css";
    } else if (path.endsWith(".js")) {
        contentType = "application/javascript";
    } else if (path.endsWith(".json")) {
        contentType = "application/json";
    } else if (path.endsWith(".png")) {
        contentType = "image/png";
    } else if (path.endsWith(".ico")) {
        contentType = "image/x-icon";
    }
    
    return serveFile(request, response, path.c_str(), contentType);
}

static esp_err_t handleApiStatus(PsychicRequest *request, PsychicResponse *response) {
    JsonDocument doc;
    
    doc["T_air"] = megaStatus.T_air;
    doc["T1"] = megaStatus.T1;
    doc["T2"] = megaStatus.T2;
    doc["heater"] = megaStatus.heater;
    doc["fan"] = megaStatus.fan;
    doc["inlet"] = megaStatus.inlet;
    doc["outlet"] = megaStatus.outlet;
    doc["error"] = megaStatus.error;
    doc["state"] = megaStatus.state;
    doc["mode"] = currentMode;
    doc["wifi_rssi"] = WiFi.RSSI();
    
    doc["target_T_air"] = megaStatus.target_T_air;
    doc["target_T1"] = megaStatus.target_T1;
    doc["heater_pwm"] = megaStatus.heater_pwm;
    doc["fan_pwm"] = megaStatus.fan_pwm;
    doc["smoke_damper"] = megaStatus.smoke_damper;
    doc["vent_damper"] = megaStatus.vent_damper;
    doc["current_stage"] = megaStatus.current_stage;
    doc["total_stages"] = megaStatus.total_stages;
    doc["stage_name"] = megaStatus.stage_name;
    doc["stage_time_elapsed"] = megaStatus.stage_time_elapsed;
    doc["stage_time_total"] = megaStatus.stage_time_total;
    doc["program_name"] = megaStatus.program_name;
    doc["process_time"] = megaStatus.process_time;
    
    doc["pid_Kp"] = megaStatus.pid_Kp;
    doc["pid_Ki"] = megaStatus.pid_Ki;
    doc["pid_Kd"] = megaStatus.pid_Kd;
    doc["pid_source"] = megaStatus.pid_source;

    String responseStr;
    serializeJson(doc, responseStr);
    
    return response->send(200, "application/json", responseStr.c_str());
}

static esp_err_t handleApiCommand(PsychicRequest *request, PsychicResponse *response) {
    PsychicWebParameter* p = request->getParam("cmd");
    
    if (p != nullptr) {
        String cmd = p->value();
        
        if (cmd == "start") {
            sendBoolCommand("start");
            processStartTime = millis();
            writeEventLog("INFO", "Процесс запущен");
        }
        else if (cmd == "stop") {
            sendBoolCommand("stop");
            processStartTime = 0;
            writeEventLog("INFO", "Процесс остановлен");
        }
        else if (cmd == "pause") sendBoolCommand("pause");
        else if (cmd == "resume") sendBoolCommand("resume");
        else if (cmd == "reboot") {
             JsonDocument d;
             d["reboot"] = true; 
             megaProtocol_sendCommand(d);
        }
        else if (cmd == "load_program") {
            PsychicWebParameter* prog = request->getParam("program");
            if (prog != nullptr) {
                JsonDocument d;
                d["load_program"] = prog->value();
                megaProtocol_sendCommand(d);
                currentProgramName = prog->value();
                writeEventLog("INFO", "Загружена программа: " + prog->value());
            }
        }
        
        return response->send(200, "text/plain", "OK");
    } 
    return response->send(400, "text/plain", "No command");
}

static esp_err_t handleApiSet(PsychicRequest *request, PsychicResponse *response) {
    PsychicWebParameter* p = request->getParam("setpoint");
    PsychicWebParameter* type = request->getParam("type");

    if (p != nullptr && type != nullptr) {
        JsonDocument doc;
        String setType = type->value();
        
        if (setType == "chamber") {
            doc["setpoint_chamber"] = p->value().toFloat();
            megaStatus.target_T_air = p->value().toFloat();
        }
        else if (setType == "product") {
            doc["setpoint_product"] = p->value().toFloat();
            megaStatus.target_T1 = p->value().toFloat();
        }
        
        megaProtocol_sendCommand(doc);
        writeEventLog("SET", "Установлена цель: " + setType + " = " + p->value());
        return response->send(200, "text/plain", "OK");
    }
    return response->send(400, "text/plain", "Bad Params");
}

static esp_err_t handleApiPrograms(PsychicRequest *request, PsychicResponse *response) {
    File file = LittleFS.open("/programs.json", "r");
    if (!file) {
        JsonDocument demo;
        JsonArray programs = demo["programs"].to<JsonArray>();
        
        JsonObject prog1 = programs.add<JsonObject>();
        prog1["id"] = 1;
        prog1["name"] = "Сушка";
        prog1["description"] = "Сушка продуктов перед копчением";
        JsonArray stages1 = prog1["stages"].to<JsonArray>();
        
        JsonObject stage1 = stages1.add<JsonObject>();
        stage1["name"] = "Прогрев";
        stage1["temp"] = 50;
        stage1["time"] = 1800;
        stage1["smoke"] = 0;
        stage1["vent"] = 1;
        
        JsonObject stage2 = stages1.add<JsonObject>();
        stage2["name"] = "Сушка";
        stage2["temp"] = 60;
        stage2["time"] = 7200;
        stage2["smoke"] = 0;
        stage2["vent"] = 1;
        
        JsonObject prog2 = programs.add<JsonObject>();
        prog2["id"] = 2;
        prog2["name"] = "Горячее копчение";
        prog2["description"] = "Классическое горячее копчение";
        
        String demoJson;
        serializeJson(demo, demoJson);
        
        file = LittleFS.open("/programs.json", "w");
        file.print(demoJson);
        file.close();
        
        response->setContentType("application/json");
        return response->send(demoJson.c_str());
    }
    
    String content = file.readString();
    file.close();
    
    response->setContentType("application/json");
    return response->send(content.c_str());
}

static esp_err_t handleApiLog(PsychicRequest *request, PsychicResponse *response) {
    JsonDocument doc;
    doc["log"] = readEventLog();
    
    String responseStr;
    serializeJson(doc, responseStr);
    
    return response->send(200, "application/json", responseStr.c_str());
}

static esp_err_t handleApiPID(PsychicRequest *request, PsychicResponse *response) {
    if (request->method() == HTTP_GET) {
        JsonDocument doc;
        doc["Kp"] = megaStatus.pid_Kp;
        doc["Ki"] = megaStatus.pid_Ki;
        doc["Kd"] = megaStatus.pid_Kd;
        doc["source"] = megaStatus.pid_source;
        
        String responseStr;
        serializeJson(doc, responseStr);
        return response->send(200, "application/json", responseStr.c_str());
    }
    else if (request->method() == HTTP_POST) {
        String body = request->body();
        JsonDocument doc;
        DeserializationError error = deserializeJson(doc, body);
        
        if (error) {
            return response->send(400, "text/plain", "Invalid JSON");
        }
        
        if (doc["Kp"].is<float>()) megaStatus.pid_Kp = doc["Kp"].as<float>();
        if (doc["Ki"].is<float>()) megaStatus.pid_Ki = doc["Ki"].as<float>();
        if (doc["Kd"].is<float>()) megaStatus.pid_Kd = doc["Kd"].as<float>();
        if (doc["source"].is<String>()) megaStatus.pid_source = doc["source"].as<String>();
        
        JsonDocument megaDoc;
        megaDoc["pid_update"] = true;
        megaDoc["Kp"] = megaStatus.pid_Kp;
        megaDoc["Ki"] = megaStatus.pid_Ki;
        megaDoc["Kd"] = megaStatus.pid_Kd;
        megaDoc["source"] = megaStatus.pid_source;
        megaProtocol_sendCommand(megaDoc);
        
        writeEventLog("PID", "Обновлены PID настройки");
        return response->send(200, "text/plain", "OK");
    }
    
    return response->send(405, "text/plain", "Method not allowed");
}

// --- Инициализация WiFi ---
void wifi_init() {
    Serial.print("Подключение к WiFi: ");
    Serial.println(WIFI_SSID);
    
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    
    Serial.println("WiFi STA запущен. Резервный AP через 20 сек...");
}

// --- Инициализация сервера ---
void webServer_init(PsychicHttpServer& server) {
    // Отладочная печать списка файлов
    Serial.println("=== Files in LittleFS ===");
    File root = LittleFS.open("/");
    File file = root.openNextFile();
    while(file) {
        Serial.printf("  %s (%d bytes)\n", file.name(), file.size());
        file = root.openNextFile();
    }
    Serial.println("========================");
    
    server.begin();
    Serial.println("Psychic HTTP сервер запущен.");

    delay(100);

    // ВАЖНО: Регистрируем WebSocket ПЕРВЫМ
    server.on("/ws", &websocketHandler);

    // Затем регистрируем статические файлы
    server.on("/", HTTP_GET, handleRoot);
    server.on("/index.html", HTTP_GET, [](PsychicRequest *request, PsychicResponse *response) {
        return serveFile(request, response, "/index.html", "text/html");
    });
    server.on("/style.css", HTTP_GET, [](PsychicRequest *request, PsychicResponse *response) {
        return serveFile(request, response, "/style.css", "text/css");
    });
    server.on("/app.js", HTTP_GET, [](PsychicRequest *request, PsychicResponse *response) {
        return serveFile(request, response, "/app.js", "application/javascript");
    });
    server.on("/sw.js", HTTP_GET, [](PsychicRequest *request, PsychicResponse *response) {
        return serveFile(request, response, "/sw.js", "application/javascript");
    });
    server.on("/manifest.json", HTTP_GET, [](PsychicRequest *request, PsychicResponse *response) {
        return serveFile(request, response, "/manifest.json", "application/json");
    });
    server.on("/offline.html", HTTP_GET, [](PsychicRequest *request, PsychicResponse *response) {
        return serveFile(request, response, "/offline.html", "text/html");
    });
    server.on("/uPlot.iife.min.js", HTTP_GET, [](PsychicRequest *request, PsychicResponse *response) {
        return serveFile(request, response, "/uPlot.iife.min.js", "application/javascript");
    });
    server.on("/uPlot.min.css", HTTP_GET, [](PsychicRequest *request, PsychicResponse *response) {
        return serveFile(request, response, "/uPlot.min.css", "text/css");
    });
    
    // Общий обработчик для остальных файлов
    server.on("*", HTTP_GET, handleFile);
    
    // Регистрация API обработчиков
    server.on("/api/status", HTTP_GET, handleApiStatus);
    server.on("/api/cmd", HTTP_GET, handleApiCommand);
    server.on("/api/set", HTTP_GET, handleApiSet);
    server.on("/api/programs", HTTP_GET, handleApiPrograms);
    server.on("/api/log", HTTP_GET, handleApiLog);
    server.on("/api/pid", HTTP_GET, handleApiPID);
    server.on("/api/pid", HTTP_POST, handleApiPID);

    // ИСПРАВЛЕНО: WebSocket обработчики без записи в журнал при подключении/отключении
    websocketHandler.onOpen([](PsychicWebSocketClient *client) {
        Serial.printf("[WS] Клиент подключен: %s (ID: %d)\n", client->remoteIP().toString().c_str(), client->socket());
        // НЕ вызываем writeEventLog для WebSocket событий
    });

    websocketHandler.onClose([](PsychicWebSocketClient *client) {
        Serial.printf("[WS] Клиент отключен: %s (ID: %d)\n", client->remoteIP().toString().c_str(), client->socket());
        // НЕ вызываем writeEventLog для WebSocket событий
    });

    websocketHandler.onFrame([](PsychicWebSocketRequest *request, httpd_ws_frame *frame) -> esp_err_t {
        PsychicWebSocketClient *client = request->client();

        if (frame->type == HTTPD_WS_TYPE_TEXT && frame->len > 0) {
            if (frame->len > 2048) {
                Serial.printf("[WS] Сообщение слишком большое: %d байт\n", frame->len);
                return ESP_OK;
            }

            char* payload = (char*)malloc(frame->len + 1);
            if (!payload) return ESP_ERR_NO_MEM;
            
            memcpy(payload, frame->payload, frame->len);
            payload[frame->len] = '\0';
            String msg = String(payload);
            free(payload);

            Serial.printf("[WS ← RX] %s\n", msg.c_str());

            JsonDocument doc;
            DeserializationError error = deserializeJson(doc, msg);

            if (error) {
                Serial.printf("[WS] Ошибка JSON: %s\n", error.c_str());
                return ESP_OK;
            }

            if (doc["command"].is<String>()) {
                String cmd = doc["command"].as<String>();

                if (cmd == "start") {
                    sendBoolCommand("start");
                    processStartTime = millis();
                    writeEventLog("CMD", "Команда: START");
                }
                else if (cmd == "stop") {
                    sendBoolCommand("stop");
                    processStartTime = 0;
                    writeEventLog("CMD", "Команда: STOP");
                }
                else if (cmd == "pause") {
                    sendBoolCommand("pause");
                    writeEventLog("CMD", "Команда: PAUSE");
                }
                else if (cmd == "resume") {
                    sendBoolCommand("resume");
                    writeEventLog("CMD", "Команда: RESUME");
                }
                else if (cmd == "load_program") {
                    if (doc["program"].is<String>()) {
                        String program = doc["program"].as<String>();
                        JsonDocument forward;
                        forward["load_program"] = program;
                        megaProtocol_sendCommand(forward);
                        currentProgramName = program;
                        writeEventLog("CMD", "Загружена программа: " + program);
                    }
                }
                else if (cmd == "set_target") {
                    if (doc["type"].is<String>() && doc["value"].is<float>()) {
                        String type = doc["type"].as<String>();
                        float value = doc["value"].as<float>();
                        
                        JsonDocument forward;
                        if (type == "chamber") {
                            forward["setpoint_chamber"] = value;
                            megaStatus.target_T_air = value;
                        } else if (type == "product") {
                            forward["setpoint_product"] = value;
                            megaStatus.target_T1 = value;
                        }
                        megaProtocol_sendCommand(forward);
                        writeEventLog("SET", "Установка цели: " + type + " = " + String(value));
                    }
                }
                else if (cmd == "save_pid") {
                    if (doc["Kp"].is<float>() && doc["Ki"].is<float>() && doc["Kd"].is<float>()) {
                        megaStatus.pid_Kp = doc["Kp"].as<float>();
                        megaStatus.pid_Ki = doc["Ki"].as<float>();
                        megaStatus.pid_Kd = doc["Kd"].as<float>();
                        if (doc["source"].is<String>()) {
                            megaStatus.pid_source = doc["source"].as<String>();
                        } else {
                            megaStatus.pid_source = "Камера 1";
                        }
                        
                        JsonDocument forward;
                        forward["pid_update"] = true;
                        forward["Kp"] = megaStatus.pid_Kp;
                        forward["Ki"] = megaStatus.pid_Ki;
                        forward["Kd"] = megaStatus.pid_Kd;
                        forward["source"] = megaStatus.pid_source;
                        megaProtocol_sendCommand(forward);
                        writeEventLog("PID", "Сохранены PID настройки");
                    }
                }
                else if (cmd == "ping") {
                    JsonDocument pongDoc;
                    pongDoc["type"] = "pong";
                    String pongStr;
                    serializeJson(pongDoc, pongStr);
                    client->sendMessage(pongStr.c_str());
                    return ESP_OK;
                }
                else {
                    Serial.printf("[WS] Неизвестная команда: %s\n", cmd.c_str());
                    JsonDocument err;
                    err["error"] = "Unknown command";
                    err["command"] = cmd;
                    String errStr;
                    serializeJson(err, errStr);
                    client->sendMessage(errStr.c_str());
                    return ESP_OK;
                }

                JsonDocument ackDoc;
                ackDoc["ack"] = cmd;
                String ackStr;
                serializeJson(ackDoc, ackStr);
                client->sendMessage(ackStr.c_str());
            } else {
                Serial.println("[WS] Сообщение без команды или неверный формат");
            }
        }

        return ESP_OK;
    });

    Serial.println("WebSocket готов: ws://<IP>/ws");
}

// === Рассылка статуса всем клиентам ===
void broadcastStatus() {
    static unsigned long lastBroadcast = 0;
    if (millis() - lastBroadcast < 2000) return;

    if (processStartTime > 0) {
        megaStatus.process_time = (millis() - processStartTime) / 1000;
    }
    
    if (megaStatus.current_stage > 0 && megaStatus.stage_time_total > 0) {
        megaStatus.stage_time_elapsed = megaStatus.process_time % megaStatus.stage_time_total;
    }

    JsonDocument doc;
    JsonObject status = doc["status"].to<JsonObject>();

    status["T_air"] = megaStatus.T_air;
    status["T1"] = megaStatus.T1;
    status["T2"] = megaStatus.T2;
    status["heater"] = megaStatus.heater;
    status["fan"] = megaStatus.fan;
    status["inlet"] = megaStatus.inlet;
    status["outlet"] = megaStatus.outlet;
    status["state"] = megaStatus.state;
    
    status["target_T_air"] = megaStatus.target_T_air;
    status["target_T1"] = megaStatus.target_T1;
    status["heater_pwm"] = megaStatus.heater_pwm;
    status["fan_pwm"] = megaStatus.fan_pwm;
    status["smoke_damper"] = megaStatus.smoke_damper;
    status["vent_damper"] = megaStatus.vent_damper;
    status["current_stage"] = megaStatus.current_stage;
    status["stage_name"] = megaStatus.stage_name;
    status["stage_time_elapsed"] = megaStatus.stage_time_elapsed;
    status["stage_time_total"] = megaStatus.stage_time_total;
    status["program_name"] = megaStatus.program_name;
    status["process_time"] = megaStatus.process_time;
    status["wifi_rssi"] = WiFi.RSSI();

    String payload;
    serializeJson(doc, payload);

    websocketHandler.sendAll(payload.c_str());

    lastBroadcast = millis();
}