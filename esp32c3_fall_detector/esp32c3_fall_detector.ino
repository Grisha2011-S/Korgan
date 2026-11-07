#include "ESP32_BLE_Server.h"
#include "MPU6050_Lite.h"

#include <Preferences.h>
#include <math.h>

#define BUTTON_PIN        0
#define LED_PIN           1
#define BOARD_LED_PIN     8
#define BUZZER_PIN        2
#define SDA_PIN           4
#define SCL_PIN           3

#define ENABLE_BATTERY_ADC  0
#define BATTERY_ADC_PIN     5
#define BATTERY_DIVIDER     2.0f
#define BATTERY_ADC_SAMPLES 8

#if ENABLE_BATTERY_ADC && (BATTERY_ADC_PIN > 4)
#warning "ESP32-C3: BATTERY_ADC_PIN is on ADC2, battery level will read 0%"
#endif

#define THRESH_FREE_FALL_G      0.55f
#define MIN_FREE_FALL_MS        65
#define MAX_FREE_FALL_MS        700

#define THRESH_IMPACT_G         2.8f
#define DIRECT_IMPACT_G         4.8f
#define THRESH_GYRO_TUMBLE_DPS  130.0f

#define REST_SETTLE_DELAY_MS    400
#define REST_DURATION_MS        1500
#define REST_MAX_GYRO_DPS       45.0f
#define REST_MAX_ACCEL_DELTA_G  0.40f

#define CPU_FREQ_MHZ            80
#define ENABLE_SMART_GYRO_SLEEP true
#define LOOP_DELAY_MS           5
#define GYRO_STARTUP_MS         35
#define ALARM_COOLDOWN_MS       4000
#define SOS_COOLDOWN_MS         1500
#define BUTTON_DEBOUNCE_MS      20
#define SENSOR_RETRY_MS         5000
#define SENSOR_FAIL_LIMIT       5

#define FW_VERSION              "3.0.1"
#define DEVICE_NAME             "Korgan-C3"
#define MAX_PENDING_EVENTS      8
#define ACK_RETRY_INTERVAL_MS   5000
#define READY_FALLBACK_MS       1500

enum PendingKind : uint8_t {
    EVENT_FALL = 0,
    EVENT_SOS = 1
};

struct PendingEvent {
    uint32_t seq;
    uint8_t kind;
    int8_t battery;
    uint32_t uptime;
};

enum FallState : uint8_t {
    STATE_IDLE,
    STATE_FREE_FALL,
    STATE_REST_SETTLE,
    STATE_REST_CHECK
};

ESP32_BLE_Server bleServer;
MPU6050_Lite mpu;
Preferences preferences;

PendingEvent pendingEvents[MAX_PENDING_EVENTS];
uint8_t pendingCount = 0;
uint32_t nextSequence = 0;
bool storageReady = false;

bool sensorReady = false;
uint8_t sensorFailCount = 0;
unsigned long lastSensorRetry = 0;
unsigned long lastSensorRead = 0;

FallState fsmState = STATE_IDLE;
unsigned long stateStart = 0;
float maxGyroDuringFall = 0.0f;
float maxImpact = 0.0f;

bool gyroSleeping = false;
unsigned long stillSince = 0;
unsigned long gyroReadyAt = 0;

bool alarmActive = false;
bool alarmIsSos = false;
unsigned long alarmStart = 0;

int buttonStable = HIGH;
int buttonLastRead = HIGH;
unsigned long buttonLastChange = 0;
unsigned long lastFallAlarm = 0;
unsigned long lastSosAlarm = 0;

bool lastBleConnected = false;
bool lastBleSubscribed = false;
bool readySent = false;
unsigned long connectStartedAt = 0;
unsigned long lastPendingSendAt = 0;

bool sensorErrorReported = false;
unsigned long lastTelemetryAt = 0;

void initBuzzer() {
#if defined(ESP_ARDUINO_VERSION_MAJOR) && (ESP_ARDUINO_VERSION_MAJOR >= 3)
    ledcAttach(BUZZER_PIN, 1000, 8);
    ledcWrite(BUZZER_PIN, 0);
#else
    ledcSetup(0, 1000, 8);
    ledcAttachPin(BUZZER_PIN, 0);
    ledcWrite(0, 0);
#endif
}

