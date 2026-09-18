package com.github.lany192.gomoku.data.settings

import android.content.Context
import com.github.lany192.gomoku.domain.ai.AiAlgorithm

/** AI 算法选择持久化 */
interface AiAlgorithmStore {
    /** 未存过算法时返回 null，由调用方决定默认算法 */
    fun read(): AiAlgorithm?

    fun write(algorithm: AiAlgorithm)
}

/** 按枚举名存（与档位存储同一份 prefs）；名字不认识时视为未存过 */
class SharedPrefsAiAlgorithmStore(context: Context) : AiAlgorithmStore {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    override fun read(): AiAlgorithm? = algorithmByName(prefs.getString(KEY_AI_ALGORITHM, null))

    override fun write(algorithm: AiAlgorithm) {
        prefs.edit().putString(KEY_AI_ALGORITHM, algorithm.name).apply()
    }

    private companion object {
        const val PREF_NAME = "fivechess"
        const val KEY_AI_ALGORITHM = "ai_algorithm_v1"
    }
}

/** 枚举名 → 算法；未存过或已改名的旧值返回 null（回退默认算法） */
internal fun algorithmByName(name: String?): AiAlgorithm? =
    AiAlgorithm.entries.firstOrNull { it.name == name }
