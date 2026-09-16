package com.mts.kisskh

object KisskhCipher {
    private fun toInt32(x: Double): Int {
        var v = (x % 4294967296.0).toLong()
        if (v < 0) v += 4294967296L
        return v.toInt()
    }

    fun computeHash(s: String): Long {
        var h = 0.0
        for (ch in s) {
            val c = ch.code
            val int32H = toInt32(h)
            val shifted = (int32H shl 5).toDouble()
            h = shifted - h + c
        }
        return h.toLong()
    }

    private val table0 = intArrayOf(
        0x4f6bdaa3L.toInt(), (-0x61d07350L).toInt(), 0x7f5e722dL.toInt(), (-0x61210cecL).toInt(),
        0x536620a8L.toInt(), (-0x32b653e8L).toInt(), (-0x4de821cbL).toInt(), 0x2cc92d21L.toInt(),
        (-0x73412227L).toInt(), 0x41f771c1L.toInt(), (-0xc1f500cL).toInt(), (-0x20d67d2bL).toInt(),
        0x2dadde47L.toInt(), 0x6c5aaf86L.toInt(), (-0x6045ff8eL).toInt(), 0x409382a7L.toInt(),
        (-0x6417db2L).toInt(), (-0x6a1bd238L).toInt(), 0xa5e2dbaL.toInt(), 0x4acdaf1dL.toInt(),
        0x54c72698L.toInt(), (-0x3edcf4b0L).toInt(), (-0x3482d916L).toInt(), (-0x7e4f7609L).toInt(),
        (-0x6c9fb16cL).toInt(), 0x524345c4L.toInt(), (-0x66c19cd2L).toInt(), 0x188eead9L.toInt(),
        (-0x351884c7L).toInt(), (-0x675bc103L).toInt(), 0x19a5dd3L.toInt(), 0x1914b70aL.toInt(),
        (-0x4fb1e313L).toInt(), 0x28ea2210L.toInt(), 0x29707fc3L.toInt(), 0x3064c8c9L.toInt(),
        (-0x17593e17L).toInt(), (-0x3fb31c07L).toInt(), (-0x16c363c6L).toInt(), (-0x26a7ab0dL).toInt(),
        (-0x4b793324L).toInt(), 0x74ca2f25L.toInt(), (-0x62094ce1L).toInt(), 0x44aee7ecL.toInt()
    )

    private val t1 = IntArray(256)
    private val t2 = IntArray(256)
    private val t3 = IntArray(256)
    private val t4 = IntArray(256)
    private val sbox = IntArray(256)

    init {
        val dbox = IntArray(256)
        for (i in 0 until 256) {
            dbox[i] = if (i < 128) (i shl 1) else ((i shl 1) xor 0x11b)
        }

        var p = 0
        var q = 0
        for (i in 0 until 256) {
            var s = q xor (q shl 1) xor (q shl 2) xor (q shl 3) xor (q shl 4)
            s = ((s ushr 8) xor (s and 0xff) xor 0x63) and 0xff
            sbox[p] = s

            val d = dbox[p]
            val v = (0x101 * dbox[s]) xor (0x1010100 * s)

            t1[p] = (v shl 24) or (v ushr 8)
            t2[p] = (v shl 16) or (v ushr 16)
            t3[p] = (v shl 8) or (v ushr 24)
            t4[p] = v

            if (p == 0) {
                p = 1
                q = 1
            } else {
                p = d xor dbox[dbox[dbox[dbox[dbox[d]] xor d]]]
                q = q xor dbox[dbox[q]]
            }
        }
    }

    private fun strToWords(s: String): Pair<IntArray, Int> {
        val words = IntArray((s.length + 3) / 4)
        for (i in s.indices) {
            words[i ushr 2] = words[i ushr 2] or ((s[i].code and 0xff) shl (24 - (i % 4) * 8))
        }
        return Pair(words, s.length)
    }

    private fun wordsToHex(words: IntArray, byteLength: Int): String {
        val sb = StringBuilder(byteLength * 2)
        for (i in 0 until byteLength) {
            val byte = (words[i ushr 2] ushr (24 - (i % 4) * 8)) and 0xff
            sb.append(String.format("%02X", byte))
        }
        return sb.toString()
    }

