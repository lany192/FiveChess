package com.github.lany192.gomoku.data.settings

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiAlgorithmStoreTest {

    @Test
    fun `枚举名解析回对应算法`() {
        AiAlgorithm.entries.forEach { algorithm ->
            assertEquals(algorithm, algorithmByName(algorithm.name))
        }
    }

    @Test
    fun `未存过或改名的旧值视为未存过`() {
        assertNull(algorithmByName(null))
        assertNull(algorithmByName(""))
        // 旧版本存过的名字在新版本里不存在时回退默认算法，而不是崩溃
        assertNull(algorithmByName("MINIMAX_V2"))
    }
}
