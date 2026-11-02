package com.musicapp.util

import android.content.Context
import android.media.AudioManager
import android.media.Spatializer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hihonor.android.magicx.media.audio.config.ResultCode
import com.hihonor.android.magicx.media.audio.interfaces.AudioChannelFormatListener
import com.hihonor.android.magicx.media.audio.interfaces.AudioFormatExtend
import com.hihonor.android.magicx.media.audio.interfaces.HnAudioClient
import com.hihonor.android.magicx.media.audio.interfaces.HnAudioPlayClient
import com.hihonor.android.magicx.media.audio.interfaces.IAudioServiceCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * 荣耀 Audio Kit 管理器
 *
 * 整合三大音频能力：
 *  1. 三维声内容接入 — 查询设备支持的多声道格式（声道掩码、编码、采样率）并监听变化
 *  2. 高清音频播放 — 开启/关闭 96~192kHz 高采样率无损播放
 *  3. 空间音频 — 查询设备空间音频可用/开启状态并监听变化
 *
 * 使用前需确保：
 *  - 已在荣耀开发者平台创建应用并下载 mcs-services.json 放入工程根目录
 *  - 设备为 MagicOS 7.0+ 荣耀手机
 */
class HonorAudioKitManager(private val context: Context) {

    companion object {
        private const val TAG = "HonorAudioKit"
    }

    // ---------- 基础服务 ----------
    private var hnAudioClient: HnAudioClient? = null
    private var playClient: HnAudioPlayClient? = null
    private var isPlayServiceBound = false

    // ---------- 空间音频（系统 API） ----------
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var spatializer: Spatializer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    // ---------- 对外暴露的状态 ----------
    private val _isDeviceSupported = MutableStateFlow(false)
    val isDeviceSupported: StateFlow<Boolean> = _isDeviceSupported.asStateFlow()

    private val _isMultichannelSupported = MutableStateFlow(false)
    val isMultichannelSupported: StateFlow<Boolean> = _isMultichannelSupported.asStateFlow()

    private val _isHighSampleRateSupported = MutableStateFlow(false)
    val isHighSampleRateSupported: StateFlow<Boolean> = _isHighSampleRateSupported.asStateFlow()

    private val _supportedChannelFormats = MutableStateFlow<List<ChannelFormatInfo>>(emptyList())
    val supportedChannelFormats: StateFlow<List<ChannelFormatInfo>> = _supportedChannelFormats.asStateFlow()

    private val _highSampleRateEnabled = MutableStateFlow(false)
    val highSampleRateEnabled: StateFlow<Boolean> = _highSampleRateEnabled.asStateFlow()

    private val _spatialAudioAvailable = MutableStateFlow(false)
    val spatialAudioAvailable: StateFlow<Boolean> = _spatialAudioAvailable.asStateFlow()

    private val _spatialAudioEnabled = MutableStateFlow(false)
    val spatialAudioEnabled: StateFlow<Boolean> = _spatialAudioEnabled.asStateFlow()

    private val _serviceStatus = MutableStateFlow(ServiceStatus.IDLE)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    // 三维声格式变化监听
    private val channelFormatListener = AudioChannelFormatListener { formats ->
        Log.i(TAG, "onSupportedAudioChannelFormatChanged: ${formats?.size}")
        val infos = formats?.map { it.toInfo() } ?: emptyList()
        _supportedChannelFormats.value = infos
    }

    // 空间音频状态监听
    private val spatializerListener = object : Spatializer.OnSpatializerStateChangedListener {
        override fun onSpatializerEnabledChanged(spat: Spatializer, isEnabled: Boolean) {
            Log.i(TAG, "spatializer enabled: $isEnabled")
            _spatialAudioEnabled.value = isEnabled
        }

        override fun onSpatializerAvailableChanged(spat: Spatializer, isAvailable: Boolean) {
            Log.i(TAG, "spatializer available: $isAvailable")
            _spatialAudioAvailable.value = isAvailable
        }
    }

    // 服务连接回调
    private val serviceCallback = object : IAudioServiceCallback {
        override fun onResult(result: Int) {
            Log.i(TAG, "onResult: $result")
            when (result) {
                ResultCode.VENDOR_NOT_SUPPORTED -> {
                    _serviceStatus.value = ServiceStatus.NOT_SUPPORTED
                    _isDeviceSupported.value = false
                }
                ResultCode.AUDIO_SERVICE_SUCCESS -> {
                    _serviceStatus.value = ServiceStatus.BASE_CONNECTED
                    onBaseServiceConnected()
                }
                ResultCode.AUDIO_PLAY_SERVICE_SUCCESS -> {
                    isPlayServiceBound = true
                    _serviceStatus.value = ServiceStatus.PLAY_CONNECTED
                    queryMultichannelFormats()
                }
                ResultCode.AUDIO_PLAY_SERVICE_DISCONNECTED,
                ResultCode.AUDIO_PLAY_SERVICE_DIED -> {
                    isPlayServiceBound = false
                    _serviceStatus.value = ServiceStatus.PLAY_DISCONNECTED
                }
                ResultCode.AUDIO_SERVICE_DISCONNECTED,
                ResultCode.AUDIO_SERVICE_DIED -> {
                    _serviceStatus.value = ServiceStatus.BASE_DISCONNECTED
                }
            }
        }
    }

