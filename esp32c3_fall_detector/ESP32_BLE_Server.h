#ifndef ESP32_BLE_SERVER_H
#define ESP32_BLE_SERVER_H

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>

#if defined(CONFIG_BLUEDROID_ENABLED)
#include <BLE2902.h>
#endif

#define DEFAULT_SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define DEFAULT_CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

class ESP32_BLE_Server : public BLEServerCallbacks, public BLECharacteristicCallbacks {
private:
    BLEServer* pServer = nullptr;
    BLECharacteristic* pCharacteristic = nullptr;

#if defined(CONFIG_BLUEDROID_ENABLED)
    BLE2902* pCccd = nullptr;
#endif

    volatile bool deviceConnected = false;
    volatile bool subscribed = false;
    volatile bool wasConnected = false;
    volatile unsigned long connectedAt = 0;
    volatile unsigned long subscribedAt = 0;

    static const uint8_t RX_SLOTS = 8;
    static const size_t RX_MAX = 64;
    char rxQueue[RX_SLOTS][RX_MAX] = {{0}};
    volatile uint8_t rxHead = 0;
    volatile uint8_t rxTail = 0;
    volatile uint8_t rxCount = 0;
    portMUX_TYPE dataMux = portMUX_INITIALIZER_UNLOCKED;
    void onConnect(BLEServer* server) override {
        (void)server;
        deviceConnected = true;
        connectedAt = millis();
    }

    void onDisconnect(BLEServer* server) override {
        (void)server;
        deviceConnected = false;
        subscribed = false;
        connectedAt = 0;
        subscribedAt = 0;
    }

    void onWrite(BLECharacteristic* characteristic) override {
        String value = characteristic->getValue();
        size_t length = value.length();
        if (length == 0) {
            return;
        }
        if (length > RX_MAX - 1) {
            length = RX_MAX - 1;
        }

        portENTER_CRITICAL(&dataMux);
        if (rxCount == RX_SLOTS) {
            rxTail = (rxTail + 1) % RX_SLOTS;
            rxCount--;
        }
        memcpy(rxQueue[rxHead], value.c_str(), length);
        rxQueue[rxHead][length] = '\0';
        rxHead = (rxHead + 1) % RX_SLOTS;
        rxCount++;
        portEXIT_CRITICAL(&dataMux);
    }

#if defined(CONFIG_NIMBLE_ENABLED)
    void onSubscribe(BLECharacteristic* characteristic, ble_gap_conn_desc* desc,
                     uint16_t subValue) override {
        (void)characteristic;
        (void)desc;
        subscribed = (subValue != 0);
        subscribedAt = subscribed ? millis() : 0;
    }
#endif

public:
    bool begin(const char* deviceName = "Korgan-C3") {
        BLEDevice::init(deviceName);

        pServer = BLEDevice::createServer();
        if (pServer == nullptr) {
            return false;
        }
        pServer->setCallbacks(this);

        BLEService* service = pServer->createService(DEFAULT_SERVICE_UUID);
        if (service == nullptr) {
            return false;
        }

        pCharacteristic = service->createCharacteristic(
            DEFAULT_CHARACTERISTIC_UUID,
            BLECharacteristic::PROPERTY_READ |
                BLECharacteristic::PROPERTY_WRITE |
                BLECharacteristic::PROPERTY_WRITE_NR |
                BLECharacteristic::PROPERTY_NOTIFY
        );
        if (pCharacteristic == nullptr) {
            return false;
        }
        pCharacteristic->setCallbacks(this);
        pCharacteristic->setValue("READY");

#if defined(CONFIG_BLUEDROID_ENABLED)
        pCccd = new BLE2902();
        pCharacteristic->addDescriptor(pCccd);
#endif

        service->start();

        BLEAdvertising* advertising = BLEDevice::getAdvertising();
        advertising->addServiceUUID(DEFAULT_SERVICE_UUID);
        advertising->setScanResponse(true);
        advertising->setMinPreferred(0x06);
        BLEDevice::startAdvertising();
        return true;
    }

    void loop() {
        if (!deviceConnected && wasConnected) {
            wasConnected = false;
            delay(300);
            BLEDevice::startAdvertising();
        } else if (deviceConnected && !wasConnected) {
            wasConnected = true;
        }
    }

    bool isConnected() const {
        return deviceConnected;
    }

    bool isSubscribed() const {
        if (!deviceConnected) {
            return false;
        }
#if defined(CONFIG_BLUEDROID_ENABLED)
        return pCccd != nullptr && pCccd->getNotifications();
#elif defined(CONFIG_NIMBLE_ENABLED)
        return subscribed;
#else
        return true;
#endif
    }

    unsigned long connectedFor() const {
        return deviceConnected ? (millis() - connectedAt) : 0;
    }

    unsigned long subscribedFor() const {
        return subscribed ? (millis() - subscribedAt) : 0;
    }

    bool send(const String& message) {
        if (pCharacteristic == nullptr || !deviceConnected) {
            return false;
        }
        pCharacteristic->setValue(message.c_str());
        pCharacteristic->notify();
        return true;
    }

    bool takeReceived(String& out) {
        char local[RX_MAX] = {0};
        bool available;

        portENTER_CRITICAL(&dataMux);
        available = rxCount > 0;
        if (available) {
            memcpy(local, rxQueue[rxTail], RX_MAX);
            rxTail = (rxTail + 1) % RX_SLOTS;
            rxCount--;
        }
        portEXIT_CRITICAL(&dataMux);

        if (available) {
            out = String(local);
        }
        return available;
    }

    bool hasNewData() {
        portENTER_CRITICAL(&dataMux);
        bool available = rxCount > 0;
        portEXIT_CRITICAL(&dataMux);
        return available;
    }

    void clearReceived() {
        portENTER_CRITICAL(&dataMux);
        rxHead = 0;
        rxTail = 0;
        rxCount = 0;
        rxQueue[0][0] = '\0';
        portEXIT_CRITICAL(&dataMux);
    }
};

#endif
