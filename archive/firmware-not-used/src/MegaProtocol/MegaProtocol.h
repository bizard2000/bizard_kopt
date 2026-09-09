#pragma once
#include <Arduino.h>
#include <ArduinoJson.h>

void megaProtocol_init();
void megaProtocol_sendHandshake();
void megaProtocol_sendCommand(const JsonDocument& doc);
void megaProtocol_readSerial();