package com.github.lany192.fivechess.data.settings

import android.content.Context
import com.github.lany192.fivechess.domain.ai.Difficulty

/** AI 难度持久化 */
interface AiLevelStore {
    fun read(): Int
    fun write(level: Int)
}

/** 沿用旧版 fivechess/ai_level 偏好键，老用户已选难度不丢失 */
class SharedPrefsAiLevelStore(context: Context) : AiLevelStore {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    override fun read(): Int = prefs.getInt(KEY_AI_LEVEL, Difficulty.MEDIUM.ordinal)

    override fun write(level: Int) {
        prefs.edit().putInt(KEY_AI_LEVEL, level).apply()
    }

    private companion object {
        const val PREF_NAME = "fivechess"
        const val KEY_AI_LEVEL = "ai_level"
    }
}