void buzzer(bool on) {
#if defined(ESP_ARDUINO_VERSION_MAJOR) && (ESP_ARDUINO_VERSION_MAJOR >= 3)
    ledcWrite(BUZZER_PIN, on ? 128 : 0);
#else
    ledcWrite(0, on ? 128 : 0);
#endif
}

void stopAlarm() {
    alarmActive = false;
    digitalWrite(LED_PIN, LOW);
    digitalWrite(BOARD_LED_PIN, HIGH);
    buzzer(false);
}

void startAlarm(bool sos) {
    alarmActive = true;
    alarmIsSos = sos;
    alarmStart = millis();
}

void updateAlarm(unsigned long now) {
    if (!alarmActive) {
        return;
    }

    unsigned long duration = alarmIsSos ? 1800UL : 900UL;
    unsigned long elapsed = now - alarmStart;

    if (elapsed >= duration) {
        stopAlarm();
        return;
    }

    bool on = (elapsed % 250UL) < 150UL;
    digitalWrite(LED_PIN, on ? HIGH : LOW);
    digitalWrite(BOARD_LED_PIN, on ? LOW : HIGH);
    buzzer(on);
}

#if ENABLE_BATTERY_ADC
static const float LIPO_VOLTS[]   = {3.27f, 3.61f, 3.69f, 3.71f, 3.73f, 3.75f, 3.77f, 3.79f, 3.80f,
                                     3.82f, 3.84f, 3.85f, 3.87f, 3.91f, 3.95f, 3.98f, 4.02f, 4.08f,
                                     4.11f, 4.15f, 4.20f};
static const uint8_t LIPO_PERCENT[] = {0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70,
                                       75, 80, 85, 90, 95, 100};
#endif

int readBatteryPercent() {
#if ENABLE_BATTERY_ADC
    uint32_t sum = 0;
    for (uint8_t i = 0; i < BATTERY_ADC_SAMPLES; ++i) {
        sum += analogReadMilliVolts(BATTERY_ADC_PIN);
    }
    float batteryVolts = (sum / (float)BATTERY_ADC_SAMPLES / 1000.0f) * BATTERY_DIVIDER;

    const uint8_t points = sizeof(LIPO_PERCENT) / sizeof(LIPO_PERCENT[0]);
    if (batteryVolts <= LIPO_VOLTS[0]) return 0;
    if (batteryVolts >= LIPO_VOLTS[points - 1]) return 100;
    for (uint8_t i = 1; i < points; ++i) {
        if (batteryVolts < LIPO_VOLTS[i]) {
            float t = (batteryVolts - LIPO_VOLTS[i - 1]) / (LIPO_VOLTS[i] - LIPO_VOLTS[i - 1]);
            return (int)(LIPO_PERCENT[i - 1] + t * (LIPO_PERCENT[i] - LIPO_PERCENT[i - 1]) + 0.5f);
        }
    }
    return 100;
#else
    return -1;
#endif
}

const char* kindName(uint8_t kind) {
    return kind == EVENT_SOS ? "SOS" : "FALL";
}

String serializeEvent(const PendingEvent& event) {
    return String(kindName(event.kind)) + ":" + String(event.seq) + ":" +
           String((int)event.battery) + ":" + String(event.uptime);
}

bool parseEvent(const String& text, PendingEvent& event) {
    int p1 = text.indexOf(':');
    if (p1 < 0) return false;
    int p2 = text.indexOf(':', p1 + 1);
    if (p2 < 0) return false;
    int p3 = text.indexOf(':', p2 + 1);
    if (p3 < 0) return false;

    String kind = text.substring(0, p1);
    long seq = text.substring(p1 + 1, p2).toInt();
    long battery = text.substring(p2 + 1, p3).toInt();
    long uptime = text.substring(p3 + 1).toInt();

    if (seq <= 0) return false;

    event.kind = (kind == "SOS") ? EVENT_SOS : EVENT_FALL;
    event.seq = (uint32_t)seq;
    event.battery = (int8_t)constrain(battery, -1, 100);
    event.uptime = (uint32_t)uptime;
    return true;
}

