package com.example.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.dispatcher.AlertSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class BleStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    MONITORING
}

data class DiscoveredDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val isEsp32: Boolean,
    val lastSeen: Long = System.currentTimeMillis()
)

class BleManager(private val context: Context) {
    private val TAG = "BleManager"

    private val CLIENT_CHARACTERISTIC_CONFIG =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    private val ESP32_CUSTOM_SERVICE_UUID =
        UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val ESP32_CUSTOM_CHAR_UUID =
        UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    private val UART_SERVICE_UUID =
        UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val UART_TX_UUID =
        UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private val _bleStatus = MutableStateFlow(BleStatus.DISCONNECTED)
    val bleStatus: StateFlow<BleStatus> = _bleStatus.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _lastReceivedPacket = MutableStateFlow<String?>(null)
    val lastReceivedPacket: StateFlow<String?> = _lastReceivedPacket.asStateFlow()

    private val _signalRssi = MutableStateFlow<Int?>(null)
    val signalRssi: StateFlow<Int?> = _signalRssi.asStateFlow()

    private val _deviceError = MutableStateFlow<String?>(null)
    val deviceError: StateFlow<String?> = _deviceError.asStateFlow()

    /** Заряд браслета из последнего события FALL/SOS, -1 если прошивка его не передаёт. */
    @Volatile
    var lastBatteryPercent: Int = -1
        private set

    /** Получает источник тревоги: AlertSource.ESP32_FALL или AlertSource.ESP32_SOS. */
    var onFallDetectedListener: ((String) -> Unit)? = null

    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var subscriptionsReady = false
    private var lastEventSeq = 0L
    private var lastEventTime = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = try {
                device.name ?: result.scanRecord?.deviceName ?: "Неизвестное BLE устройство"
            } catch (_: Exception) {
                "Неизвестное устройство"
            }
            val address = device.address
            val rssi = result.rssi
            val isEsp = name.contains("esp", ignoreCase = true) ||
                    name.contains("c3", ignoreCase = true) ||
                    name.contains("fall", ignoreCase = true) ||
                    name.contains("guard", ignoreCase = true) ||
                    name.contains("korgan", ignoreCase = true) ||
                    name.contains("қорған", ignoreCase = true)

