package com.github.lany192.gomoku.ui.robot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.lany192.gomoku.R
import com.github.lany192.gomoku.data.settings.algorithmByName
import com.github.lany192.gomoku.databinding.AiSelectBinding
import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.ui.common.setupEdgeToEdge

/**
 * 对手设置：难度单选 + 算法分组列表。
 *
 * 任何返回路径（返回键/返回箭头）都带结果 finish，由对局页负责落库与生效；
 * 本页不写持久化，避免"看着改了其实没应用"的两份真相。
 */
class AiSelectActivity : AppCompatActivity() {

    private lateinit var binding: AiSelectBinding
    private var level = Difficulty.MEDIUM
    private var algorithm = AiAlgorithm.DEFAULT
    private lateinit var adapter: AiSelectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AiSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(binding.root)

        level = Difficulty.entries.getOrNull(intent.getIntExtra(EXTRA_LEVEL, -1)) ?: Difficulty.MEDIUM
        algorithm = algorithmByName(intent.getStringExtra(EXTRA_ALGORITHM)) ?: AiAlgorithm.DEFAULT

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupLevelChips()
        adapter = AiSelectAdapter(onSelected = { algorithm = it }, selected = algorithm)
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
    }

    override fun finish() {
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_ALGORITHM, algorithm.name)
                .putExtra(EXTRA_LEVEL, level.ordinal),
        )
        super.finish()
    }

    private fun setupLevelChips() {
        binding.difficultyGroup.check(chipId(level))
        binding.difficultyGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val chosen = levelOfChip(checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener)
            if (chosen != null) level = chosen
        }
    }

    private fun chipId(level: Difficulty): Int = when (level) {
        Difficulty.NOVICE -> R.id.level_novice
        Difficulty.EASY -> R.id.level_easy
        Difficulty.MEDIUM -> R.id.level_medium
        Difficulty.HARD -> R.id.level_hard
        Difficulty.MASTER -> R.id.level_master
    }

    private fun levelOfChip(id: Int): Difficulty? = when (id) {
        R.id.level_novice -> Difficulty.NOVICE
        R.id.level_easy -> Difficulty.EASY
        R.id.level_medium -> Difficulty.MEDIUM
        R.id.level_hard -> Difficulty.HARD
        R.id.level_master -> Difficulty.MASTER
        else -> null
    }

    companion object {
        const val EXTRA_ALGORITHM = "ai_algorithm"
        const val EXTRA_LEVEL = "ai_level"

        fun intent(context: Context, algorithm: AiAlgorithm, level: Difficulty): Intent =
            Intent(context, AiSelectActivity::class.java)
                .putExtra(EXTRA_ALGORITHM, algorithm.name)
                .putExtra(EXTRA_LEVEL, level.ordinal)

        /** 解析返回结果；数据缺失或不认识时返回 null（调用方保持原选择） */
        fun parseResult(algorithmName: String?, levelOrdinal: Int): Pair<AiAlgorithm, Difficulty>? {
            val algorithm = algorithmByName(algorithmName) ?: return null
            val level = Difficulty.entries.getOrNull(levelOrdinal) ?: return null
            return algorithm to level
        }
    }
}