String serializeQueue() {
    String out;
    for (uint8_t i = 0; i < pendingCount; ++i) {
        if (i > 0) out += ';';
        out += serializeEvent(pendingEvents[i]);
    }
    return out;
}

void loadQueue() {
    storageReady = preferences.begin("korgan", false);
    if (!storageReady) {
        Serial.println("[NVS] Не удалось открыть хранилище, очередь только в RAM");
        return;
    }

    nextSequence = preferences.getUInt("next_seq", 0);
    String raw = preferences.getString("queue", "");
    pendingCount = 0;

    int start = 0;
    while (start < (int)raw.length() && pendingCount < MAX_PENDING_EVENTS) {
        int end = raw.indexOf(';', start);
        if (end < 0) end = raw.length();
        String item = raw.substring(start, end);
        if (item.length() > 0 && parseEvent(item, pendingEvents[pendingCount])) {
            pendingCount++;
        }
        start = end + 1;
    }

    Serial.printf("[NVS] next_seq=%lu, pending=%u\n",
                  (unsigned long)nextSequence, pendingCount);
}

void saveQueue() {
    if (!storageReady) {
        return;
    }
    preferences.putUInt("next_seq", nextSequence);
    preferences.putString("queue", serializeQueue());
}

void sendPendingNow();

void enqueueEvent(uint8_t kind) {
    PendingEvent event;
    event.seq = ++nextSequence;
    event.kind = kind;
    event.battery = (int8_t)readBatteryPercent();
    event.uptime = (uint32_t)(millis() / 1000UL);

    if (pendingCount >= MAX_PENDING_EVENTS) {
        for (uint8_t i = 1; i < pendingCount; ++i) {
            pendingEvents[i - 1] = pendingEvents[i];
        }
        pendingCount--;
    }

    pendingEvents[pendingCount++] = event;
    saveQueue();

    Serial.printf("[ALARM] %s #%lu сохранено в NVS (battery=%d, pending=%u)\n",
                  kindName(kind), (unsigned long)event.seq, (int)event.battery,
                  pendingCount);

    if (readySent && bleServer.isConnected()) {
        sendPendingNow();
        lastPendingSendAt = millis();
    }
}

void acknowledgeEvent(uint32_t seq) {
    if (seq == 0) {
        return;
    }

    for (uint8_t i = 0; i < pendingCount; ++i) {
        if (pendingEvents[i].seq == seq) {
            for (uint8_t j = i + 1; j < pendingCount; ++j) {
                pendingEvents[j - 1] = pendingEvents[j];
            }
            pendingCount--;
            saveQueue();
            Serial.printf("[ACK] Событие #%lu удалено (осталось %u)\n",
                          (unsigned long)seq, pendingCount);
            return;
        }
    }

    Serial.printf("[ACK] #%lu уже подтверждено\n", (unsigned long)seq);
}

String statusPacket() {
    String out = "INFO:FW=" FW_VERSION;
    out += ";SENSOR=";
    out += sensorReady ? "OK" : "ERROR";
    out += ";QUEUE=" + String(pendingCount);
    out += ";BAT=" + String(readBatteryPercent());
    return out;
}

void sendStatus() {
    if (bleServer.isConnected()) {
        bleServer.send(statusPacket());
    }
}

void sendReady() {
    if (!bleServer.isConnected()) {
        return;
    }
    bleServer.send(String("READY:") + FW_VERSION);
    sendStatus();
    if (!sensorReady) {
        bleServer.send("ERROR:SENSOR");
        sensorErrorReported = true;
    } else {
        sensorErrorReported = false;
    }
    Serial.println("[BLE] READY отправлен телефону");
}

void sendPendingNow() {
    if (!bleServer.isConnected()) {
        return;
    }
    for (uint8_t i = 0; i < pendingCount; ++i) {
        String packet = serializeEvent(pendingEvents[i]);
        if (bleServer.send(packet)) {
            Serial.printf("[BLE] TX %s\n", packet.c_str());
        }
    }
}

