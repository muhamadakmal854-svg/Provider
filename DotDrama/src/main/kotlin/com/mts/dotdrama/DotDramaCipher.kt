package com.mts.dotdrama

import android.util.Base64
import java.nio.charset.StandardCharsets
import kotlin.math.abs

object DotDramaCipher {
    private const val SECRET = "6f5fKnWnXB9528J4gQlAbNEWYDeMOR486u5D4ATaHgkvEcNaTk"

    fun decrypt(bodyStr: String, headerVal: String?): String? {
        val cleanBody = bodyStr.trim()
        if (cleanBody.isEmpty()) return null

        val raw = try {
            Base64.decode(cleanBody, Base64.DEFAULT)
        } catch (_: Exception) {
            try {
                Base64.decode(cleanBody, Base64.NO_WRAP)
            } catch (_: Exception) {
                try {
                    java.util.Base64.getDecoder().decode(cleanBody)
                } catch (_: Exception) {
                    return null
                }
            }
        } ?: return null

        val header = headerVal?.trim().orEmpty()
        val combined = header + SECRET
        var h = 0x811c9dc5.toInt()
        val fnvPrime = 0x01000193

        for (ch in combined) {
            h = (h xor ch.code) * fnvPrime
        }

        var v2 = h
        val v71 = v2 shl 13
        v2 += v71
        var v7 = (v2 shr 7) xor v2
        val v11 = v7 shl 3
        v7 += v11
        val v12 = v7 shr 17
        v7 = (v7 xor v12) + (v7 shl 5)
        val finalKeyInt = abs(v7)

        val key = ByteArray(8)
        var tempV7 = finalKeyInt
        for (i in 0 until 8) {
            key[i] = (tempV7 and 0xff).toByte()
            tempV7 = tempV7 shr 8
        }

        for (i in raw.indices) {
            raw[i] = (raw[i].toInt() xor key[i % 8].toInt()).toByte()
        }

        return try {
            String(raw, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}

