package com.lany.fivechess.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

import com.lany.fivechess.R
import com.lany.fivechess.net.ConnectionItem

import java.util.ArrayList

class ConnectionAdapter(private val mContext: Context, data: List<ConnectionItem>) : BaseAdapter() {
    private var mItems: List<ConnectionItem>? = ArrayList()

    init {
        this.mItems = data
    }

    override fun getCount(): Int {
        return if (mItems != null) mItems!!.size else 0
    }

    override fun getItem(position: Int): ConnectionItem? {
        return if (mItems != null) mItems!![position] else null
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val holder: ViewHolder
        if (convertView == null) {
            val inflate = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            convertView = inflate.inflate(R.layout.list_item, null)
            holder = ViewHolder()
            holder.name = convertView!!.findViewById(R.id.name) as TextView
            holder.ip = convertView.findViewById(R.id.ip) as TextView
            convertView.tag = holder
        } else {
            holder = convertView.tag as ViewHolder
        }
        val item = mItems!![position]
        holder.name!!.text = mContext.getString(R.string.game_player) + item.name
        holder.ip!!.text = "IP地址" + item.ip
        return convertView
    }

    fun changeData(data: List<ConnectionItem>) {
        mItems = data
        notifyDataSetChanged()
    }

    internal inner class ViewHolder {
        var name: TextView? = null
        var ip: TextView? = null
    }
}
