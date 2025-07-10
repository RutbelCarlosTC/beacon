## Integrantes:
- Añazco Huamanquispe, Andre  
- Ttito Campos, Rutbel Carlos  
- Vilca Quispe, Frank’s Javier  
- César Alejandro Garay Bedregal  

---

## Descripción general

El sistema consta de dos componentes principales:  
**Transmisor BLE** y **Scanner BLE**, ambos desarrollados en Android, utilizando **Bluetooth Low Energy (BLE)** bajo el estándar **iBeacon** para el envío y lectura de datos ambientales.

---

## Emisor BLE – Envío de temperatura y humedad

El transmisor emite paquetes iBeacon con la siguiente estructura:

- **UUID:** Identificador único del sensor (fijo).
- **Major:** Temperatura multiplicada por 10 (ej. 25.3 °C → `253`).
- **Minor:** Humedad multiplicada por 10 (ej. 65.7 % → `657`).
- **TX Power:** Potencia de transmisión (ej. `0xC5` = -59 dBm).

**Fragmento relevante del código:**
```kotlin
val tempInt = (temperature * 10).toInt().coerceIn(0, 65535)
val humidityInt = (humidity * 10).toInt().coerceIn(0, 65535)
data.putShort(tempInt.toShort())     // Major
data.putShort(humidityInt.toShort()) // Minor
```

**Vista del Emisor en ejecución:**

![Emisor BLE](https://github.com/user-attachments/assets/850d5016-c8aa-4771-999c-091b9a7d8693)

> Se transmiten datos ambientales modificando dinámicamente los campos **Major** (temperatura) y **Minor** (humedad).

---

## Receptor BLE – Lectura de datos ambientales

El escáner BLE detecta dispositivos cercanos con **Manufacturer ID 0x004C (Apple)**, verifica el UUID esperado, y luego decodifica los valores desde los campos `Major` y `Minor`.

**Vista del Receptor en ejecución:**

![Receptor BLE](https://github.com/user-attachments/assets/28f8d84a-436c-4751-9cce-420e2072b9f0)

**Fragmento relevante del código:**
```kotlin
val tempRaw = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)
val humidityRaw = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)
val temperature = tempRaw / 100.0f
val humidity = humidityRaw / 100.0f
```

**Estimación de distancia** basada en TX Power y RSSI:
```kotlin
val ratio = (txPower - rssi) / 20.0
val distance = Math.pow(10.0, ratio)
```

---

## Permisos requeridos

El sistema requiere los siguientes permisos (declarados y solicitados en tiempo de ejecución):

## Tabla de permisos BLE requeridos

| Permiso                          | Descripción                                 | ¿Runtime request? | Desde Android |
|----------------------------------|---------------------------------------------|-------------------|----------------|
| `BLUETOOTH_SCAN`                | Escaneo de dispositivos BLE                 |  Sí             | 12 (API 31)     |
| `BLUETOOTH_CONNECT`            | Conexión e interacción con dispositivos     |  Sí             | 12 (API 31)     |
| `BLUETOOTH_ADVERTISE`          | Publicidad de datos vía BLE (iBeacon)       |  Sí             | 12 (API 31)     |
| `ACCESS_FINE_LOCATION`         | Localización precisa (necesaria para escaneo BLE en versiones anteriores) |  Sí (hasta Android 11) | 6 (API 23) |
| `ACCESS_COARSE_LOCATION`       | Localización aproximada (alternativa menos precisa) | A veces       | 6 (API 23)      |
| `BLUETOOTH`                    | Permiso base para Bluetooth (obsoleto desde Android 12) |  No             | 1               |
| `BLUETOOTH_ADMIN`              | Cambios en configuración Bluetooth (obsoleto) |  No           | 1               |

>  **Sí**: Se debe solicitar explícitamente al usuario.  
>  **A veces**: Depende de la versión del sistema y si se solicita `FINE`.  
>  **No**: Basta con declararlo en el `AndroidManifest.xml`.
> En versiones Android 12 y superiores, los permisos deben gestionarse explícitamente y otorgarse por el usuario en tiempo real.
