package com.erns.androidbeacon

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var transmitter: Transmitter
    private lateinit var tvTemperature: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var btnStartTransmitter: Button
    private lateinit var btnStopTransmitter: Button

    private var sensorJob: Job? = null
    private var isTransmitting = false

    // Valores simulados de sensores
    private var currentTemperature: Float = 25.0f
    private var currentHumidity: Float = 60.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupClickListeners()
        requestPermissions()

        transmitter = Transmitter(applicationContext)
    }

    private fun initViews() {
        tvTemperature = findViewById(R.id.tvTemperature)
        tvHumidity = findViewById(R.id.tvHumidity)
        btnStartTransmitter = findViewById(R.id.btnStartTransmitter)
        btnStopTransmitter = findViewById(R.id.btnStopTransmitter)

        updateSensorDisplay()
    }

    private fun setupClickListeners() {
        btnStartTransmitter.setOnClickListener {
            startTransmission()
        }

        btnStopTransmitter.setOnClickListener {
            stopTransmission()
        }
    }

    private fun startTransmission() {
        if (!isTransmitting) {
            isTransmitting = true
            btnStartTransmitter.isEnabled = false
            btnStopTransmitter.isEnabled = true

            // Iniciar simulación de sensores
            startSensorSimulation()

            Log.d(TAG, "Iniciando transmisión de datos de sensores")
        }
    }

    private fun stopTransmission() {
        if (isTransmitting) {
            isTransmitting = false
            btnStartTransmitter.isEnabled = true
            btnStopTransmitter.isEnabled = false

            // Detener simulación y transmisión
            sensorJob?.cancel()
            transmitter.stopAdvertising()

            Log.d(TAG, "Transmisión detenida")
        }
    }

    private fun startSensorSimulation() {
        sensorJob = CoroutineScope(Dispatchers.Main).launch {
            while (isTransmitting) {
                // Simular lecturas de sensores con pequeñas variaciones
                currentTemperature = 20.0f + Random.nextFloat() * 15.0f // 20-35°C
                currentHumidity = 45.0f + Random.nextFloat() * 30.0f    // 45-75%

                updateSensorDisplay()

                // Enviar datos via iBeacon
                transmitter.updateSensorData(currentTemperature, currentHumidity)

                delay(2000) // Actualizar cada 2 segundos
            }
        }
    }

    private fun updateSensorDisplay() {
        tvTemperature.text = "Temperatura: ${String.format("%.1f", currentTemperature)}°C"
        tvHumidity.text = "Humedad: ${String.format("%.1f", currentHumidity)}%"
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        activityResultLauncher.launch(permissions)
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

    override fun onDestroy() {
        super.onDestroy()
        stopTransmission()
    }
}