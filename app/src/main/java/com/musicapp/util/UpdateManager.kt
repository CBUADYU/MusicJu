package com.musicapp.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.musicapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 版本更新检查器 — 对接 update.php / version.json 服务。
 *
 * 协议: http://yyjgx.cbuad.net/version.json
 *   { "version": "1.2.0", "url": "https://your-storage.example.com/xxx.apk", "note": "...", "published_at": "..." }
 *
 * 发布新版本:
 *   curl -X POST \
 *     -d "token=cbuad" \
 *     -d "version=1.2.0" \
 *     -d "url=https://your-storage.example.com/xxx.apk" \
 *     -d "note=修复XX功能" \
 *     http://yyjgx.cbuad.net/update.php
 *
 * 用法:
 *   val result = UpdateManager.checkUpdate()
 *   when (result) {
 *       is UpdateResult.Newer -> showUpdateDialog(result.latest)
 *       UpdateResult.UpToDate -> toast("已是最新版本")
 *       is UpdateResult.Error -> Log.w("Update", "检查失败", result.t)
 *   }
 */
object UpdateManager {

    /** 更新地址 — 发布前切到正式域名 */
    private const val VERSION_JSON_URL = "http://yyjgx.cbuad.net/version.json"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /** 当前 App 的 versionName (来自 build.gradle) */
    val currentVersionName: String = BuildConfig.VERSION_NAME

    /** 当前 App 的 versionCode (来自 build.gradle) */
    val currentVersionCode: Int = BuildConfig.VERSION_CODE

    /**
     * 检查远程有没有新版本。
     * @return UpdateResult — UpToDate / Newer / Error
     */
    suspend fun checkUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(VERSION_JSON_URL).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code}")
                }
                val body = resp.body?.string() ?: throw IllegalStateException("empty body")
                parseVersionJson(body)
            }
        }.fold(
            onSuccess = { latest ->
                val cmp = compareVersions(latest.version, currentVersionName)
                if (cmp > 0) UpdateResult.Newer(latest)
                else UpdateResult.UpToDate
            },
            onFailure = { UpdateResult.Error(it) }
        )
    }

    /**
     * 打开系统浏览器下载更新 URL。
     * Android 7+ 支持 APK 安装弹窗（需 manifest 里配 FileProvider + REQUEST_INSTALL_PACKAGES）。
     */
    fun openDownloadPage(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        context.startActivity(intent)
    }

    // --- 内部 ---

    private fun parseVersionJson(body: String): LatestVersion {
        val json = JSONObject(body)
        return LatestVersion(
            version = json.optString("version", "0.0.0"),
            url = json.optString("url", ""),
            note = json.optString("note", ""),
            publishedAt = json.optString("published_at", "")
        )
    }

    /**
     * 语义化版本比较。
     * 把 "1.2.0" 拆成 [1,2,0] 再逐段比 —— 避免字符串 "2.0.0" > "10.0.0" 这种经典坑。
     * @return >0 表示 a > b, 0 表示相等, <0 表示 a < b
     */
    fun compareVersions(a: String, b: String): Int {
        fun split(v: String): IntArray {
            val parts = v.trim().trimStart('v').split('.')
            val ints = IntArray(3)
            for (i in parts.indices) ints[i] = parts[i].toIntOrNull() ?: 0
            return ints
        }
        val aa = split(a)
        val bb = split(b)
        for (i in 0 until 3) {
            val d = aa[i] - bb[i]
            if (d != 0) return d
        }
        return 0
    }
}

/** 最新版本信息 */
data class LatestVersion(
    val version: String,
    val url: String,
    val note: String,
    val publishedAt: String
)

/** 检查结果 */
sealed class UpdateResult {
    /** 没有更新 */
    data object UpToDate : UpdateResult()
    /** 有更新 */
    data class Newer(val latest: LatestVersion) : UpdateResult()
    /** 检查出错（网络 / 解析） */
    data class Error(val t: Throwable) : UpdateResult()
}