void serviceBle(unsigned long now) {
    bool connected = bleServer.isConnected();

    if (!connected) {
        if (lastBleConnected) {
            Serial.println("[BLE] Телефон отключился");
        }
        lastBleConnected = false;
        lastBleSubscribed = false;
        readySent = false;
        return;
    }

    if (!lastBleConnected) {
        lastBleConnected = true;
        connectStartedAt = now;
        readySent = false;
        lastBleSubscribed = bleServer.isSubscribed();
        lastPendingSendAt = 0;
        Serial.println("[BLE] Телефон подключился");
    }

    bool subscribed = bleServer.isSubscribed();
    if (subscribed != lastBleSubscribed) {
        lastBleSubscribed = subscribed;
        if (subscribed) {
            Serial.println("[BLE] Телефон подписан на уведомления");
            readySent = false;
            lastPendingSendAt = 0;
        }
    }

    if (!readySent && (subscribed || (now - connectStartedAt >= READY_FALLBACK_MS))) {
        sendReady();
        readySent = true;
        lastPendingSendAt = 0;
    }

    if (readySent &&
        (lastPendingSendAt == 0 ||
         (now - lastPendingSendAt) >= ACK_RETRY_INTERVAL_MS)) {
        lastPendingSendAt = now;
        sendPendingNow();
    }
}

void handleCommand(String command) {
    command.trim();
    if (command.length() == 0) {
        return;
    }

    String upper = command;
    upper.toUpperCase();
    Serial.printf("[BLE] RX %s\n", command.c_str());

    if (upper == "PING") {
        bleServer.send("PONG");
        return;
    }

    if (upper.startsWith("ACK:")) {
        long seq = upper.substring(4).toInt();
        if (seq > 0) {
            acknowledgeEvent((uint32_t)seq);
        }
        return;
    }

    if (upper == "STATUS" || upper == "INFO") {
        sendStatus();
        return;
    }

    if (upper == "TEST") {
        if (millis() - lastFallAlarm >= ALARM_COOLDOWN_MS) {
            lastFallAlarm = millis();
            startAlarm(false);
            enqueueEvent(EVENT_FALL);
        }
        return;
    }

    bleServer.send("ERROR:CMD");
}

void pollBleCommands() {
    String command;
    while (bleServer.takeReceived(command)) {
        handleCommand(command);
    }
}

void emitSos() {
    unsigned long now = millis();
    if (now - lastSosAlarm < SOS_COOLDOWN_MS) {
        Serial.println("[SOS] Нажатие в пределах cooldown, пропущено");
        return;
    }
    lastSosAlarm = now;
    Serial.println("[SOS] Кнопка нажата");
    startAlarm(true);
    enqueueEvent(EVENT_SOS);
}

void checkButton(unsigned long now) {
    int reading = digitalRead(BUTTON_PIN);

    if (reading != buttonLastRead) {
        buttonLastRead = reading;
        buttonLastChange = now;
    }

    if ((now - buttonLastChange) < BUTTON_DEBOUNCE_MS) {
        return;
    }

    if (reading != buttonStable) {
        buttonStable = reading;
        if (buttonStable == LOW) {
            emitSos();
        }
    }
}

void updateGyroPower(float accelMag, unsigned long now) {
#if ENABLE_SMART_GYRO_SLEEP
    if (fsmState != STATE_IDLE) {
        stillSince = 0;
        if (gyroSleeping) {
            mpu.setGyroStandby(false);
            gyroSleeping = false;
            gyroReadyAt = now + GYRO_STARTUP_MS;
        }
        return;
    }

    if (fabsf(accelMag - 1.0f) < 0.12f) {
        if (stillSince == 0) {
            stillSince = now;
        } else if (!gyroSleeping && (now - stillSince > 3000UL)) {
            mpu.setGyroStandby(true);
            gyroSleeping = true;
            Serial.println("[POWER] Гироскоп переведён в standby");
        }
    } else {
        stillSince = 0;
        if (gyroSleeping) {
            mpu.setGyroStandby(false);
            gyroSleeping = false;
            gyroReadyAt = now + GYRO_STARTUP_MS;
            Serial.println("[POWER] Гироскоп разбужен движением");
        }
    }
#else
    (void)accelMag;
    (void)now;
    if (gyroSleeping) {
        mpu.setGyroStandby(false);
        gyroSleeping = false;
    }
#endif
}

