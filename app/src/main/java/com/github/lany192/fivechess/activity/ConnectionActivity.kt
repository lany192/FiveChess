package com.github.lany192.fivechess.activity

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.view.View
import android.view.View.OnClickListener
import android.widget.AdapterView.OnItemClickListener
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.lany192.fivechess.R
import com.github.lany192.fivechess.adapter.ChatAdapter
import com.github.lany192.fivechess.adapter.ConnectionAdapter
import com.github.lany192.fivechess.databinding.ActivityConnectBinding
import com.github.lany192.fivechess.databinding.ChatDialogBinding
import com.github.lany192.fivechess.net.ChatContent
import com.github.lany192.fivechess.net.ConnectConstants
import com.github.lany192.fivechess.net.ConnectionItem
import com.github.lany192.fivechess.net.ConnnectingService
import java.util.*

class ConnectionActivity : AppCompatActivity(), OnClickListener {
    private val mConnections = ArrayList<ConnectionItem>()
    private var mAdapter: ConnectionAdapter? = null
    private var mIP: String? = null
    private var mCM: ConnnectingService? = null
    // 联机请求对话框
    private var mConnectDialog: AlertDialog? = null
    // 联机请求等待对话框
    private var waitDialog: ProgressDialog? = null
    // 显示聊天对话框
    private var mChatDialog: Dialog? = null
    private var mChatAdapter: ChatAdapter? = null
    private val mChats = ArrayList<ChatContent>()
    private var mScanDialog: ProgressDialog? = null
    
