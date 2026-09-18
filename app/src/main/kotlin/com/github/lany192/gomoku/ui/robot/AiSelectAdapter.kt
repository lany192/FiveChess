package com.github.lany192.gomoku.ui.robot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.lany192.gomoku.databinding.ItemAiAlgorithmBinding
import com.github.lany192.gomoku.databinding.ItemAiFamilyBinding
import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiFamily
import com.google.android.material.color.MaterialColors

/**
 * 算法列表：家族头 + 算法项。行顺序固定为枚举顺序（家族连续是 [AiAlgorithm] 的不变量），
 * 选中项显示主色勾选标记；点算法行立即生效（返回即应用），无需确认按钮。
 */
class AiSelectAdapter(
    private val onSelected: (AiAlgorithm) -> Unit,
    selected: AiAlgorithm,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows: List<Row> = buildList {
        for (family in AiFamily.entries) {
            val algorithms = AiAlgorithm.entries.filter { it.family == family }
            if (algorithms.isEmpty()) continue
            add(Row.Family(family))
            algorithms.forEach { add(Row.Algorithm(it)) }
        }
    }

    private var selected: AiAlgorithm = selected

    /** 外部改变选中项时刷新勾选标记 */
    fun select(algorithm: AiAlgorithm) {
        if (algorithm == selected) return
        selected = algorithm
        notifyItemRangeChanged(0, rows.size)
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Family -> TYPE_FAMILY
        is Row.Algorithm -> TYPE_ALGORITHM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FAMILY) {
            FamilyHolder(ItemAiFamilyBinding.inflate(inflater, parent, false))
        } else {
            AlgoHolder(ItemAiAlgorithmBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Family -> (holder as FamilyHolder).binding.familyName.setText(familyNameRes(row.family))
            is Row.Algorithm -> bindAlgorithm((holder as AlgoHolder).binding, row.algorithm)
        }
    }

    private fun bindAlgorithm(binding: ItemAiAlgorithmBinding, algorithm: AiAlgorithm) {
        binding.algorithmName.setText(algorithmNameRes(algorithm))
        val chosen = algorithm == selected
        binding.algorithmCheck.visibility = if (chosen) View.VISIBLE else View.INVISIBLE
        binding.algorithmName.setTextColor(
            MaterialColors.getColor(binding.algorithmName, if (chosen) PRIMARY_ATTR else ON_SURFACE_ATTR)
        )
        binding.root.setOnClickListener {
            if (algorithm != selected) {
                select(algorithm)
                onSelected(algorithm)
            }
        }
    }

    private sealed interface Row {
        data class Family(val family: AiFamily) : Row
        data class Algorithm(val algorithm: AiAlgorithm) : Row
    }

    private class FamilyHolder(val binding: ItemAiFamilyBinding) : RecyclerView.ViewHolder(binding.root)

    private class AlgoHolder(val binding: ItemAiAlgorithmBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val TYPE_FAMILY = 0
        const val TYPE_ALGORITHM = 1
        val PRIMARY_ATTR = androidx.appcompat.R.attr.colorPrimary
        val ON_SURFACE_ATTR = com.google.android.material.R.attr.colorOnSurface
    }
}
