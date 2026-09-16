package com.mts.kisskh

import android.util.Base64
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object KisskhSubDecryptor {
    private const val KEY1 = "8056483646328763"
    private const val IV1  = "6852612370185273"

    private const val KEY2 = "AmSmZVcH93UQUezi"
    private const val IV2  = "ReBKWW8cqdjPEnF6"

    private const val KEY3 = "sWODXX04QRTkHdlZ"
    private const val IV3  = "8pwhapJeC4hrS9hO"

    fun decryptCue(b64: String, keyStr: String, ivStr: String): String {
        return try {
            val cipherBytes = Base64.decode(b64.trim(), Base64.DEFAULT)
            val key = SecretKeySpec(keyStr.toByteArray(Charsets.UTF_8), "AES")
            val iv = IvParameterSpec(ivStr.toByteArray(Charsets.UTF_8))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, iv)
            val decryptedBytes = cipher.doFinal(cipherBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            b64
        }
    }

    fun decryptSubtitle(rawContent: String, extension: String): String {
        val (keyStr, ivStr) = when {
            extension.equals("txt1", ignoreCase = true) -> Pair(KEY2, IV2)
            extension.equals("txt", ignoreCase = true) -> Pair(KEY1, IV1)
            else -> Pair(KEY3, IV3)
        }

        val lines = rawContent.lines()
        val outLines = ArrayList<String>(lines.size)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.all { it.isDigit() } || trimmed.contains("-->")) {
                outLines.add(line)
            } else {
                val dec = decryptCue(trimmed, keyStr, ivStr)
                outLines.add(dec)
            }
        }
        return outLines.joinToString("\n")
    }
}

object KisskhSubServer {
    private const val TAG = "KisskhSubServer"
    private val subCache = ConcurrentHashMap<String, String>()
    private var serverPort: Int = 0

    init {
        try {
            val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            serverPort = ss.localPort
            Thread {
                while (!ss.isClosed) {
                    try {
                        val socket = ss.accept()
                        Thread {
                            handleClient(socket)
                        }.start()
                    } catch (e: Exception) {
                        break
                    }
                }
            }.apply {
                isDaemon = true
                name = "KisskhSubServer"
                start()
            }
            Log.d(TAG, "Subtitle server started on port $serverPort")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start subtitle server: ${e.message}")
        }
    }

    fun addSubtitle(id: String, content: String): String {
        if (subCache.size > 60) {
            val firstKey = subCache.keys().nextElement()
            if (firstKey != null) subCache.remove(firstKey)
        }
        subCache[id] = content
        return "http://127.0.0.1:$serverPort/sub/$id.srt"
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 10000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val firstLine = reader.readLine() ?: return
                val parts = firstLine.split(" ")
                if (parts.size < 2 || !parts[0].equals("GET", ignoreCase = true)) return

                val rawPath = parts[1].substringBefore("?").removePrefix("/sub/").removeSuffix(".srt")
                val content = subCache[rawPath]
                val out = s.getOutputStream()
                if (content != null) {
                    val bytes = content.toByteArray(Charsets.UTF_8)
                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain; charset=utf-8\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Connection: close\r\n\r\n"
                    out.write(response.toByteArray(Charsets.UTF_8))
                    out.write(bytes)
                } else {
                    val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    out.write(notFound.toByteArray(Charsets.UTF_8))
                }
                out.flush()
            }
        } catch (e: Exception) {
            // Ignore socket disconnects
        }
    }
}
