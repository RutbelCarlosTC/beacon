package com.erns.androidbeacon

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import com.erns.androidbeacon.tools.BleTools
import java.nio.ByteBuffer

class Transmitter(private val context: Context) {
    private val TAG = "Transmitter"
    private var advertiser: android.bluetooth.le.BluetoothLeAdvertiser? = null
    private var isAdvertising = false

    // UUID específico para sensores de temperatura y humedad
    private val SENSOR_UUID = "6ef0e30d-7308-4458-b62e-f706c692ca77"

    fun updateSensorData(temperature: Float, humidity: Float) {
        if (isAdvertising) {
            startAdvertisingWithSensorData(temperature, humidity)
        } else {
            startAdvertisingWithSensorData(temperature, humidity)
        }
    }

    private fun startAdvertisingWithSensorData(temperature: Float, humidity: Float) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "BLUETOOTH_ADVERTISE permission denied!")
            return
        }

        Log.d(TAG, "Advertising sensor data - Temp: ${String.format("%.1f", temperature)}°C, Humidity: ${String.format("%.1f", humidity)}%")

        adapter.name = "TempHumSensor"
        advertiser = adapter.bluetoothLeAdvertiser

        if (advertiser == null) {
            Log.e(TAG, "BluetoothLeAdvertiser is null")
            return
        }

        // Verificar soporte de características
        if (!adapter.isLe2MPhySupported) {
            Log.w(TAG, "2M PHY not supported, continuing anyway")
        }

        if (!adapter.isLeExtendedAdvertisingSupported) {
            Log.w(TAG, "LE Extended Advertising not supported, using legacy advertising")
        }

        val dataBuilder = AdvertiseData.Builder()
        dataBuilder.setIncludeDeviceName(true)
        dataBuilder.setIncludeTxPowerLevel(false)

        // Construir datos del iBeacon con información de sensores
        val manufacturerData = buildiBeaconData(temperature, humidity)
        dataBuilder.addManufacturerData(0x004C, manufacturerData) // Apple Company ID

        val settingsBuilder = AdvertiseSettings.Builder()
        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
        settingsBuilder.setConnectable(false)
        settingsBuilder.setTimeout(0)
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)

        // Detener publicidad anterior si existe
        if (isAdvertising) {
            advertiser?.stopAdvertising(callbackClose)
        }

        // Iniciar nueva publicidad
        advertiser?.startAdvertising(settingsBuilder.build(), dataBuilder.build(), callback)
    }

    private fun buildiBeaconData(temperature: Float, humidity: Float): ByteArray {
        val data = ByteBuffer.allocate(23)

        // Estructura iBeacon
        data.put(0x02.toByte())  // iBeacon Type
        data.put(0x15.toByte())  // Length (21 bytes following)

        // UUID del sensor (16 bytes)
        val uuid: ByteArray = BleTools.getIdAsByte(SENSOR_UUID.replace("-", ""))
        data.put(uuid)

        // Major: Temperatura (2 bytes)
        // Convertir temperatura a entero multiplicando por 10 (ej: 25.3°C -> 253)
        val tempInt = (temperature * 10).toInt().coerceIn(0, 65535)
        data.putShort(tempInt.toShort())

        // Minor: Humedad (2 bytes)
        // Convertir humedad a entero multiplicando por 10 (ej: 67.5% -> 675)
        val humidityInt = (humidity * 10).toInt().coerceIn(0, 65535)
        data.putShort(humidityInt.toShort())

        // TX Power (1 byte)
        data.put(0xC5.toByte()) // -59 dBm a 1 metro

        return data.array()
    }

    fun stopAdvertising() {
        if (isAdvertising && advertiser != null) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                advertiser?.stopAdvertising(callbackClose)
                isAdvertising = false
                Log.d(TAG, "Advertising stopped")
            }
        }
    }

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            isAdvertising = true
            Log.d(TAG, "Sensor data advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            isAdvertising = false
            Log.e(TAG, "Advertising failed, errorCode: $errorCode")

            when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> Log.e(TAG, "ADVERTISE_FAILED_ALREADY_STARTED")
                ADVERTISE_FAILED_DATA_TOO_LARGE -> Log.e(TAG, "ADVERTISE_FAILED_DATA_TOO_LARGE")
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> Log.e(TAG, "ADVERTISE_FAILED_FEATURE_UNSUPPORTED")
                ADVERTISE_FAILED_INTERNAL_ERROR -> Log.e(TAG, "ADVERTISE_FAILED_INTERNAL_ERROR")
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> Log.e(TAG, "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS")
                else -> Log.e(TAG, "Unhandled error: $errorCode")
            }
        }
    }

    private val callbackClose = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertising successfully stopped")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "Failed to stop advertising, errorCode: $errorCode")
        }
    }
}