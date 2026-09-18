package com.github.lany192.gomoku.data.settings

import com.github.lany192.gomoku.domain.ai.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiLevelStoreTest {

    @Test
    fun `旧版三档序号映射到对应档位`() {
        assertEquals(Difficulty.EASY, legacyLevel(0))
        assertEquals(Difficulty.MEDIUM, legacyLevel(1))
        assertEquals(Difficulty.HARD, legacyLevel(2))
    }

    @Test
    fun `未存过或越界的序号不映射`() {
        assertNull(legacyLevel(-1))
        assertNull(legacyLevel(3))
    }
}
