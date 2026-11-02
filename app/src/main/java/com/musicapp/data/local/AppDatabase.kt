package com.musicapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.musicapp.data.local.dao.LocalSongDao
import com.musicapp.data.local.dao.PlayHistoryDao
import com.musicapp.data.local.dao.PlaylistDao
import com.musicapp.data.local.dao.SongDao
import com.musicapp.data.local.dao.UserPlatformDao
import com.musicapp.data.local.entity.LocalSongEntity
import com.musicapp.data.local.entity.PlayHistoryEntity
import com.musicapp.data.local.entity.PlaylistEntity
import com.musicapp.data.local.entity.SongEntity
import com.musicapp.data.local.entity.UserPlatformEntity

/**
 * 开发期数据库：目前没有存量用户，fallbackToDestructiveMigration 保证升级不崩。
 *
 * 等表结构定稿、准备发布前：
 *  1. 写一份真实的 Migration（照着 exportSchema 导出的历史 schema 写）
 *  2. 把 fallbackToDestructiveMigration() 换成 addMigrations(...)
 *  3. 保留 exportSchema = true 以便后续每改一次表都能对照历史 schema 写正确的 Migration
 */
@Database(
    entities = [
        PlaylistEntity::class,
        SongEntity::class,
        PlayHistoryEntity::class,
        UserPlatformEntity::class,
        LocalSongEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun songDao(): SongDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun userPlatformDao(): UserPlatformDao
    abstract fun localSongDao(): LocalSongDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_app.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
