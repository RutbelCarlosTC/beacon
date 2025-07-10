package com.erns.androidbeacon

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.erns.androidbeacon.tools.BleTools
import java.nio.ByteBuffer

class Scanner(private val context: Context) {
    private val TAG = "Scanner"
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false

    // UUID específico para sensores de temperatura y humedad (debe coincidir con el Transmitter)
    private val SENSOR_UUID = "6ef0e30d-7308-4458-b62e-f706c692ca77"

    // Callback para manejar los resultados del escaneo
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val device = result.device
            val rssi = result.rssi
            val scanRecord = result.scanRecord

            Log.d(TAG, "Dispositivo encontrado: ${device.address} - RSSI: $rssi")

            // Procesar los datos del beacon
            scanRecord?.let { record ->
                // Buscar datos de fabricante con ID 76 (Apple - 0x004C)
                val manufacturerData = record.getManufacturerSpecificData(76)
                manufacturerData?.let { data ->
                    parseSensorBeaconData(data, rssi, device.address)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Escaneo falló con código: $errorCode")

            when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> Log.e(TAG, "SCAN_FAILED_ALREADY_STARTED")
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Log.e(TAG, "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED")
                SCAN_FAILED_FEATURE_UNSUPPORTED -> Log.e(TAG, "SCAN_FAILED_FEATURE_UNSUPPORTED")
                SCAN_FAILED_INTERNAL_ERROR -> Log.e(TAG, "SCAN_FAILED_INTERNAL_ERROR")
                SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> Log.e(TAG, "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES")
                SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> Log.e(TAG, "SCAN_FAILED_SCANNING_TOO_FREQUENTLY")
                else -> Log.e(TAG, "Error no manejado: $errorCode")
            }
            isScanning = false
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.forEach { result ->
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }
    }

    fun startScanning() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "BLUETOOTH_SCAN permission denied!")
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth no está habilitado")
            return
        }

        bluetoothLeScanner = adapter.bluetoothLeScanner

        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner no disponible")
            return
        }

        if (isScanning) {
            Log.d(TAG, "Ya está escaneando")
            return
        }

        // Configurar filtros para optimizar el escaneo
        val filters = mutableListOf<ScanFilter>()

        // Filtro para buscar dispositivos con datos de fabricante Apple (ID 76)
        val filter = ScanFilter.Builder()
            .setManufacturerData(76, byteArrayOf(0x02, 0x15)) // iBeacon identifier
            .build()
        filters.add(filter)

        // Filtro adicional para dispositivos con el nombre del sensor
        val nameFilter = ScanFilter.Builder()
            .setDeviceName("TempHumSensor")
            .build()
        filters.add(nameFilter)

        // Configurar ajustes de escaneo
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER) // Ahorra batería
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .setReportDelay(0L)
            .build()

        // Iniciar escaneo
        bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        isScanning = true
        Log.d(TAG, "Escaneo de sensores iniciado")
    }

    fun stopScanning() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "BLUETOOTH_SCAN permission denied!")
            return
        }

        bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        Log.d(TAG, "Escaneo detenido")
    }

    private fun parseSensorBeaconData(data: ByteArray, rssi: Int, deviceAddress: String) {
        if (data.size < 23) {
            Log.w(TAG, "Datos insuficientes para ser un iBeacon")
            return
        }

        // Verificar que sea un iBeacon (primeros 2 bytes deben ser 0x02, 0x15)
        if (data[0] != 0x02.toByte() || data[1] != 0x15.toByte()) {
            Log.w(TAG, "No es un iBeacon válido")
            return
        }

        // Extraer UUID (bytes 2-17)
        val uuid = ByteArray(16)
        System.arraycopy(data, 2, uuid, 0, 16)
        val uuidString = BleTools.byteArrayToHexString(uuid)
        val formattedUuid = formatUuid(uuidString)

        // Verificar si es nuestro sensor UUID
        if (!formattedUuid.equals(SENSOR_UUID, ignoreCase = true)) {
            Log.d(TAG, "UUID no coincide con sensor esperado. Recibido: $formattedUuid, Esperado: $SENSOR_UUID")
            return
        }

        // Extraer Major: Temperatura (bytes 18-19)
        val tempRaw = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)
        val temperature = tempRaw / 100.0f  // Convertir de entero a decimal

        // Extraer Minor: Humedad (bytes 20-21) - Big Endian
        val humidityRaw = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)
        val humidity = humidityRaw / 100.0f

        // Extraer TX Power (byte 22)
        val txPower = data[22].toInt()

        // Calcular distancia aproximada
        val distance = calculateDistance(txPower, rssi)

        Log.i(TAG, "=== SENSOR BEACON DETECTADO ===")
        Log.i(TAG, "Dirección MAC: $deviceAddress")
        Log.i(TAG, "UUID: $formattedUuid")
        Log.i(TAG, "Temperatura: ${String.format("%.1f", temperature)}°C")
        Log.i(TAG, "Humedad: ${String.format("%.1f", humidity)}%")
        Log.i(TAG, "TX Power: $txPower dBm")
        Log.i(TAG, "RSSI: $rssi dBm")
        Log.i(TAG, "Distancia aproximada: ${String.format("%.2f", distance)} metros")
        Log.i(TAG, "===============================")

        // Callback para notificar los datos del sensor
        onSensorDataReceived(deviceAddress, temperature, humidity, rssi, distance)
    }

    private fun formatUuid(hexString: String): String {
        // Convertir el string hex a formato UUID estándar
        val cleanHex = hexString.replace("-", "").lowercase()
        if (cleanHex.length != 32) {
            return hexString
        }

        return "${cleanHex.substring(0, 8)}-${cleanHex.substring(8, 12)}-${cleanHex.substring(12, 16)}-${cleanHex.substring(16, 20)}-${cleanHex.substring(20, 32)}"
    }

    private fun calculateDistance(txPower: Int, rssi: Int): Double {
        if (rssi == 0) return -1.0

        val ratio = (txPower - rssi) / 20.0
        return Math.pow(10.0, ratio)
    }

    // Callback para notificar cuando se reciben datos del sensor
    private fun onSensorDataReceived(
        address: String,
        temperature: Float,
        humidity: Float,
        rssi: Int,
        distance: Double
    ) {
        // Aquí puedes implementar la lógica para actualizar la UI
        // Por ejemplo, enviar un broadcast o llamar a un callback
        Log.d(TAG, "Datos del sensor procesados: $address - Temp: ${String.format("%.1f", temperature)}°C - Hum: ${String.format("%.1f", humidity)}%")

        // Ejemplo de cómo podrías notificar a la UI:
        // sensorDataListener?.onSensorDataReceived(address, temperature, humidity, rssi, distance)
    }

    // Interfaz para callback de datos del sensor
    interface SensorDataListener {
        fun onSensorDataReceived(
            address: String,
            temperature: Float,
            humidity: Float,
            rssi: Int,
            distance: Double
        )
    }

    // Variable para listener
    private var sensorDataListener: SensorDataListener? = null

    // Método para establecer listener
    fun setSensorDataListener(listener: SensorDataListener) {
        this.sensorDataListener = listener
    }

    fun isScanning(): Boolean {
        return isScanning
    }

    // Método adicional para obtener información detallada del último sensor detectado
    fun getLastSensorInfo(): String {
        return "Escaneo activo: $isScanning"
    }
}