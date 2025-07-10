package com.erns.androidbeacon

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.random.Random

class MainActivity : AppCompatActivity(), Scanner.SensorDataListener {
    private val TAG = "MainActivity"
    private var scanner: Scanner? = null
    private var transmitter: Transmitter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializar scanner y transmitter
        scanner = Scanner(applicationContext)
        transmitter = Transmitter(applicationContext)

        // Configurar listener para recibir datos del sensor
        scanner?.setSensorDataListener(this)

        // Botón para iniciar transmisión
        val btnStartTransmitter: Button = findViewById(R.id.btnStartTransmitter)
        btnStartTransmitter.setOnClickListener {
            // Simular datos de sensor para testing
            val temperature = Random.nextFloat() * 15 + 20 // 20-35°C
            val humidity = Random.nextFloat() * 40 + 40     // 40-80%

            transmitter?.updateSensorData(temperature, humidity)
            Log.d(TAG, "Transmisión iniciada con datos: Temp=${String.format("%.1f", temperature)}°C, Hum=${String.format("%.1f", humidity)}%")
        }

        // Botón para iniciar escaneo
        val btnStartScanner: Button = findViewById(R.id.btnStartScanner)
        btnStartScanner.setOnClickListener {
            scanner?.startScanning()
            Log.d(TAG, "Escaneo iniciado")
        }

        // Botón para detener escaneo
        val btnStopScanner: Button = findViewById(R.id.btnStopScanner)
        btnStopScanner.setOnClickListener {
            scanner?.stopScanning()
            Log.d(TAG, "Escaneo detenido")
        }

        // Solicitar permisos
        val permissions = arrayOf(
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        activityResultLauncher.launch(permissions)
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner?.stopScanning()
        transmitter?.stopAdvertising()
    }

    // Implementación del callback para recibir datos del sensor
    override fun onSensorDataReceived(
        address: String,
        temperature: Float,
        humidity: Float,
        rssi: Int,
        distance: Double
    ) {
        // Solo log de los datos recibidos - sin cambios en UI
        Log.i(TAG, "=== DATOS RECIBIDOS DEL SENSOR ===")
        Log.i(TAG, "Dirección: $address")
        Log.i(TAG, "Temperatura: ${String.format("%.1f", temperature)}°C")
        Log.i(TAG, "Humedad: ${String.format("%.1f", humidity)}%")
        Log.i(TAG, "RSSI: $rssi dBm")
        Log.i(TAG, "Distancia: ${String.format("%.2f", distance)}m")
        Log.i(TAG, "================================")
    }

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            val permissionName = it.key
            val isGranted = it.value
            if (isGranted) {
                Log.d(TAG, "Permission $permissionName is granted")
            } else {
                Log.d(TAG, "Permission $permissionName is denied")
            }
        }
    }
}