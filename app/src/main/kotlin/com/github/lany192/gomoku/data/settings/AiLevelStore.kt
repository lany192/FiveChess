package com.github.lany192.gomoku.data.settings

import android.content.Context
import com.github.lany192.gomoku.domain.ai.Difficulty

/** AI 难度持久化 */
interface AiLevelStore {
    /** 未存过难度时返回 null，由调用方决定默认档位 */
    fun read(): Difficulty?

    fun write(level: Difficulty)
}

/** 新键存枚举名（档位增删不影响已存值）；旧版 fivechess/ai_level 键只读一次用于迁移 */
class SharedPrefsAiLevelStore(context: Context) : AiLevelStore {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    override fun read(): Difficulty? {
        prefs.getString(KEY_AI_LEVEL, null)?.let { name ->
            Difficulty.entries.firstOrNull { it.name == name }?.let { return it }
        }
        if (!prefs.contains(KEY_LEGACY_AI_LEVEL)) return null
        return legacyLevel(prefs.getInt(KEY_LEGACY_AI_LEVEL, -1))
    }

    override fun write(level: Difficulty) {
        prefs.edit().putString(KEY_AI_LEVEL, level.name).remove(KEY_LEGACY_AI_LEVEL).apply()
    }

    private companion object {
        const val PREF_NAME = "fivechess"
        const val KEY_AI_LEVEL = "ai_level_v2"

        /** 旧版三档难度的序号键（0=简单 1=中等 2=困难） */
        const val KEY_LEGACY_AI_LEVEL = "ai_level"
    }
}

/** 旧版三档难度序号 → 现档位，越界返回 null */
internal fun legacyLevel(ordinal: Int): Difficulty? = when (ordinal) {
    0 -> Difficulty.EASY
    1 -> Difficulty.MEDIUM
    2 -> Difficulty.HARD
    else -> null
}
