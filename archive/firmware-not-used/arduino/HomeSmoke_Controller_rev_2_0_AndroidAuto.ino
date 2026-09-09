#include <DigOut.h>
#include "max6675.h"
#include <OneWire.h>
#include <DallasTemperature.h>

// HomeSmoke controller revision 2.0
// Arduino is only the executor: sensors, manual power, PID and fail-safe stop.
// The automatic smoking program is intentionally REMOVED from Arduino and
// is executed by the Android application.
//
// Backward compatible commands kept from the original project:
//   a0     manual mode
//   a1     PID mode
//   a2     legacy auto request -> SAFE STOP (auto is no longer in Arduino)
//   a3     STOP / heater off
//   vNN    manual heater power 0..100 %
//   kNN    chamber setpoint 0..100 C
//   uNN    product setpoint storage 0..100 C (telemetry only)
//   pNN    kP = NN / 100
//   iNN    kI = NN / 100
//   dNN    kD = NN / 100
//   zNN    proportional zone = NN / 100
//
// New host-auto safety commands:
//   x1     arm Android-auto watchdog
//   x0     disarm Android-auto watchdog
//   h      heartbeat from Android while auto is running
// If x1 is armed and heartbeats disappear, the heater is switched off.
//
// Command terminators accepted:
//   1) real NUL byte 0x00
//   2) legacy literal two characters "\\0"

#define TEMP_DS_PERIOD_MS       800UL
#define TEMP_PROBES_PERIOD_MS   2000UL
#define PID_PERIOD_MS           800UL
#define TELEMETRY_PERIOD_MS     500UL
#define HOST_AUTO_TIMEOUT_MS    10000UL
#define T_PWM_MS                1500UL

DigOut outten(10);
byte zad = 0;
byte zad_r = 0;

unsigned long currentMillis = 0;
unsigned long preTempMillis = 0;
unsigned long preTempMillis2 = 0;
unsigned long prePIctlMillis = 0;
unsigned long preTelemetryMillis = 0;

// 0 = manual, 1 = PID, 3 = off. 2 is reserved/legacy and is treated as OFF.
int progMode = 3;
byte ust = 0;
byte ustv = 0;

// Android-auto watchdog. This is NOT an automatic program on Arduino.
bool hostAutoArmed = false;
unsigned long lastHostHeartbeat = 0;

String myString = "";
String myString1 = "";       // last accepted command, shown in telemetry
String rxBuffer = "";

#define THERMISTORPIN A0
#define THERMISTORNOMINAL 199000
#define TEMPERATURENOMINAL 25
#define NUMSAMPLES 5
#define BCOEFFICIENT 4267
#define SERIESRESISTOR 200000
int samples[NUMSAMPLES];
float tempv2 = 0.0;

int thermoDO = 4;
int thermoCS = 3;
int thermoCLK = 2;
MAX6675 thermocouple(thermoCLK, thermoCS, thermoDO);
float tempv = 0.0;

#define ONE_WIRE_BUS 12
#define TEMPERATURE_PRECISION 12
OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);
DeviceAddress Thermometer;
float temp = 0.0;

float kP = 10.0;
#define P_MIN 0.0
#define P_MAX 100.0
float kI = 0.02;
#define I_MIN 0.0
#define I_MAX 30.0
float kD = 1.0;
float d_ctl = 6.0;
#define OUT_MIN 0.0
#define OUT_MAX 100.0

float pidIntegral = 0.0;
float pidPrevError = 0.0;

void resetPid() {
  pidIntegral = 0.0;
  pidPrevError = 0.0;
  prePIctlMillis = 0;
}

uint8_t PIctl(float currentTemp, uint8_t setpoint) {
  float e = (float)setpoint - currentTemp;

  float p;
  if (currentTemp < (float)setpoint - d_ctl) p = P_MAX;
  else if (currentTemp > (float)setpoint) p = P_MIN;
  else p = kP * e;

  pidIntegral += kI * e;
  if (pidIntegral < I_MIN) pidIntegral = I_MIN;
  if (pidIntegral > I_MAX) pidIntegral = I_MAX;

  float d = kD * (e - pidPrevError);
  pidPrevError = e;

  float output = p + pidIntegral + d;
  if (output < OUT_MIN) output = OUT_MIN;
  if (output > OUT_MAX) output = OUT_MAX;
  return (uint8_t)output;
}

bool chamberSensorOk() {
  return !isnan(temp) && temp > -40.0 && temp < 150.0;
}

void setup() {
  sensors.begin();
  sensors.getAddress(Thermometer, 0);
  sensors.setResolution(Thermometer, TEMPERATURE_PRECISION);

  Serial.begin(19200);
  analogReference(EXTERNAL);

  rxBuffer.reserve(32);
  myString.reserve(32);
  myString1.reserve(32);
}

void loop() {
  currentMillis = millis();

  if (currentMillis - preTempMillis >= TEMP_DS_PERIOD_MS) {
    sensors.requestTemperatures();
    temp = sensors.getTempC(Thermometer);
    preTempMillis = currentMillis;
  }

  if (currentMillis - preTempMillis2 >= TEMP_PROBES_PERIOD_MS) {
    tempv = thermocouple.readCelsius();
    termistor();
    preTempMillis2 = currentMillis;
  }

  modeSelect();

  if (currentMillis > 3000UL) {
    outten.lpwm(T_PWM_MS, zad);
  }

  if (currentMillis - preTelemetryMillis >= TELEMETRY_PERIOD_MS) {
    sendTelemetry();
    preTelemetryMillis = currentMillis;
  }
}