    /**
     * 初始化 AudioKit。必须在主线程调用。
     */
    fun initialize() {
        Log.i(TAG, "initialize")
        val supported = HnAudioClient.isDeviceSupported(context)
        _isDeviceSupported.value = supported
        if (!supported) {
            Log.w(TAG, "device not supported AudioKit")
            _serviceStatus.value = ServiceStatus.NOT_SUPPORTED
            return
        }
        try {
            hnAudioClient = HnAudioClient(context, serviceCallback)
            hnAudioClient?.initialize()
        } catch (e: Exception) {
            Log.e(TAG, "initialize failed", e)
            _serviceStatus.value = ServiceStatus.ERROR
        }
        setupSpatialAudio()
    }

    /**
     * 基础服务连接成功后，查询支持的服务并创建播放客户端
     */
    private fun onBaseServiceConnected() {
        val client = hnAudioClient ?: return
        val services = client.getSupportedServices() ?: emptyList()
        Log.i(TAG, "supported services: $services")

        val hasMultichannel = services.any {
            it == HnAudioClient.ServiceType.HNAUDIO_SERVICE_MULTICHANNELPLAY.serviceType
        }
        val hasHighSampleRate = services.any {
            it == HnAudioClient.ServiceType.HNAUDIO_SERVICE_HIGHSAMPLERATEPLAY.serviceType
        }
        _isMultichannelSupported.value = hasMultichannel
        _isHighSampleRateSupported.value = hasHighSampleRate

        // 优先创建多声道播放客户端（可同时用于高清播放开关）
        if (hasMultichannel) {
            playClient = client.createService(HnAudioClient.ServiceType.HNAUDIO_SERVICE_MULTICHANNELPLAY)
        } else if (hasHighSampleRate) {
            playClient = client.createService(HnAudioClient.ServiceType.HNAUDIO_SERVICE_HIGHSAMPLERATEPLAY)
        }
    }

    /**
     * 查询设备支持的三维环绕声格式
     */
    fun queryMultichannelFormats() {
        val client = playClient
        if (!isPlayServiceBound || client == null) {
            Log.w(TAG, "play service not bound")
            return
        }
        try {
            val formats = client.getSupportedAudioChannelFormat()
            val infos = formats?.map { it.toInfo() } ?: emptyList()
            _supportedChannelFormats.value = infos
            Log.i(TAG, "supported channel formats: ${infos.size}")
        } catch (e: Exception) {
            Log.e(TAG, "query formats failed", e)
        }
    }

    /**
     * 注册三维声格式变化监听
     */
    fun registerChannelFormatListener() {
        val client = playClient
        if (!isPlayServiceBound || client == null) return
        try {
            client.registerAudioChannelFormatListener(channelFormatListener)
        } catch (e: Exception) {
            Log.e(TAG, "register listener failed", e)
        }
    }

    /**
     * 取消注册三维声格式变化监听
     */
    fun unregisterChannelFormatListener() {
        try {
            playClient?.unregisterAudioChannelFormatListener(channelFormatListener)
        } catch (e: Exception) {
            Log.e(TAG, "unregister listener failed", e)
        }
    }

    /**
     * 开启/关闭高清音频播放（96~192kHz 无损）
     */
    fun setHighSampleRatePlay(enable: Boolean) {
        val client = playClient
        if (!isPlayServiceBound || client == null) {
            Log.w(TAG, "play service not bound, cannot toggle high sample rate")
            return
        }
        try {
            client.enableHighSampleRatePlay(enable)
            _highSampleRateEnabled.value = enable
            Log.i(TAG, "high sample rate play: $enable")
        } catch (e: Exception) {
            Log.e(TAG, "setHighSampleRatePlay failed", e)
        }
    }

    // ---------- 空间音频 ----------
    private fun setupSpatialAudio() {
        try {
            spatializer = audioManager.spatializer
            spatializer?.let { sp ->
                _spatialAudioAvailable.value = sp.isAvailable
                _spatialAudioEnabled.value = sp.isEnabled
                sp.addOnSpatializerStateChangedListener(
                    { runnable -> mainHandler.post(runnable) },
                    spatializerListener
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "setup spatial audio failed", e)
        }
    }

    /**
     * 主动刷新空间音频状态
     */
    fun refreshSpatialAudio() {
        val sp = spatializer ?: return
        _spatialAudioAvailable.value = sp.isAvailable
        _spatialAudioEnabled.value = sp.isEnabled
    }

    /**
     * 释放所有资源
     */
    fun destroy() {
        Log.i(TAG, "destroy")
        try {
            spatializer?.removeOnSpatializerStateChangedListener(spatializerListener)
        } catch (e: Exception) {
            Log.e(TAG, "remove spatializer listener failed", e)
        }
        try {
            playClient?.unregisterAudioChannelFormatListener(channelFormatListener)
            playClient?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "destroy play client failed", e)
        }
        try {
            hnAudioClient?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "destroy audio client failed", e)
        }
        playClient = null
        hnAudioClient = null
        isPlayServiceBound = false
        callbackExecutor.shutdown()
    }

    // ---------- 数据模型 ----------
    /** 三维声格式信息 */
    data class ChannelFormatInfo(
        val channelMask: Int,
        val encodings: List<EncodingInfo>
    ) {
        data class EncodingInfo(
            val encoding: Int,
            val sampleRates: List<Int>
        )
    }

    enum class ServiceStatus {
        IDLE, NOT_SUPPORTED, BASE_CONNECTED, PLAY_CONNECTED,
        BASE_DISCONNECTED, PLAY_DISCONNECTED, ERROR
    }

    private fun AudioFormatExtend.toInfo(): ChannelFormatInfo {
        val encs = encodings?.map { enc ->
            ChannelFormatInfo.EncodingInfo(
                encoding = enc.encoding,
                sampleRates = enc.sampleRates ?: emptyList()
            )
        } ?: emptyList()
        return ChannelFormatInfo(channelMask = channelMask, encodings = encs)
    }
}
