package com.musicapp.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.musicapp.domain.model.MusicPlatform
import com.musicapp.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地音乐扫描器
 * 支持扫描:
 * - MP3, FLAC, WAV, AAC, OGG, M4A, WMA 等常见格式
 * - NCM (网易云加密), QMC (QQ音乐加密), KGM (酷狗加密) 等加密格式
 */
object LocalMusicScanner {

    private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "ape", "dsf", "dff", "aiff", "opus"
    )

    private val ENCRYPTED_EXTENSIONS = mapOf(
        "ncm" to MusicPlatform.NETEASE,
        "qmc0" to MusicPlatform.QQ,
        "qmc3" to MusicPlatform.QQ,
        "qmcflac" to MusicPlatform.QQ,
        "qmcogg" to MusicPlatform.QQ,
        "kgm" to MusicPlatform.KUGOU,
        "kgma" to MusicPlatform.KUGOU,
        "kwm" to MusicPlatform.KUWO,
        "mflac" to MusicPlatform.MIGU,
        "mgg" to MusicPlatform.MIGU
    )

    data class ScanResult(
        val songs: List<Song>,
        val encryptedSongs: List<EncryptedFile>,
        val totalScanned: Int
    )

    data class EncryptedFile(
        val file: File,
        val extension: String,
        val platform: MusicPlatform,
        val size: Long
    )

    /**
     * 通过 MediaStore 扫描所有音频文件
     */
    suspend fun scanMediaStore(context: Context): ScanResult = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val encryptedFiles = mutableListOf<EncryptedFile>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE
        )

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null,
            MediaStore.Audio.Media.DATE_ADDED + " DESC"
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (it.moveToNext()) {
                val path = it.getString(dataCol) ?: continue
                val file = File(path)
                if (!file.exists()) continue

                val ext = file.extension.lowercase()
                // 使用 file.name 作为展示名（cursor 的 nameCol 经常返回 null）
                if (ext in ENCRYPTED_EXTENSIONS) {
                    encryptedFiles.add(
                        EncryptedFile(
                            file = file,
                            extension = ext,
                            platform = ENCRYPTED_EXTENSIONS[ext] ?: MusicPlatform.UNKNOWN,
                            size = it.getLong(sizeCol)
                        )
                    )
                } else if (ext in SUPPORTED_AUDIO_EXTENSIONS) {
                    val title = it.getString(titleCol) ?: file.nameWithoutExtension
                    val artist = it.getString(artistCol) ?: "<未知艺术家>"
                    val album = it.getString(albumCol) ?: ""
                    val duration = it.getLong(durationCol)

                    songs.add(
                        Song(
                            songId = "local_${it.getLong(idCol)}",
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            platform = MusicPlatform.UNKNOWN,
                            sourceUrl = Uri.fromFile(file).toString(),
                            coverUrl = ""
                        )
                    )
                }
            }
        }

        ScanResult(
            songs = songs,
            encryptedSongs = encryptedFiles,
            totalScanned = songs.size + encryptedFiles.size
        )
    }

    /**
     * 扫描指定目录下的音频文件
     */
    suspend fun scanDirectory(directory: File): ScanResult = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val encryptedFiles = mutableListOf<EncryptedFile>()
        var scanned = 0

        directory.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            scanned++
            val ext = file.extension.lowercase()

            if (ext in ENCRYPTED_EXTENSIONS) {
                encryptedFiles.add(
                    EncryptedFile(
                        file = file,
                        extension = ext,
                        platform = ENCRYPTED_EXTENSIONS[ext] ?: MusicPlatform.UNKNOWN,
                        size = file.length()
                    )
                )
            } else if (ext in SUPPORTED_AUDIO_EXTENSIONS) {
                songs.add(
                    Song(
                        songId = "local_${file.absolutePath.hashCode()}",
                        title = file.nameWithoutExtension,
                        artist = "<未知艺术家>",
                        album = "",
                        duration = 0,
                        platform = MusicPlatform.UNKNOWN,
                        sourceUrl = Uri.fromFile(file).toString(),
                        coverUrl = ""
                    )
                )
            }
        }

        ScanResult(
            songs = songs,
            encryptedSongs = encryptedFiles,
            totalScanned = scanned
        )
    }

    /**
     * 常见音乐目录
     */
    fun getCommonMusicDirectories(): List<File> {
        val dirs = mutableListOf<File>()

        // 外部存储音乐目录
        val externalMusic = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_MUSIC
        )
        if (externalMusic.exists()) dirs.add(externalMusic)

        // 网易云音乐下载目录
        val neteaseDir = File(
            android.os.Environment.getExternalStorageDirectory(),
            "netease/cloudmusic/Music"
        )
        if (neteaseDir.exists()) dirs.add(neteaseDir)

        // QQ音乐下载目录
        val qqDir = File(
            android.os.Environment.getExternalStorageDirectory(),
            "qqmusic/song"
        )
        if (qqDir.exists()) dirs.add(qqDir)

        // 酷狗下载目录
        val kgDir = File(
            android.os.Environment.getExternalStorageDirectory(),
            "kgmusic/download"
        )
        if (kgDir.exists()) dirs.add(kgDir)

        // 酷我下载目录
        val kwDir = File(
            android.os.Environment.getExternalStorageDirectory(),
            "kwmusic"
        )
        if (kwDir.exists()) dirs.add(kwDir)

        // 汽水音乐下载目录
        val qishuiDir = File(
            android.os.Environment.getExternalStorageDirectory(),
            "Android/data/com.ss.android.ugc.aweme/files/music"
        )
        if (qishuiDir.exists()) dirs.add(qishuiDir)

        // Download 目录下的音乐
        val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        if (downloadDir.exists()) dirs.add(downloadDir)

        return dirs
    }
}