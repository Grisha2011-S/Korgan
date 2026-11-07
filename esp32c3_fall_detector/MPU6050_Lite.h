#ifndef MPU6050_LITE_H
#define MPU6050_LITE_H

#include <Arduino.h>
#include <Wire.h>
#include <stdint.h>

#define MPU6050_ADDR 0x68

// Регистры MPU-6050 / MPU-6500
#define MPU6050_REG_SMPLRT_DIV   0x19
#define MPU6050_REG_CONFIG       0x1A
#define MPU6050_REG_GYRO_CONFIG  0x1B
#define MPU6050_REG_ACCEL_CONFIG 0x1C
#define MPU6050_REG_ACCEL_XOUT_H 0x3B
#define MPU6050_REG_PWR_MGMT_1   0x6B
#define MPU6050_REG_PWR_MGMT_2   0x6C
#define MPU6050_REG_WHO_AM_I     0x75

// Диапазоны измерения акселерометра (AFS_SEL)
enum AccelRange : uint8_t {
    ACCEL_FS_2G  = 0x00, // ±2g  (16384 LSB/g) — насыщается при ударе
    ACCEL_FS_4G  = 0x08, // ±4g  (8192 LSB/g)
    ACCEL_FS_8G  = 0x10, // ±8g  (4096 LSB/g) — компромисс для падений
    ACCEL_FS_16G = 0x18  // ±16g (2048 LSB/g) — максимальный запас по удару
};

// Диапазоны измерения гироскопа (FS_SEL)
enum GyroRange : uint8_t {
    GYRO_FS_250DPS  = 0x00, // ±250 °/s  (131.0 LSB/°/s)
    GYRO_FS_500DPS  = 0x08, // ±500 °/s  (65.5 LSB/°/s)
    GYRO_FS_1000DPS = 0x10, // ±1000 °/s (32.8 LSB/°/s)
    GYRO_FS_2000DPS = 0x18  // ±2000 °/s (16.4 LSB/°/s)
};

struct MPUData {
    float accelX = 0.0f;
    float accelY = 0.0f;
    float accelZ = 0.0f;
    float gyroX = 0.0f;
    float gyroY = 0.0f;
    float gyroZ = 0.0f;
    float temp = 0.0f;
};

class MPU6050_Lite {
private:
    uint8_t address;
    bool initialized = false;
    bool gyroStandby = false;
    float accelScaleDivider = 4096.0f;
    float gyroScaleDivider = 131.0f;

    bool writeRegister(uint8_t reg, uint8_t value) {
        Wire.beginTransmission(address);
        Wire.write(reg);
        Wire.write(value);
        return Wire.endTransmission() == 0;
    }

    uint8_t readRegister(uint8_t reg) {
        Wire.beginTransmission(address);
        Wire.write(reg);
        if (Wire.endTransmission(false) != 0) {
            return 0;
        }
        if (Wire.requestFrom((uint8_t)address, (uint8_t)1) == 1) {
            return Wire.read();
        }
        return 0;
    }

    // Транзакция чтения 14 байт с несколькими попытками. На дешёвых клонах
    // MPU-6050 I2C-шина иногда теряет пакет, и одиночный сбой не должен
    // выглядеть как падение.
    bool readBurst(uint8_t* buffer, size_t length) {
        for (uint8_t attempt = 0; attempt < 3; ++attempt) {
            Wire.beginTransmission(address);
            Wire.write(MPU6050_REG_ACCEL_XOUT_H);
            if (Wire.endTransmission(false) != 0) {
                delay(1);
                continue;
            }

            size_t received = Wire.requestFrom((uint8_t)address, (uint8_t)length);
            if (received != length) {
                while (Wire.available()) {
                    Wire.read();
                }
                delay(1);
                continue;
            }

            for (size_t i = 0; i < length; ++i) {
                buffer[i] = (uint8_t)Wire.read();
            }
            return true;
        }
        return false;
    }

public:
    MPU6050_Lite(uint8_t addr = MPU6050_ADDR) : address(addr) {}

