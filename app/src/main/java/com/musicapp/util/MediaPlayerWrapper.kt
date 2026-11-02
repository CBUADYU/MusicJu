package com.musicapp.util

import androidx.media3.common.Player
import androidx.media3.common.Player.Commands
import androidx.media3.exoplayer.ExoPlayer

/**
 * 把一个只有单个 MediaItem 的 ExoPlayer 「伪装」成有完整播放列表的播放器。
 *
 * 背景：MediaSession / DefaultMediaNotificationProvider / MagicOS 9 灵动岛
 * 读取 Player.Commands 来决定 PlaybackState 里有没有 ACTION_SKIP_TO_NEXT / PREVIOUS。
 * ExoPlayer 只有 1 个 MediaItem 时 hasNextMediaItem()=false，不会暴露
 * COMMAND_SEEK_TO_NEXT_MEDIA_ITEM，灵动岛和系统控制中心就看不到上/下一曲按钮。
 *
 * 这个 wrapper 强制向 MediaSession 报告自己支持 next/prev，同时把实际的
 * seekToNextMediaItem / seekToPreviousMediaItem 路由回 MusicPlayer 完整的 playlist 逻辑。
 */
class MediaPlayerWrapper(
    private val player: ExoPlayer,
    private val musicPlayer: MusicPlayer
) : Player by player {

    /** 强制对外宣称支持 next / prev —— MediaSession 用这个生成 PlaybackState */
    override fun getAvailableCommands(): Commands =
        player.availableCommands.buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build()

    override fun seekToNextMediaItem() = musicPlayer.playNext()
    override fun seekToPreviousMediaItem() = musicPlayer.playPrevious()
    override fun seekToNext() = musicPlayer.playNext()
    override fun seekToPrevious() = musicPlayer.playPrevious()
}