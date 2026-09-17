package com.github.lany192.fivechess.ui.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.lany192.fivechess.R
import com.github.lany192.fivechess.data.bt.BtDiscoveryManager
import com.github.lany192.fivechess.data.bt.bluetoothAdapterOf
import com.github.lany192.fivechess.data.bt.localBluetoothAddress
import com.github.lany192.fivechess.data.bt.localBluetoothName
import com.github.lany192.fivechess.databinding.ActivityConnectBinding
import com.github.lany192.fivechess.ui.common.setupEdgeToEdge
import com.github.lany192.fivechess.ui.connect.PeerAdapter
import com.github.lany192.fivechess.ui.net.NetGameActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * 蓝牙联机页：权限 / 蓝牙开关就绪后才创建 ViewModel（发现与监听随之启动）
 */
class BluetoothConnectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectBinding
    private val peerAdapter = PeerAdapter(this, idLabel = R.string.label_mac) { item ->
        viewModel.dispatch(BtConnectIntent.PeerClicked(item.ip))
    }
    private var handshakeDialog: AlertDialog? = null
    private var waitDialog: AlertDialog? = null

    private val viewModel: BluetoothConnectViewModel by viewModels {
        viewModelFactory {
            initializer {
                BluetoothConnectViewModel(BtDiscoveryManager(this@BluetoothConnectActivity))
            }
        }
    }

    /** 权限/开关两条就绪路径都可能走到 [proceed]，用标志位保证只初始化一次 */
    private var ready = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            ensureBluetoothEnabled()
        } else {
            Toast.makeText(this, R.string.msg_bt_permission, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val enableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            proceed()
        } else {
            Toast.makeText(this, R.string.msg_bt_check, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            Toast.makeText(this, R.string.msg_bt_unavailable, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (hasBluetoothPermissions()) {
            ensureBluetoothEnabled()
        } else {
            permissionLauncher.launch(BtDiscoveryManager.REQUIRED_PERMISSIONS)
        }
    }

    private fun hasBluetoothPermissions(): Boolean =
        BtDiscoveryManager.REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission")
    private fun ensureBluetoothEnabled() {
        val adapter = bluetoothAdapterOf(this)
        if (adapter == null) {
            Toast.makeText(this, R.string.msg_bt_unavailable, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (adapter.isEnabled) {
            proceed()
            return
        }
        enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    private fun proceed() {
        if (ready) return
        ready = true
        binding = ActivityConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.hint.setText(R.string.bt_scan_hint)
        binding.scan.setText(R.string.bt_scan)
        renderSelfInfo()
        binding.scan.setOnClickListener {
            viewModel.dispatch(BtConnectIntent.ScanClicked)
        }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = peerAdapter
        observeViewModel()
    }

    /**
     * 显示本机蓝牙身份
     *
     * 列表里往往混着一堆无关设备，先让用户知道"自己是哪一个"（对方列表里显示的就是这个名字），
     * 双方对一下名字就能锁定目标。地址在 Android 6 以上通常拿不到，拿不到就只显示名称。
     */
    private fun renderSelfInfo() {
        val adapter = bluetoothAdapterOf(this)
        val name = localBluetoothName(adapter) ?: getString(R.string.bt_name_unknown)
        val address = localBluetoothAddress(adapter)
        binding.selfInfo.text = if (address == null) {
            getString(R.string.bt_self_name, name)
        } else {
            getString(R.string.bt_self_name_mac, name, address)
        }
        binding.selfInfo.visibility = View.VISIBLE
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { peerAdapter.submit(it.peers) }
                }
                launch {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is BtConnectEffect.ShowHandshakeRequest -> showHandshakeDialog(effect.name)
                            BtConnectEffect.DismissHandshake -> handshakeDialog?.dismiss()
                            is BtConnectEffect.ShowConnecting -> showConnectingDialog()
                            BtConnectEffect.DismissConnecting -> waitDialog?.dismiss()
                            is BtConnectEffect.NavigateToGame -> {
                                NetGameActivity.startBluetooth(
                                    this@BluetoothConnectActivity, effect.isServer, effect.address,
                                )
                                // 发现管理器是一次性的（监听口已让给对局页），返回本页也点不动，
                                // 直接出栈，避免留下一个失效的联机页
                                finish()
                            }
                            is BtConnectEffect.ShowMessage -> Toast.makeText(
                                this@BluetoothConnectActivity, effect.text, Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun showHandshakeDialog(name: String) {
        val dialog = handshakeDialog ?: MaterialAlertDialogBuilder(this)
            .setCancelable(false)
            .create()
            .also { handshakeDialog = it }
        dialog.setMessage(name + getString(R.string.fight_request))
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getText(R.string.agree)) { _, _ ->
            viewModel.dispatch(BtConnectIntent.HandshakeAgreed)
        }
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getText(R.string.reject)) { _, _ ->
            viewModel.dispatch(BtConnectIntent.HandshakeRejected)
        }
        if (!dialog.isShowing) dialog.show()
    }

    private fun showConnectingDialog() {
        val dialog = waitDialog ?: MaterialAlertDialogBuilder(this)
            .setCancelable(true)
            .create()
            .also {
                it.setOnCancelListener { viewModel.dispatch(BtConnectIntent.ConnectCancelled) }
                waitDialog = it
            }
        dialog.setMessage(getString(R.string.msg_bt_wait_peer))
        if (!dialog.isShowing) dialog.show()
    }
}