void enterIdle() {
    fsmState = STATE_IDLE;
    maxGyroDuringFall = 0.0f;
    maxImpact = 0.0f;
}

void emitFall(const char* reason) {
    unsigned long now = millis();
    if (now - lastFallAlarm < ALARM_COOLDOWN_MS) {
        Serial.printf("[FSM] %s, но действует cooldown\n", reason);
        return;
    }
    lastFallAlarm = now;
    Serial.printf("[FSM] ПАДЕНИЕ: %s (удар %.2fg, гиро %.1f°/s)\n",
                  reason, maxImpact, maxGyroDuringFall);
    startAlarm(false);
    enqueueEvent(EVENT_FALL);
}

void markSensorFailure() {
    sensorFailCount++;
    if (sensorFailCount < SENSOR_FAIL_LIMIT) {
        return;
    }

    if (sensorReady) {
        Serial.println("[SENSOR] MPU6050 перестал отвечать");
    }
    sensorReady = false;
    enterIdle();
    if (bleServer.isConnected() && !sensorErrorReported) {
        bleServer.send("ERROR:SENSOR");
        sensorErrorReported = true;
    }
}

void trySensorRecovery(unsigned long now) {
    if (sensorReady || (now - lastSensorRetry < SENSOR_RETRY_MS)) {
        return;
    }
    lastSensorRetry = now;
    Serial.println("[SENSOR] Повторная инициализация MPU6050...");

    Wire.end();
    if (mpu.begin(SDA_PIN, SCL_PIN, ACCEL_FS_8G)) {
        sensorReady = true;
        sensorFailCount = 0;
        gyroSleeping = false;
        gyroReadyAt = now + GYRO_STARTUP_MS;
        stillSince = 0;
        enterIdle();
        Serial.println("[SENSOR] MPU6050 восстановлен");
        if (bleServer.isConnected()) {
            sendReady();
        }
    }
}

void processFallDetection(unsigned long now) {
    if (!sensorReady) {
        return;
    }

    MPUData sensor;
    if (!mpu.readAll(sensor)) {
        markSensorFailure();
        return;
    }
    sensorFailCount = 0;

    float accelMag = sqrtf(sensor.accelX * sensor.accelX +
                           sensor.accelY * sensor.accelY +
                           sensor.accelZ * sensor.accelZ);

    updateGyroPower(accelMag, now);

    bool gyroValid = !gyroSleeping && (long)(now - gyroReadyAt) >= 0;
    float gyroMag = gyroValid ? sqrtf(sensor.gyroX * sensor.gyroX +
                                      sensor.gyroY * sensor.gyroY +
                                      sensor.gyroZ * sensor.gyroZ)
                              : 0.0f;

    if (now - lastTelemetryAt >= 1000UL) {
        lastTelemetryAt = now;
        Serial.printf("[SENSOR] |A|=%.2fg |G|=%.1f°/s state=%u\n",
                      accelMag, gyroMag, (unsigned)fsmState);
    }

    switch (fsmState) {
        case STATE_IDLE: {
            if (accelMag < THRESH_FREE_FALL_G) {
                fsmState = STATE_FREE_FALL;
                stateStart = now;
                maxGyroDuringFall = gyroMag;
                maxImpact = 0.0f;
            } else if (accelMag > DIRECT_IMPACT_G) {
                maxImpact = accelMag;
                fsmState = STATE_REST_SETTLE;
                stateStart = now;
                Serial.printf("[FSM] Прямой удар %.2fg → проверка покоя\n", accelMag);
            }
            break;
        }

        case STATE_FREE_FALL: {
            if (gyroMag > maxGyroDuringFall) {
                maxGyroDuringFall = gyroMag;
            }
            unsigned long fallDuration = now - stateStart;

            if (accelMag > THRESH_IMPACT_G) {
                if (fallDuration < MIN_FREE_FALL_MS) {
                    Serial.printf("[FSM] Слишком короткая невесомость %lu мс — шаг/прыжок\n",
                                  fallDuration);
                    enterIdle();
                    break;
                }
                if (maxGyroDuringFall < THRESH_GYRO_TUMBLE_DPS) {
                    Serial.printf("[FSM] Нет вращения (%.1f°/s) — не падение\n",
                                  maxGyroDuringFall);
                    enterIdle();
                    break;
                }

                maxImpact = accelMag;
                Serial.printf("[FSM] Невесомость %lu мс + удар %.2fg + гиро %.1f°/s\n",
                              fallDuration, maxImpact, maxGyroDuringFall);
                fsmState = STATE_REST_SETTLE;
                stateStart = now;
            } else if (fallDuration > MAX_FREE_FALL_MS) {
                Serial.println("[FSM] Таймаут невесомости — сброс");
                enterIdle();
            }
            break;
        }

        case STATE_REST_SETTLE: {
            if (accelMag > maxImpact) {
                maxImpact = accelMag;
            }
            if (now - stateStart >= REST_SETTLE_DELAY_MS) {
                fsmState = STATE_REST_CHECK;
                stateStart = now;
            }
            break;
        }

        case STATE_REST_CHECK: {
            if (gyroMag > REST_MAX_GYRO_DPS ||
                fabsf(accelMag - 1.0f) > REST_MAX_ACCEL_DELTA_G) {
                Serial.println("[FSM] Человек двигается — ложная тревога отменена");
                enterIdle();
                break;
            }
            if (now - stateStart >= REST_DURATION_MS) {
                emitFall("человек неподвижен после удара");
                enterIdle();
            }
            break;
        }
    }
}

