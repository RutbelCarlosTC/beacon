package com.erns.androidbeacon.tools

object BleTools {

    fun getIdAsByte(value: String): ByteArray {
        val uuid = ByteArray(16)
        for (i in uuid.indices) {
            val index = i * 2
            val `val` = value.substring(index, index + 2).toInt(16)
            uuid[i] = `val`.toByte()
        }
        return uuid
    }


    fun byteArrayToHexString(byteArray: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val result = StringBuilder()

        for (i in byteArray.indices) {
            val octet = byteArray[i].toInt()
            val firstIndex = (octet and 0xF0) ushr 4
            val secondIndex = octet and 0x0F
            result.append(hexChars[firstIndex])
            result.append(hexChars[secondIndex])

            // Agregar guiones para formato UUID
            if (i == 3 || i == 5 || i == 7 || i == 9) {
                result.append('-')
            }
        }

        return result.toString()
    }
}