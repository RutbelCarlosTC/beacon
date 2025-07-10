## Integrantes:
- Añazco Huamanquispe, Andre  
- Ttito Campos, Rutbel Carlos  
- Vilca Quispe, Frank’s Javier  
- César Alejandro Garay Bedregal  

---
El sistema consta de dos componentes: **Transmisor BLE** y **Scanner BLE**, ambos desarrollados en Android utilizando Bluetooth Low Energy (BLE) en formato iBeacon.

##  Emisor BLE – Envío de temperatura y humedad
El transmisor emite paquetes iBeacon con la siguiente estructura:

- **UUID:** Identificador del sensor (fijo).
- **Major:** Temperatura multiplicada por 10 (ej. 25.3 °C → `253`).
- **Minor:** Humedad multiplicada por 10 (ej. 65.7 % → `657`).
- **TX Power:** Potencia de transmisión (ej. `0xC5` = -59 dBm).

Fragmento relevante:
```kotlin
val tempInt = (temperature * 10).toInt().coerceIn(0, 65535)
val humidityInt = (humidity * 10).toInt().coerceIn(0, 65535)
data.putShort(tempInt.toShort())  // Major
data.putShort(humidityInt.toShort())  // Minor
```

![Emisor BLE](https://github.com/user-attachments/assets/91aff0a3-8edd-4e33-a5ed-f3b0dfcfafd6)

Se transmiten datos ambientales modificando los campos **Major** (temperatura) y **Minor** (humedad).

---

##  Receptor BLE – Lectura de datos ambientales
![Receptor BLE](https://github.com/user-attachments/assets/28f8d84a-436c-4751-9cce-420e2072b9f0)