void setup() {
    setCpuFrequencyMhz(CPU_FREQ_MHZ);

    Serial.begin(115200);
    delay(300);
    Serial.println();
    Serial.println("=================================================");
    Serial.println("  Korgan ESP32-C3 fall detector / SOS — v" FW_VERSION);
    Serial.println("=================================================");

    pinMode(BUTTON_PIN, INPUT_PULLUP);
    delay(10);
    buttonStable = digitalRead(BUTTON_PIN);
    buttonLastRead = buttonStable;
    buttonLastChange = millis();
    Serial.printf("[BOOT] Кнопка GPIO%d: %s\n", BUTTON_PIN,
                  buttonStable == LOW ? "LOW" : "HIGH");

    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);
    pinMode(BOARD_LED_PIN, OUTPUT);
    digitalWrite(BOARD_LED_PIN, HIGH);
    initBuzzer();

#if ENABLE_BATTERY_ADC
    analogSetPinAttenuation(BATTERY_ADC_PIN, ADC_11db);
#endif

    loadQueue();

    if (!bleServer.begin(DEVICE_NAME)) {
        Serial.println("[BLE] Ошибка инициализации BLE");
    }

    sensorReady = mpu.begin(SDA_PIN, SCL_PIN, ACCEL_FS_8G);
    if (sensorReady) {
        Serial.printf("[SENSOR] MPU6050 готов (SDA=%d, SCL=%d)\n", SDA_PIN, SCL_PIN);
    } else {
        Serial.printf("[SENSOR] MPU6050 не найден (SDA=%d, SCL=%d)\n", SDA_PIN, SCL_PIN);
        lastSensorRetry = millis();
    }

    digitalWrite(LED_PIN, HIGH);
    digitalWrite(BOARD_LED_PIN, LOW);
    delay(120);
    digitalWrite(LED_PIN, LOW);
    digitalWrite(BOARD_LED_PIN, HIGH);

    lastSensorRead = millis();
    Serial.println("[BOOT] Система запущена");
}

void loop() {
    unsigned long now = millis();

    bleServer.loop();
    serviceBle(now);
    pollBleCommands();

    updateAlarm(now);
    checkButton(now);

    trySensorRecovery(now);

    if (now - lastSensorRead >= LOOP_DELAY_MS) {
        lastSensorRead = now;
        processFallDetection(now);
    }

    delay(1);
}
