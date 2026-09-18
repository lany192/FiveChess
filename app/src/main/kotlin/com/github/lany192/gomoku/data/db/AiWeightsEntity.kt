package com.github.lany192.gomoku.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 学习类引擎的权重行：id 是算法枚举名（TD_LEARNING / Q_LEARNING / ALPHA_ZERO / GENETIC） */
@Entity(tableName = "ai_weights")
class AiWeightsEntity(
    @PrimaryKey val id: String,
    val weights: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiWeightsEntity) return false
        return id == other.id && weights.contentEquals(other.weights)
    }

    override fun hashCode(): Int = 31 * id.hashCode() + weights.contentHashCode()
}
