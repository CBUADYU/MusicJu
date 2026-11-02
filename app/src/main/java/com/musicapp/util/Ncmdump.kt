package com.musicapp.util

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.io.*

/**
 * Ncmdump - NCM 加密音乐文件解密工具
 *
 * NCM 文件格式:
 * - 10 bytes: magic header
 * - 4 bytes: key length
 * - key_length bytes: encrypted AES key
 * - 4 bytes: metadata length
 * - metadata_length bytes: metadata (JSON, 包含封面等信息)
 * - 5 bytes: gap
 * - 4 bytes: album image size
 * - album_image_size bytes: album image data
 * - remaining bytes: encrypted music data (RC4/stream cipher)
 */
object Ncmdump {

    private const val MAGIC = "CTENFDAM"

    data class NcmInfo(
        val format: String = "mp3",
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val coverData: ByteArray? = null
    )

    /**
     * 检查是否为 NCM 文件
     */
    fun isNcmFile(file: File): Boolean {
        return file.extension.lowercase() == "ncm"
    }

    /**
     * 检查是否为 NCM 文件 (通过 magic bytes)
     */
    fun isNcmFile(input: InputStream): Boolean {
        val header = ByteArray(8)
        return try {
            input.read(header)
            String(header) == MAGIC
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 解密 NCM 文件到目标路径
     * @return 解密后的文件
     */
    fun decrypt(source: File, destDir: File): Result<File> {
        return try {
            val info = decryptToFile(source, destDir)
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decryptToFile(source: File, destDir: File): File {
        FileInputStream(source).use { input ->
            destDir.mkdirs()

            // 跳过 magic header (10 bytes)
            val magic = ByteArray(8)
            input.read(magic)
            if (String(magic) != MAGIC) {
                throw IOException("不是有效的 NCM 文件")
            }
            // 跳过 2 bytes 版本号
            input.skip(2)

            // 读取加密的 AES key
            val keyLen = readIntLE(input)
            val encryptedKey = ByteArray(keyLen)
            input.read(encryptedKey)

            // 解密 AES key
            val decryptedKey = decryptAesKey(encryptedKey)

            // 读取 metadata (JSON)
            val metaLen = readIntLE(input)
            val metaBytes = ByteArray(metaLen)
            input.read(metaBytes)
            val metadata = try {
                org.json.JSONObject(String(metaBytes))
            } catch (_: Exception) {
                null
            }

            // 获取格式和标题
            val format = metadata?.optString("format", "mp3") ?: "mp3"
            val title = metadata?.optString("musicName", "") ?: ""
            val artist = metadata?.optJSONArray("artist")?.let { arr ->
                (0 until arr.length()).joinToString(" / ") { arr.getJSONArray(it).getString(0) }
            } ?: ""

            // 跳过 5 bytes CRC
            input.skip(5)

            // 跳过封面图片（不嵌入到解密文件中，但需要 read 掉以推进流指针）
            val coverSize = readIntLE(input)
            if (coverSize > 0) {
                input.skip(coverSize.toLong())
            }

            // 生成输出文件名
            val outName = if (title.isNotEmpty()) {
                "${title} - ${artist}.$format".replace(Regex("[/\\\\:*?\"<>|]"), "_")
            } else {
                "${source.nameWithoutExtension}.$format".replace(Regex("[/\\\\:*?\"<>|]"), "_")
            }
            val destFile = File(destDir, outName)

            // 解密音乐数据 (RC4-like stream cipher)
            FileOutputStream(destFile).use { output ->
                val sbox = buildSBox(decryptedKey)
                decryptStream(input, output, sbox)
            }

            return destFile
        }
    }

    /**
     * 解密 AES key
     * NCM 使用 AES-128-ECB 加密 key
     */
    private fun decryptAesKey(encryptedKey: ByteArray): ByteArray {
        val aesKey = SecretKeySpec(CORE_KEY, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey)
        return cipher.doFinal(encryptedKey).apply {
            // 去掉 unpad 部分
            val len = this.size
            if (len > 17 && this[17] == 0x00.toByte()) {
                // 网易云音乐 key 格式: 前 17 字节固定
                val result = ByteArray(len - 17)
                System.arraycopy(this, 17, result, 0, len - 17)
                return result
            }
        }
    }

    /**
     * 构建 RC4 风格的 S-Box
     */
    private fun buildSBox(key: ByteArray): IntArray {
        val sbox = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + sbox[i] + key[i % key.size].toInt() and 0xFF) % 256
            val tmp = sbox[i]
            sbox[i] = sbox[j]
            sbox[j] = tmp
        }
        return sbox
    }

    /**
     * 流式解密
     */
    private fun decryptStream(input: InputStream, output: OutputStream, sbox: IntArray) {
        val buffer = ByteArray(8192)
        var i = 0
        var j = 0
        var len: Int
        while (input.read(buffer).also { len = it } > 0) {
            for (k in 0 until len) {
                i = (i + 1) % 256
                j = (j + sbox[i]) % 256
                val tmp = sbox[i]
                sbox[i] = sbox[j]
                sbox[j] = tmp
                val idx = (sbox[i] + sbox[j]) % 256
                buffer[k] = (buffer[k].toInt() xor sbox[idx]).toByte()
            }
            output.write(buffer, 0, len)
        }
    }

    private fun readIntLE(input: InputStream): Int {
        val b = ByteArray(4)
        input.read(b)
        return (b[0].toInt() and 0xFF) or
               ((b[1].toInt() and 0xFF) shl 8) or
               ((b[2].toInt() and 0xFF) shl 16) or
               ((b[3].toInt() and 0xFF) shl 24)
    }

    /**
     * AES core key
     */
    private val CORE_KEY = byteArrayOf(
        0x68.toByte(), 0x7A.toByte(), 0x48.toByte(), 0x52.toByte(),
        0x41.toByte(), 0x6D.toByte(), 0x73.toByte(), 0x6F.toByte(),
        0x35.toByte(), 0x6B.toByte(), 0x49.toByte(), 0x6E.toByte(),
        0x62.toByte(), 0x61.toByte(), 0x78.toByte(), 0x57.toByte()
    )
}