package com.robertohuertas.endless

import kotlin.experimental.xor

class KasaClient {

    private fun encrypt(input: String): ByteArray {
        val buf = input.toByteArray(Charsets.UTF_8)
        var key: Byte = -85
        for (i in 0 until buf.size) {
            buf[i] = buf[i] xor key
            key = buf[i]
        }
        return buf
    }

    private fun decrypt(input: ByteArray): String {
        val buf = ByteArray(input.size)
        var key: Byte = -85
        for (i in 0 until buf.size) {
            buf[i] = input[i] xor key
            key = input[i]
        }
        return buf.toString(Charsets.UTF_8)
    }
}
