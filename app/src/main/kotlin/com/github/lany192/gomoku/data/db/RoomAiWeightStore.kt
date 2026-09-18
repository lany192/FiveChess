package com.github.lany192.gomoku.data.db

import com.github.lany192.gomoku.domain.ai.AiWeightStore
import com.github.lany192.gomoku.domain.ai.learn.WeightsCodec

/**
 * Room 实现的权重仓库：把 `DoubleArray` 编成 BLOB 落库。
 *
 * 阻塞式（Room 不允许主线程访问数据库），调用方必须是后台线程 —— 与
 * [AiWeightStore] 的线程约定一致，误在主线程调用会得到明确的 `IllegalStateException`。
 */
class RoomAiWeightStore(private val dao: AiWeightsDao) : AiWeightStore {

    override fun load(modelId: String): DoubleArray? = dao.get(modelId)?.let { WeightsCodec.decode(it) }

    override fun save(modelId: String, weights: DoubleArray) {
        dao.put(AiWeightsEntity(modelId, WeightsCodec.encode(weights)))
    }
}
