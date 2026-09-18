package com.github.lany192.gomoku.domain.ai.learn

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 学习权重编解码：DoubleArray ↔ ByteArray（定长小端序，纯 Kotlin/JVM，可单测往返）。
 *
 * 存储侧不关心权重语义（20 维线性权重与 962 维 AlphaZero 网络共用同一格式），
 * 尺寸校验由各引擎在加载后自行按维度检查。
 */
object WeightsCodec {

    fun encode(weights: DoubleArray): ByteArray {
        val buffer = ByteBuffer.allocate(weights.size * Long.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        for (w in weights) {
            buffer.putDouble(w)
        }
        return buffer.array()
    }

    /** 字节数必须是 8 的整数倍，否则返回 null（数据损坏视为无历史权重） */
    fun decode(bytes: ByteArray): DoubleArray? {
        if (bytes.isEmpty() || bytes.size % Long.SIZE_BYTES != 0) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val weights = DoubleArray(bytes.size / Long.SIZE_BYTES)
        for (i in weights.indices) {
            weights[i] = buffer.getDouble()
        }
        return weights
    }
}
