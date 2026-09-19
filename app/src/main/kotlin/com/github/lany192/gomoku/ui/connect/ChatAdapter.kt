package com.github.lany192.gomoku.ui.connect

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.lany192.gomoku.R
import com.github.lany192.gomoku.data.net.ChatContent
import com.github.lany192.gomoku.databinding.ChatItemBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    private var items: List<ChatContent> = emptyList()

    fun submit(data: List<ChatContent>) {
        items = data
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ChatItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // 最新消息显示在列表顶部（沿用旧版倒序展示）
        val item = items[items.size - 1 - position]
        // 自己的消息标题标上"自己"，与对端区分（协议里不带此标记，仅本地回显用）
        holder.binding.title.text = if (item.self) {
            holder.binding.root.context.getString(R.string.myself) + "(" + item.connector + ")"
        } else {
            item.connector
        }
        holder.binding.content.text = item.content
        holder.binding.time.text = TIME_FORMAT.format(Date(item.time))
    }

    class ViewHolder(val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        val TIME_FORMAT = SimpleDateFormat("yyyy-M-d HH:mm:ss", Locale.getDefault())
    }
}
