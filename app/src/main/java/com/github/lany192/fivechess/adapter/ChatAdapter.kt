package com.github.lany192.fivechess.adapter

import java.util.ArrayList
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

import com.github.lany192.fivechess.databinding.ChatItemBinding
import com.github.lany192.fivechess.net.ChatContent

class ChatAdapter(private val mContext: Context, data: List<ChatContent>) : BaseAdapter() {
    private var mData: List<ChatContent> = ArrayList()

    init {
        mData = data
    }

    override fun getCount(): Int {
        return mData.size
    }

    override fun getItem(position: Int): Any {
        return mData[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val holder: ViewHolder
        if (convertView == null) {
            val inflate = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val binding = ChatItemBinding.inflate(inflate, parent, false)
            convertView = binding.root
            holder = ViewHolder()
            holder.title = binding.title
            holder.content = binding.content
            holder.time = binding.time
            convertView.tag = holder
        } else {
            holder = convertView.tag as ViewHolder
        }
        val count = count - 1
        val item = mData[count - position]
        holder.title!!.text = item.connector.name + "(" + item.connector.ip + ")"
        holder.content!!.text = item.content
        holder.time!!.text = item.time
        return convertView
    }

    fun changeData(data: List<ChatContent>) {
        mData = data
        notifyDataSetChanged()
    }

    internal inner class ViewHolder {
        var title: TextView? = null
        var content: TextView? = null
        var time: TextView? = null
    }
}
