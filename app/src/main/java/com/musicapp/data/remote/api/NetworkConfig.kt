package com.musicapp.data.remote.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络配置中心
 */
object NetworkConfig {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (com.musicapp.BuildConfig.HTTP_LOG) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.NONE
    }

    /** 通用 OkHttp（Retrofit 用） */
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    /**
     * 网易云专用 OkHttp —— 强制加 Referer + 完整 UA，防止网易云拦截。
     * MusicRepository 和 Coil 加载网易云图片都用这个。
     */
    val neteaseOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(NeteaseRefererInterceptor())
        .build()

    fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun createNeteaseApi(baseUrl: String = NeteaseApi.DEFAULT_BASE_URL): NeteaseApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(neteaseOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NeteaseApi::class.java)
    }
}

/**
 * 网易云请求拦截器 —— 强制加 Referer + 浏览器 UA。
 * 网易云 music.163.com 公开接口会检查 Referer/UA，缺失或异常会返回空或拦截。
 */
class NeteaseRefererInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val isNetease = original.url.host.contains("163.com") ||
                        original.url.host.contains("music.163.com") ||
                        original.url.host.contains("p1.music.126.net") ||
                        original.url.host.contains("p2.music.126.net") ||
                        original.url.host.contains("p3.music.126.net") ||
                        original.url.host.contains("p4.music.126.net")

        val request = original.newBuilder().apply {
            if (isNetease) {
                header("Referer", "https://music.163.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept", "*/*")
                header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            }
        }.build()
        return chain.proceed(request)
    }
}
