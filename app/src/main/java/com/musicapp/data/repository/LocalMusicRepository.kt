package com.musicapp.data.repository

import android.content.Context
import com.musicapp.data.local.dao.LocalSongDao
import com.musicapp.data.local.entity.LocalSongEntity
import com.musicapp.util.LocalMusicScanner
import com.musicapp.util.Ncmdump
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地音乐仓库 - 管理本地音乐扫描、解密和存储
 */
class LocalMusicRepository(
    private val localSongDao: LocalSongDao,
    private val context: Context
) {
    /** 解密输出目录 */
    private val decryptedDir: File
        get() = File(context.filesDir, "decrypted_music").also { it.mkdirs() }

    /** 获取所有本地歌曲 */
    fun getAllSongs(): Flow<List<LocalSongEntity>> = localSongDao.getAllSongs()

    /** 获取可播放的歌曲 */
    fun getPlayableSongs(): Flow<List<LocalSongEntity>> = localSongDao.getPlayableSongs()

    /** 获取加密歌曲 */
    fun getEncryptedSongs(): Flow<List<LocalSongEntity>> = localSongDao.getEncryptedSongs()

    /** 获取歌曲数量 */
    suspend fun getCount(): Int = localSongDao.count()

    /**
     * 扫描本地音乐文件
     * @return 扫描结果统计
     */
    suspend fun scan(): ScanStats = withContext(Dispatchers.IO) {
        val result = LocalMusicScanner.scanMediaStore(context)

        val entities = mutableListOf<LocalSongEntity>()

        // 普通音频文件
        result.songs.forEach { song ->
            val file = File(song.sourceUrl.removePrefix("file://"))
            entities.add(
                LocalSongEntity(
                    songId = song.songId,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    duration = song.duration,
                    filePath = file.absolutePath,
                    fileSize = file.length(),
                    isEncrypted = false
                )
            )
        }

        // 加密文件
        result.encryptedSongs.forEach { enc ->
            val songId = "enc_${enc.file.absolutePath.hashCode()}"
            entities.add(
                LocalSongEntity(
                    songId = songId,
                    title = enc.file.nameWithoutExtension,
                    artist = enc.platform.displayName,
                    album = "",
                    duration = 0,
                    filePath = enc.file.absolutePath,
                    fileSize = enc.size,
                    isEncrypted = true,
                    encryptedFormat = enc.extension
                )
            )
        }

        // 清空旧数据并插入新数据
        localSongDao.clearAll()
        localSongDao.insertAll(entities)

        ScanStats(
            totalSongs = entities.size,
            playableSongs = entities.count { !it.isEncrypted },
            encryptedSongs = entities.count { it.isEncrypted }
        )
    }

    /**
     * 解密 NCM 文件
     * @return 解密后的文件路径
     */
    suspend fun decryptNcm(songId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val songs = getAllSongsSuspend()
            val song = songs.find { it.songId == songId }
                ?: return@withContext Result.failure(Exception("未找到歌曲记录"))

            if (song.decryptedPath.isNotEmpty()) {
                val decryptedFile = File(song.decryptedPath)
                if (decryptedFile.exists()) {
                    return@withContext Result.success(song.decryptedPath)
                }
            }

            val sourceFile = File(song.filePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("源文件不存在"))
            }

            val result = Ncmdump.decrypt(sourceFile, decryptedDir)
            result.fold(
                onSuccess = { decryptedFile ->
                    localSongDao.updateDecryptedPath(songId, decryptedFile.absolutePath)
                    Result.success(decryptedFile.absolutePath)
                },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 批量解密所有 NCM 文件
     */
    suspend fun decryptAllNcm(): DecryptStats = withContext(Dispatchers.IO) {
        val songs = getAllSongsSuspend()
        val ncmSongs = songs.filter { it.encryptedFormat == "ncm" }

        var success = 0
        var failed = 0
        val errors = mutableListOf<String>()

        ncmSongs.forEach { song ->
            val result = decryptNcm(song.songId)
            result.fold(
                onSuccess = { success++ },
                onFailure = { e ->
                    failed++
                    errors.add("${song.title}: ${e.message}")
                }
            )
        }

        DecryptStats(
            total = ncmSongs.size,
            success = success,
            failed = failed,
            errors = errors
        )
    }

    /** 删除本地歌曲记录 */
    suspend fun deleteSong(songId: String) {
        localSongDao.deleteById(songId)
    }

    /** 清除所有本地歌曲记录 */
    suspend fun clearAll() {
        localSongDao.clearAll()
    }

    /** 同步获取所有歌曲（用于非 Flow 场景） */
    private suspend fun getAllSongsSuspend(): List<LocalSongEntity> {
        return getAllSongs().first()
    }

    data class ScanStats(
        val totalSongs: Int,
        val playableSongs: Int,
        val encryptedSongs: Int
    )

    data class DecryptStats(
        val total: Int,
        val success: Int,
        val failed: Int,
        val errors: List<String>
    )
}