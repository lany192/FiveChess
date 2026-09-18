package com.github.lany192.gomoku.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AiWeightsEntity::class], version = 1, exportSchema = false)
abstract class AiWeightsDatabase : RoomDatabase() {

    abstract fun aiWeightsDao(): AiWeightsDao

    companion object {
        private const val DB_NAME = "ai_weights.db"

        @Volatile
        private var instance: AiWeightsDatabase? = null

        /** 进程内单例：权重库只有一个文件，重复 open 会浪费资源 */
        fun get(context: Context): AiWeightsDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AiWeightsDatabase::class.java,
                DB_NAME,
            ).build().also { instance = it }
        }
    }
}