    private lateinit var binding: ActivityConnectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mScanDialog = ProgressDialog(this)
        mScanDialog!!.setMessage(getString(R.string.scan_loading))
        initView()
        initNet()
    }

    /**
     * 处理网络回调信息，刷新界面
     */
    private val mHandler = object : Handler(Looper.getMainLooper()) {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                ConnectConstants.ON_JOIN -> {
                    val add = getConnectItem(msg)
                    if (!mConnections.contains(add)) {
                        mConnections.add(add)
                        mAdapter!!.changeData(mConnections)
                    }
                }
                ConnectConstants.ON_EXIT -> {
                    val remove = getConnectItem(msg)
                    if (mConnections.contains(remove)) {
                        mConnections.remove(remove)
                        mAdapter!!.changeData(mConnections)
                    }
                }
                ConnectConstants.CONNECT_ASK -> {
                    val ask = getConnectItem(msg)
                    showConnectDialog(ask.name, ask.ip)
                }

                ConnectConstants.CHAT_ONE -> {
                    val ci = getConnectItem(msg)

                    val chat = msg.peekData()?.getString("chat")
                    val cc = ChatContent(ci, chat!!)
                    mChats.add(cc)
                    showChatDialog()
                }
                ConnectConstants.CONNECT_AGREE -> {
                    if (waitDialog != null && waitDialog!!.isShowing) {
                        waitDialog!!.dismiss()
                    }
                    val ip = msg.peekData()?.getString("ip")
                    WifiGameActivity.start(this@ConnectionActivity, false, ip!!)
                }
                ConnectConstants.CONNECT_REJECT -> {
                    if (waitDialog != null && waitDialog!!.isShowing) {
                        waitDialog!!.dismiss()
                    }
                    Toast.makeText(this@ConnectionActivity, "对方拒绝了你的请求",
                            Toast.LENGTH_LONG).show()
                }
                else -> {
                }
            }
        }
    }

    private fun initView() {
        val scan = binding.scan
        scan.setOnClickListener(this)
        binding.list = binding.list
        mAdapter = ConnectionAdapter(this, mConnections)
        binding.list.adapter = mAdapter
        binding.list.onItemClickListener = OnItemClickListener { parent, v, position, id ->
            val ipDst = mConnections[position].ip
            mCM!!.sendAskConnect(ipDst)
            val title = "请求对战"
            val message = "等待" + ipDst + "回应.请稍后...."
            showProgressDialog(title, message)
        }
        // 屏蔽对话功能
        // binding.list.setOnItemLongClickListener(new OnItemLongClickListener() {
        //
        // @Override
        // public boolean onItemLongClick(AdapterView<?> parent, View v,
        // int position, long id) {
        // String ipDst = mConnections.get(position).ip;
        // showMenuDialog(ipDst);
        // return true;
        // }
        // });
    }

    private fun initNet() {
        mIP = ip
        if (TextUtils.isEmpty(mIP)) {
            Toast.makeText(this, "请检查wifi连接后重试", Toast.LENGTH_LONG).show()
            finish()
        }

    }

    override fun onStart() {
        super.onStart()
        mCM = ConnnectingService(mIP, mHandler)
        mCM!!.start()
        mCM!!.sendScanMsg()
    }

    override fun onStop() {
        super.onStop()
        mCM!!.stop()
        mCM!!.sendExitMsg()
    }

    /**
     * 从消息里面获取数据并生成ConnectionItem对象

     * @param msg
     * *
     * @return ConnectionItem
     */
    private fun getConnectItem(msg: Message): ConnectionItem {
        val data = msg.peekData()
        val name = data?.getString("name")
        val ip = data?.getString("ip")
        val ci = ConnectionItem(name, ip)
        return ci
    }

    /**
     * 获取本机的ip地址,通过wifi连接局域网的情况

     * @return ip地址
     */
    private // 检查Wifi状态
            // 获取32位整型IP地址
            // 把整型地址转换成"*. *. *. *"地址
    val ip: String?
        get() {
            val wm = getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wm.isWifiEnabled) {
//                Log.d(TAG, "wifi is not enable,enable wifi first")
                return null
            }
            val wi = wm.connectionInfo
            val ipAdd = wi.ipAddress
            val ip = intToIp(ipAdd)
            return ip
        }

    private fun intToIp(i: Int): String {
        return (i and 0xFF).toString() + "." + (i shr 8 and 0xFF) + "." + (i shr 16 and 0xFF) + "." + (i shr 24 and 0xFF)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.scan -> {
                mScanDialog!!.show()
                mCM!!.sendScanMsg()
            }

            else -> {
            }
        }
    }


    private fun showConnectDialog(name: String, ip: String) {
        val msg = name + getString(R.string.fight_request)
        val listener = DialogInterface.OnClickListener { dialog, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> {
                    mCM!!.accept(ip)
                    WifiGameActivity.start(this@ConnectionActivity, true, ip)
                }
                DialogInterface.BUTTON_NEGATIVE -> mCM!!.reject(ip)
                else -> {
                }
            }
        }
        if (mConnectDialog == null) {
            val b = AlertDialog.Builder(this)
            b.setCancelable(false)
            b.setMessage(msg)
            b.setPositiveButton(R.string.agree, listener)
            b.setNegativeButton(R.string.reject, listener)
            mConnectDialog = b.create()
        } else {
            mConnectDialog!!.setMessage(msg)
            mConnectDialog!!.setButton(DialogInterface.BUTTON_POSITIVE,
                    getText(R.string.agree), listener)
            mConnectDialog!!.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getText(R.string.reject), listener)
        }
        if (!mConnectDialog!!.isShowing) {
            mConnectDialog!!.show()
        }
    }

    /**
     * 显示聊天内容对话框
     */
    private fun showChatDialog() {
        if (mChatDialog == null) {
            val b = AlertDialog.Builder(this)
            b.setTitle("对话")
            val view = layoutInflater.inflate(R.layout.chat_dialog, null)
            val binding = ChatDialogBinding.bind(view)
            mChatAdapter = ChatAdapter(this, mChats)
            binding.listChat.adapter = mChatAdapter
            b.setView(view)
            mChatDialog = b.create()
            mChatDialog!!.show()
        } else {
            if (mChatDialog!!.isShowing) {
                mChatAdapter!!.notifyDataSetChanged()
            } else {
                mChatDialog!!.show()
            }
        }
    }

    private fun showProgressDialog(title: String, message: String) {
        if (waitDialog == null) {
            waitDialog = ProgressDialog(this)
        }
        //waitDialog.setTitle(title);
        waitDialog!!.setMessage(message)
        waitDialog!!.isIndeterminate = true
        waitDialog!!.setCancelable(true)
        waitDialog!!.show()
    }
}