            val current = _discoveredDevices.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.address == address }
            if (existingIndex >= 0) {
                current[existingIndex] = DiscoveredDevice(device, name, address, rssi, isEsp)
            } else {
                current.add(DiscoveredDevice(device, name, address, rssi, isEsp))
            }
            current.sortWith(compareByDescending<DiscoveredDevice> { it.isEsp32 }.thenByDescending { it.rssi })
            _discoveredDevices.value = current
        }

        override fun onScanFailed(errorCode: Int) {
            addLog("Ошибка сканирования BLE: код $errorCode")
            _bleStatus.value = BleStatus.DISCONNECTED
        }
    }

    private val descriptorQueue: java.util.ArrayDeque<Pair<BluetoothGattDescriptor, ByteArray>> =
        java.util.ArrayDeque()
    private var isWritingDescriptor = false

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _bleStatus.value = BleStatus.CONNECTED
                _deviceError.value = null
                commandCharacteristic = null
                subscriptionsReady = false
                lastEventSeq = 0L
                lastEventTime = 0L
                val name = try { gatt.device.name ?: "ESP32-C3" } catch (_: Exception) { "ESP32-C3" }
                val addr = gatt.device.address
                _connectedDeviceName.value = name
                _connectedDeviceAddress.value = addr
                addLog("Подключено к: $name ($addr)")
                addLog("Поиск GATT сервисов...")

                mainHandler.postDelayed({
                    try {
                        gatt.discoverServices()
                    } catch (e: Exception) {
                        addLog("Ошибка discoverServices: ${e.message}")
                    }
                }, 300)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _bleStatus.value = BleStatus.DISCONNECTED
                _connectedDeviceName.value = null
                _connectedDeviceAddress.value = null
                _signalRssi.value = null
                commandCharacteristic = null
                subscriptionsReady = false
                _deviceError.value = null
                descriptorQueue.clear()
                isWritingDescriptor = false
                addLog("Отключено от BLE устройства (код $status)")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("Сервисы найдены (${gatt.services.size} шт). Настройка подписки...")
                descriptorQueue.clear()
                isWritingDescriptor = false
                subscriptionsReady = false

                commandCharacteristic = findCommandCharacteristic(gatt)
                if (commandCharacteristic == null) {
                    addLog("Не найдена характеристика для отправки команд (ACK/PING)")
                }

                var foundTarget = false
                for (service in gatt.services) {
                    for (ch in service.characteristics) {
                        val properties = ch.properties
                        val canNotify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        val canIndicate = (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

                        if (canNotify || canIndicate) {
                            gatt.setCharacteristicNotification(ch, true)
                            val desc = ch.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                            if (desc != null) {
                                val value = if (canNotify) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                descriptorQueue.add(Pair(desc, value))
                                foundTarget = true
                            }
                        }
                    }
                }

                if (foundTarget) {
                    processNextDescriptor(gatt)
                } else {
                    onSubscriptionsReady()
                }

                try {
                    gatt.readRemoteRssi()
                } catch (_: Exception) {}
            } else {
                addLog("Ошибка поиска сервисов: код $status")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            isWritingDescriptor = false
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("Подписка активна для: ${descriptor.characteristic?.uuid}")
            } else {
                addLog("Ошибка записи дескриптора (${descriptor.characteristic?.uuid}): $status")
            }
            processNextDescriptor(gatt)
            if (descriptorQueue.isEmpty() && !isWritingDescriptor) {
                onSubscriptionsReady()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                addLog("Ошибка записи команды (${characteristic.uuid}): $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val data = characteristic.value
            processIncomingData(data)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            processIncomingData(value)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _signalRssi.value = rssi
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun processNextDescriptor(gatt: BluetoothGatt) {
        if (isWritingDescriptor || descriptorQueue.isEmpty()) return
        val (desc, value) = descriptorQueue.poll() ?: return
        isWritingDescriptor = true
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(desc, value)
            } else {
                @Suppress("DEPRECATION")
                desc.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(desc)
            }
        } catch (e: Exception) {
            isWritingDescriptor = false
            addLog("Исключение при записи дескриптора: ${e.message}")
            processNextDescriptor(gatt)
        }
    }

    private fun processIncomingData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        val text = String(data, Charsets.UTF_8).trim()
        if (text.isEmpty()) return
        _lastReceivedPacket.value = text
        addLog("RX: \"$text\"")

        val parts = text.split(":", limit = 4)
        val type = parts.firstOrNull()?.trim()?.uppercase(Locale.US).orEmpty()
        val sequence = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L

        when {
            type == "FALL" || type == "SOS" || text.contains("fall", ignoreCase = true) -> {
                val source = if (type == "SOS") AlertSource.ESP32_SOS else AlertSource.ESP32_FALL
                val battery = parts.getOrNull(2)?.toIntOrNull() ?: -1

                // Подтверждаем приём ДО фильтрации дублей: прошивка удалит событие
                // из своей NVS-очереди, даже если приложение уже показало тревогу.
                if (sequence > 0) sendAck(sequence)

                val now = System.currentTimeMillis()
                val isDuplicate = if (sequence > 0) {
                    sequence <= lastEventSeq
                } else {
                    now - lastEventTime < EVENT_DEDUP_WINDOW_MS
                }
                if (isDuplicate) {
                    addLog("Повтор события #$sequence проигнорирован")
                    return
                }
                if (sequence > 0) lastEventSeq = sequence
                lastEventTime = now

                val kind = if (source == AlertSource.ESP32_SOS) "SOS" else "падение"
                if (battery in 0..100) lastBatteryPercent = battery
                val batteryText = if (battery in 0..100) ", батарея $battery%" else ""
                addLog("ТРЕВОГА: $kind от ESP32 (seq=$sequence$batteryText)")
                mainHandler.post { onFallDetectedListener?.invoke(source) }
            }
            type == "PONG" -> addLog("ESP32 ответил на проверку связи")
            type == "READY" -> {
                _deviceError.value = null
                addLog("ESP32 готов к работе")
            }
            type == "INFO" -> {
                _deviceError.value = null
                addLog("Информация от ESP32: $text")
            }
            type == "ERROR" && parts.getOrNull(1)?.uppercase(Locale.US) == "SENSOR" -> {
                _deviceError.value = DEVICE_ERROR_SENSOR
                addLog("ОШИБКА: датчик MPU6050 не отвечает")
            }
            type == "ACK" -> addLog("ESP32 подтвердил событие #${parts.getOrNull(1)}")
            type == "CAL" -> addLog("Калибровка ESP32: ${parts.getOrNull(1) ?: "?"}")
            else -> addLog("Неизвестный пакет: $text")
        }
    }

    private fun findCommandCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        val direct = gatt.getService(ESP32_CUSTOM_SERVICE_UUID)
            ?.getCharacteristic(ESP32_CUSTOM_CHAR_UUID)
        if (direct != null) return direct

        for (service in gatt.services) {
            for (characteristic in service.characteristics) {
                val properties = characteristic.properties
                val writable = (properties and (
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                    )) != 0
                val notifiable = (properties and (
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                        BluetoothGattCharacteristic.PROPERTY_INDICATE
                    )) != 0
                if (writable && notifiable) return characteristic
            }
        }
        return null
    }

    private fun onSubscriptionsReady() {
        if (subscriptionsReady) return
        subscriptionsReady = true
        _bleStatus.value = BleStatus.MONITORING
        addLog("Мониторинг запущен. Проверка связи с ESP32...")
        sendCommand("PING")
    }

    private fun sendAck(sequence: Long) {
        if (sequence <= 0) return
        val sent = sendCommand("ACK:$sequence")
        if (sent) {
            addLog("TX ACK:$sequence")
        } else {
            addLog("Не удалось отправить ACK:$sequence")
        }
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(command: String): Boolean {
        val gatt = bluetoothGatt ?: return false
        val characteristic = commandCharacteristic ?: return false
        if (isWritingDescriptor) {
            addLog("Команда $command отложена: идёт настройка подписки")
            return false
        }

        return try {
            val payload = command.toByteArray(Charsets.UTF_8)
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        } catch (e: Exception) {
            addLog("Ошибка отправки команды $command: ${e.message}")
            false
        }
    }

    fun clearDeviceError() {
        _deviceError.value = null
    }

    fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scan = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            val connect = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            return scan && connect
        } else {
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasBluetoothPermission()) {
            addLog("Нет разрешения на Bluetooth Scan")
            return
        }
        if (!isBluetoothEnabled()) {
            addLog("Bluetooth выключен на телефоне")
            return
        }

        try {
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
            _discoveredDevices.value = emptyList()
            _bleStatus.value = BleStatus.SCANNING
            addLog("Запуск сканирования BLE устройств...")

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            bluetoothLeScanner?.startScan(null, settings, scanCallback)

            mainHandler.postDelayed({
                if (_bleStatus.value == BleStatus.SCANNING) {
                    stopScan()
                }
            }, 15000)
        } catch (e: Exception) {
            addLog("Ошибка запуска сканирования: ${e.localizedMessage}")
            _bleStatus.value = BleStatus.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            if (_bleStatus.value == BleStatus.SCANNING) {
                _bleStatus.value = BleStatus.DISCONNECTED
                addLog("Сканирование остановлено. Найдено ${_discoveredDevices.value.size} устройств.")
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasBluetoothPermission()) {
            addLog("Нет разрешения Bluetooth Connect")
            return
        }
        stopScan()
        disconnect()

        _bleStatus.value = BleStatus.CONNECTING
        val name = try { device.name ?: "ESP32-C3" } catch (_: Exception) { "ESP32-C3" }
        addLog("Подключение к $name (${device.address})...")

        try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            addLog("Ошибка подключения: ${e.localizedMessage}")
            _bleStatus.value = BleStatus.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToAddress(address: String) {
        if (!hasBluetoothPermission() || !isBluetoothEnabled() || address.isBlank()) return
        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            if (device != null) {
                connectToDevice(device)
            } else {
                addLog("Устройство с MAC $address не найдено в адаптере")
            }
        } catch (e: Exception) {
            addLog("Ошибка подключения по адресу $address: ${e.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (_: Exception) {}
        _bleStatus.value = BleStatus.DISCONNECTED
        _connectedDeviceName.value = null
        _connectedDeviceAddress.value = null
        _signalRssi.value = null
        commandCharacteristic = null
        subscriptionsReady = false
        _deviceError.value = null
    }

    fun triggerSimulatedFall() {
        addLog("[ТЕСТ] Симуляция падения от ESP32-C3")
        _lastReceivedPacket.value = "FALL:0"
        mainHandler.post {
            onFallDetectedListener?.invoke(AlertSource.ESP32_FALL)
        }
    }

    fun addLog(message: String) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val time = timeFormat.format(Date())
        val entry = "[$time] $message"
        Log.d(TAG, entry)
        val list = _logs.value.toMutableList()
        list.add(0, entry)
        if (list.size > 100) {
            list.removeAt(list.size - 1)
        }
        _logs.value = list
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    companion object {
        const val DEVICE_ERROR_SENSOR = "SENSOR"
        private const val EVENT_DEDUP_WINDOW_MS = 5_000L
    }
}
