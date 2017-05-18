package com.lany.fivechess.adapter

import java.util.ArrayList
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

import com.lany.fivechess.R
import com.lany.fivechess.net.ChatContent

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
            convertView = inflate.inflate(R.layout.chat_item, null)
            holder = ViewHolder()
            holder.title = convertView!!.findViewById(R.id.title) as TextView
            holder.content = convertView.findViewById(R.id.content) as TextView
            holder.time = convertView.findViewById(R.id.time) as TextView
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
