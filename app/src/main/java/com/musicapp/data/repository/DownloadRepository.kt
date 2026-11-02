package com.musicapp.data.repository

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import com.musicapp.domain.model.Song
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

class DownloadRepository(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun downloadSong(song: Song, playUrl: String): Long {
        val fileName = "${song.title} - ${song.artist}.mp3"
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")

        val request = DownloadManager.Request(Uri.parse(playUrl))
            .setTitle("下载: ${song.title}")
            .setDescription("${song.artist} - ${song.album}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_MUSIC,
                "MusicApp/$fileName"
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        return downloadManager.enqueue(request)
    }

    fun getDownloadStatus(downloadId: Long): Flow<DownloadStatus> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                if (id == downloadId) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        )
                        val progress = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        )
                        val total = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        )
                        trySend(
                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.Success
                                DownloadManager.STATUS_FAILED -> DownloadStatus.Failed("下载失败")
                                DownloadManager.STATUS_RUNNING -> DownloadStatus.Progress(progress, total)
                                DownloadManager.STATUS_PAUSED -> DownloadStatus.Paused
                                else -> DownloadStatus.Pending
                            }
                        )
                    }
                    cursor.close()
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        awaitClose { context.unregisterReceiver(receiver) }
    }
}

sealed class DownloadStatus {
    object Pending : DownloadStatus()
    object Success : DownloadStatus()
    object Paused : DownloadStatus()
    data class Progress(val downloaded: Int, val total: Int) : DownloadStatus()
    data class Failed(val message: String) : DownloadStatus()
}