// Frame is kept compatible with the original AIA:
// |temp|tempv|ust|ustv|zad|progMode|lastCommand|kP|kI|kD|d_ctl|tempv2|end
void sendTelemetry() {
  Serial.print("|");
  Serial.print(temp);
  Serial.print("|");
  Serial.print(tempv);
  Serial.print("|");
  Serial.print(ust);
  Serial.print("|");
  Serial.print(ustv);
  Serial.print("|");
  Serial.print(zad);
  Serial.print("|");
  Serial.print(progMode);
  Serial.print("|");
  Serial.print(myString1);
  Serial.print("|");
  Serial.print(kP);
  Serial.print("|");
  Serial.print(kI);
  Serial.print("|");
  Serial.print(kD);
  Serial.print("|");
  Serial.print(d_ctl);
  Serial.print("|");
  Serial.print(tempv2);
  Serial.print("|");
  Serial.println("end");
}

// Non-blocking receiver. Accepts both real NUL and old App Inventor "\\0".
void serialEvent() {
  while (Serial.available() > 0) {
    char c = (char)Serial.read();

    if (c == '\0') {
      acceptCommand(rxBuffer);
      rxBuffer = "";
      continue;
    }

    rxBuffer += c;

    int len = rxBuffer.length();
    if (len >= 2 && rxBuffer.charAt(len - 2) == '\\' && rxBuffer.charAt(len - 1) == '0') {
      rxBuffer.remove(len - 2);
      acceptCommand(rxBuffer);
      rxBuffer = "";
      continue;
    }

    if (rxBuffer.length() > 40) {
      rxBuffer = "";
    }
  }
}

void acceptCommand(String command) {
  command.trim();
  if (command.length() == 0) return;
  myString = command;
  myString1 = command;
  myRazbor();
}

int commandValue(const String &command) {
  if (command.length() < 2) return 0;
  return command.substring(1).toInt();
}

void setModeSafe(int requestedMode) {
  int oldMode = progMode;

  if (requestedMode == 0) progMode = 0;
  else if (requestedMode == 1) progMode = 1;
  else progMode = 3;  // a2 old Arduino-auto and a3 both become safe OFF

  if (progMode != oldMode) resetPid();
  if (progMode == 3) zad = 0;
}

void myRazbor() {
  if (myString.startsWith("a")) {
    setModeSafe(commandValue(myString));
  }
  else if (myString.startsWith("v")) {
    zad_r = (byte)constrain(commandValue(myString), 0, 100);
  }
  else if (myString.startsWith("k")) {
    byte newUst = (byte)constrain(commandValue(myString), 0, 100);
    if (newUst != ust) {
      ust = newUst;
      resetPid();
    }
  }
  else if (myString.startsWith("u")) {
    ustv = (byte)constrain(commandValue(myString), 0, 100);
  }
  else if (myString.startsWith("p")) {
    kP = (float)commandValue(myString) / 100.0;
    resetPid();
  }
  else if (myString.startsWith("i")) {
    kI = (float)commandValue(myString) / 100.0;
    resetPid();
  }
  else if (myString.startsWith("d")) {
    kD = (float)commandValue(myString) / 100.0;
    resetPid();
  }
  else if (myString.startsWith("z")) {
    d_ctl = (float)commandValue(myString) / 100.0;
    resetPid();
  }
  else if (myString.startsWith("x")) {
    int v = commandValue(myString);
    hostAutoArmed = (v == 1);
    if (hostAutoArmed) lastHostHeartbeat = millis();
  }
  else if (myString == "h") {
    if (hostAutoArmed) lastHostHeartbeat = millis();
  }

  myString = "";
}

void termistor() {
  uint8_t i;
  float average = 0.0;

  for (i = 0; i < NUMSAMPLES; i++) samples[i] = analogRead(THERMISTORPIN);
  for (i = 0; i < NUMSAMPLES; i++) average += samples[i];
  average /= NUMSAMPLES;

  if (average <= 0.0 || average >= 1023.0) {
    tempv2 = NAN;
    return;
  }

  average = 1023.0 / average - 1.0;
  average = SERIESRESISTOR / average;
  tempv2 = average / THERMISTORNOMINAL;
  tempv2 = log(tempv2);
  tempv2 /= BCOEFFICIENT;
  tempv2 += 1.0 / (TEMPERATURENOMINAL + 273.15);
  tempv2 = 1.0 / tempv2;
  tempv2 -= 273.15;
}

void modeSelect() {
  // If Android Auto disappears for >10 s, stop heating.
  if (hostAutoArmed && (unsigned long)(currentMillis - lastHostHeartbeat) > HOST_AUTO_TIMEOUT_MS) {
    hostAutoArmed = false;
    progMode = 3;
    zad = 0;
    resetPid();
  }

  // DS18B20 is safety-critical for PID.
  if (!chamberSensorOk()) {
    zad = 0;
    return;
  }

  switch (progMode) {
    case 0:
      zad = zad_r;
      break;

    case 1:
      pidControl();
      break;

    case 2:  // old Arduino Auto removed intentionally
    case 3:
    default:
      zad = 0;
      break;
  }
}

void pidControl() {
  if (currentMillis - prePIctlMillis >= PID_PERIOD_MS) {
    zad = PIctl(temp, ust);
    prePIctlMillis = currentMillis;
  }
}
