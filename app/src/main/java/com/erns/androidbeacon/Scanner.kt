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
                // Buscar datos de fabricante con ID 76 (Apple)
                val manufacturerData = record.getManufacturerSpecificData(76)
                manufacturerData?.let { data ->
                    parseBeaconData(data, rssi, device.address)
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
        Log.d(TAG, "Escaneo iniciado")
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

    private fun parseBeaconData(data: ByteArray, rssi: Int, deviceAddress: String) {
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

        // Extraer Major (bytes 18-19)
        val major = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)

        // Extraer Minor (bytes 20-21)
        val minor = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)

        // Extraer TX Power (byte 22)
        val txPower = data[22].toInt()

        // Calcular distancia aproximada
        val distance = calculateDistance(txPower, rssi)

        Log.i(TAG, "=== BEACON DETECTADO ===")
        Log.i(TAG, "Dirección MAC: $deviceAddress")
        Log.i(TAG, "UUID: $uuidString")
        Log.i(TAG, "Major: $major")
        Log.i(TAG, "Minor: $minor")
        Log.i(TAG, "TX Power: $txPower")
        Log.i(TAG, "RSSI: $rssi")
        Log.i(TAG, "Distancia aproximada: ${String.format("%.2f", distance)} metros")
        Log.i(TAG, "=======================")

        // Aquí puedes agregar callback para notificar a la UI
        onBeaconDetected(deviceAddress, uuidString, major, minor, rssi, distance)
    }

    private fun calculateDistance(txPower: Int, rssi: Int): Double {
        if (rssi == 0) return -1.0

        val ratio = (txPower - rssi) / 20.0
        return Math.pow(10.0, ratio)
    }

    // Callback para notificar cuando se detecta un beacon
    private fun onBeaconDetected(
        address: String,
        uuid: String,
        major: Int,
        minor: Int,
        rssi: Int,
        distance: Double
    ) {
        // Aquí puedes implementar la lógica para actualizar la UI
        // Por ejemplo, enviar un broadcast o llamar a un callback
        Log.d(TAG, "Beacon procesado: $address - UUID: $uuid - Major: $major - Minor: $minor")
    }

    fun isScanning(): Boolean {
        return isScanning
    }
}