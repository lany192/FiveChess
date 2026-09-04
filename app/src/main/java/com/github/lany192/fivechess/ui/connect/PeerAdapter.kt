package com.github.lany192.fivechess.ui.connect

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.lany192.fivechess.R
import com.github.lany192.fivechess.data.net.ConnectionItem
import com.github.lany192.fivechess.databinding.ListItemBinding

class PeerAdapter(
    private val context: Context,
    private val onItemClick: (ConnectionItem) -> Unit,
) : RecyclerView.Adapter<PeerAdapter.ViewHolder>() {

    private var items: List<ConnectionItem> = emptyList()

    fun submit(data: List<ConnectionItem>) {
        items = data
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.name.text = context.getString(R.string.game_player) + item.name
        holder.binding.ip.text = "IP地址" + item.ip
        holder.binding.root.setOnClickListener { onItemClick(item) }
    }

    class ViewHolder(val binding: ListItemBinding) : RecyclerView.ViewHolder(binding.root)
}