    bool begin(int sdaPin, int sclPin,
               AccelRange aRange = ACCEL_FS_8G,
               GyroRange gRange = GYRO_FS_250DPS,
               uint32_t frequency = 400000) {
        initialized = false;
        Wire.begin(sdaPin, sclPin, frequency);
        delay(20);

        // Проверяем, что устройство вообще есть на шине.
        Wire.beginTransmission(address);
        if (Wire.endTransmission() != 0) {
            return false;
        }

        // Полный сброс: выводит датчик из любого «зависшего» состояния,
        // в котором он мог остаться после перезагрузки МК без снятия питания.
        writeRegister(MPU6050_REG_PWR_MGMT_1, 0x80);
        delay(100);
        writeRegister(MPU6050_REG_PWR_MGMT_1, 0x00);
        delay(50);

        // Тактирование от PLL гироскопа X — стабильнее внутреннего RC.
        if (!writeRegister(MPU6050_REG_PWR_MGMT_1, 0x01)) {
            writeRegister(MPU6050_REG_PWR_MGMT_1, 0x00);
        }
        delay(20);

        // Разбудить все оси: после reset PWR_MGMT_2 может содержать 0x3F.
        if (!writeRegister(MPU6050_REG_PWR_MGMT_2, 0x00)) {
            return false;
        }
        gyroStandby = false;

        setAccelRange(aRange);
        setGyroRange(gRange);
        setDLPF(0x03);          // ~44 Гц: отсекает вибрацию шагов
        setSampleRateDivider(4); // 1 кГц / (1 + 4) = 200 Гц

        uint8_t whoAmI = readRegister(MPU6050_REG_WHO_AM_I);
        // 0x68 — MPU-6050, 0x70/0x72 — MPU-6500, 0x98 — некоторые клоны.
        if (whoAmI != 0x68 && whoAmI != 0x70 && whoAmI != 0x72 && whoAmI != 0x98) {
            return false;
        }

        initialized = true;
        return true;
    }

    void setAccelRange(AccelRange range) {
        writeRegister(MPU6050_REG_ACCEL_CONFIG, range);
        switch (range) {
            case ACCEL_FS_2G:  accelScaleDivider = 16384.0f; break;
            case ACCEL_FS_4G:  accelScaleDivider = 8192.0f;  break;
            case ACCEL_FS_8G:  accelScaleDivider = 4096.0f;  break;
            case ACCEL_FS_16G: accelScaleDivider = 2048.0f;  break;
            default:           accelScaleDivider = 4096.0f;  break;
        }
    }

    void setGyroRange(GyroRange range) {
        writeRegister(MPU6050_REG_GYRO_CONFIG, range);
        switch (range) {
            case GYRO_FS_250DPS:  gyroScaleDivider = 131.0f; break;
            case GYRO_FS_500DPS:  gyroScaleDivider = 65.5f;  break;
            case GYRO_FS_1000DPS: gyroScaleDivider = 32.8f;  break;
            case GYRO_FS_2000DPS: gyroScaleDivider = 16.4f;  break;
            default:              gyroScaleDivider = 131.0f; break;
        }
    }

    void setDLPF(uint8_t dlpfCfg) {
        writeRegister(MPU6050_REG_CONFIG, dlpfCfg & 0x07);
    }

    void setSampleRateDivider(uint8_t divider) {
        writeRegister(MPU6050_REG_SMPLRT_DIV, divider);
    }

    // true  = гироскоп в Standby (акселерометр работает, ток ~0.5 мА)
    // false = все оси гироскопа активны
    void setGyroStandby(bool standby) {
        if (writeRegister(MPU6050_REG_PWR_MGMT_2, standby ? 0x07 : 0x00)) {
            gyroStandby = standby;
        }
    }

    bool isGyroStandby() const {
        return gyroStandby;
    }

    bool isReady() const {
        return initialized;
    }

    bool readAll(MPUData& data) {
        if (!initialized) {
            return false;
        }

        uint8_t raw[14];
        if (!readBurst(raw, sizeof(raw))) {
            return false;
        }

        int16_t rawAX   = (int16_t)(((uint16_t)raw[0]  << 8) | raw[1]);
        int16_t rawAY   = (int16_t)(((uint16_t)raw[2]  << 8) | raw[3]);
        int16_t rawAZ   = (int16_t)(((uint16_t)raw[4]  << 8) | raw[5]);
        int16_t rawTemp = (int16_t)(((uint16_t)raw[6]  << 8) | raw[7]);
        int16_t rawGX   = (int16_t)(((uint16_t)raw[8]  << 8) | raw[9]);
        int16_t rawGY   = (int16_t)(((uint16_t)raw[10] << 8) | raw[11]);
        int16_t rawGZ   = (int16_t)(((uint16_t)raw[12] << 8) | raw[13]);

        data.accelX = rawAX / accelScaleDivider;
        data.accelY = rawAY / accelScaleDivider;
        data.accelZ = rawAZ / accelScaleDivider;
        data.temp = (rawTemp / 340.0f) + 36.53f;
        data.gyroX = rawGX / gyroScaleDivider;
        data.gyroY = rawGY / gyroScaleDivider;
        data.gyroZ = rawGZ / gyroScaleDivider;
        return true;
    }

    bool probe() {
        uint8_t whoAmI = readRegister(MPU6050_REG_WHO_AM_I);
        return whoAmI == 0x68 || whoAmI == 0x70 || whoAmI == 0x72 || whoAmI == 0x98;
    }

    // Обратная совместимость с вызовом data = mpu.readAll()
    MPUData readAll() {
        MPUData data;
        readAll(data);
        return data;
    }
};

#endif // MPU6050_LITE_H