    private fun padPkcs7(s: String): String {
        val pad = 16 - (s.length % 16)
        val sb = java.lang.StringBuilder(s)
        val padChar = pad.toChar()
        for (i in 0 until pad) {
            sb.append(padChar)
        }
        return sb.toString()
    }

    private fun encryptBlock(words: IntArray, offset: Int) {
        val iv = if (offset == 0) {
            intArrayOf(0x01504af3, 0x56e619cf, 0x2e42bba6, (-0x73c08f07L).toInt())
        } else {
            intArrayOf(words[offset - 4], words[offset - 3], words[offset - 2], words[offset - 1])
        }

        for (i in 0 until 4) {
            words[offset + i] = words[offset + i] xor iv[i]
        }

        var s0 = words[offset] xor table0[0]
        var s1 = words[offset + 1] xor table0[1]
        var s2 = words[offset + 2] xor table0[2]
        var s3 = words[offset + 3] xor table0[3]
        var kIdx = 4

        for (round in 1 until 10) {
            val n0 = t1[s0 ushr 24] xor t2[(s1 ushr 16) and 0xff] xor t3[(s2 ushr 8) and 0xff] xor t4[s3 and 0xff] xor table0[kIdx++]
            val n1 = t1[s1 ushr 24] xor t2[(s2 ushr 16) and 0xff] xor t3[(s3 ushr 8) and 0xff] xor t4[s0 and 0xff] xor table0[kIdx++]
            val n2 = t1[s2 ushr 24] xor t2[(s3 ushr 16) and 0xff] xor t3[(s0 ushr 8) and 0xff] xor t4[s1 and 0xff] xor table0[kIdx++]
            s3 = t1[s3 ushr 24] xor t2[(s0 ushr 16) and 0xff] xor t3[(s1 ushr 8) and 0xff] xor t4[s2 and 0xff] xor table0[kIdx++]
            s0 = n0
            s1 = n1
            s2 = n2
        }

        val n0 = ((sbox[s0 ushr 24] shl 24) or (sbox[(s1 ushr 16) and 0xff] shl 16) or (sbox[(s2 ushr 8) and 0xff] shl 8) or sbox[s3 and 0xff]) xor table0[kIdx++]
        val n1 = ((sbox[s1 ushr 24] shl 24) or (sbox[(s2 ushr 16) and 0xff] shl 16) or (sbox[(s3 ushr 8) and 0xff] shl 8) or sbox[s0 and 0xff]) xor table0[kIdx++]
        val n2 = ((sbox[s2 ushr 24] shl 24) or (sbox[(s3 ushr 16) and 0xff] shl 16) or (sbox[(s0 ushr 8) and 0xff] shl 8) or sbox[s1 and 0xff]) xor table0[kIdx++]
        s3 = ((sbox[s3 ushr 24] shl 24) or (sbox[(s0 ushr 16) and 0xff] shl 16) or (sbox[(s1 ushr 8) and 0xff] shl 8) or sbox[s2 and 0xff]) xor table0[kIdx++]

        words[offset] = n0
        words[offset + 1] = n1
        words[offset + 2] = n2
        words[offset + 3] = s3
    }

    fun generateKkey(epId: Long, guid: String): String {
        val appVer = "2.8.10"
        val platformVer = 4830201
        val appName = "kisskh"

        val parts = arrayListOf(
            "",
            epId.toString(),
            "",
            "mg3c3b04ba",
            appVer,
            guid,
            platformVer.toString(),
            appName,
            appName,
            appName,
            appName,
            appName,
            appName,
            "00",
            ""
        )

        val initialStr = parts.joinToString("|")
        val hashVal = computeHash(initialStr)
        parts.add(1, hashVal.toString())

        val fullStr = parts.joinToString("|")
        val padded = padPkcs7(fullStr)
        val (words, byteLen) = strToWords(padded)

        var offset = 0
        while (offset < words.size) {
            encryptBlock(words, offset)
            offset += 4
        }

        return wordsToHex(words, byteLen)
    }
}
