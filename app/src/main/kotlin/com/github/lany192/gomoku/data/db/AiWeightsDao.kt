package com.github.lany192.gomoku.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 权重读写：都是阻塞调用，只允许在后台线程使用（Room 的主线程检查会兜底报错），
 * 与 `domain` 的 [com.github.lany192.gomoku.domain.ai.AiWeightStore] 约定一致。
 */
@Dao
interface AiWeightsDao {

    @Query("SELECT weights FROM ai_weights WHERE id = :id")
    fun get(id: String): ByteArray?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(entity: AiWeightsEntity)
}
