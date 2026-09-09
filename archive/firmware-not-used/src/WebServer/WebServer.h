#pragma once
#include <Arduino.h>
#include <PsychicHttp.h>

extern PsychicWebSocketHandler websocketHandler;

void wifi_init();
void webServer_init(PsychicHttpServer& server);
void broadcastStatus();