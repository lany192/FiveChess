package com.github.lany192.fivechess.data.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.github.lany192.fivechess.data.net.BtSignal
import com.github.lany192.fivechess.data.net.DiscoveryError
import com.github.lany192.fivechess.data.net.DiscoveryEvent
import com.github.lany192.fivechess.data.net.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * 蓝牙发现/握手管理（对齐 [com.github.lany192.fivechess.data.net.LanDiscoveryManager]）
 *
 * 蓝牙没有组播信令，联机请求本身就是一条 RFCOMM 连接：
 * - 被叫方在联机页常驻监听 [Protocol.BT_UUID]，accept 成功即视为收到请求，由用户裁决后回写 [BtSignal]
 * - 主叫方主动 connect，读到 AGREE 才算连上；对端正忙时收到 REJECT，语义与局域网一致
 *
 * 线程模型同局域网版：阻塞 IO 协程 + 关闭 socket 打断 accept()/read()。
 * 设备身份用 MAC 地址（沿用局域网版把自己标识塞进 ConnectionItem.ip 的做法）。
 */
@SuppressLint("MissingPermission")
class BtDiscoveryManager(context: Context) {

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? = bluetoothAdapterOf(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<DiscoveryEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<DiscoveryEvent> = _events.asSharedFlow()

    private var serverSocket: BluetoothServerSocket? = null

    /** 已 accept 但尚未被用户裁决的请求方连接 */
    @Volatile
    private var pendingSocket: BluetoothSocket? = null

    @Volatile
    private var running = false

    private var receiverRegistered = false

    fun start() {
        if (running) return
        val adapter = adapter ?: run {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_DISABLED))
            return
        }
        val enabled = try {
            adapter.isEnabled
        } catch (e: SecurityException) {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_PERMISSION_DENIED))
            return
        }
        if (!enabled) {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_DISABLED))
            return
        }
        running = true
        registerReceiver()
        // 已配对设备先入列表：这是蓝牙最主要的发现来源，未配对设备靠扫描补充
        publishBondedDevices(adapter)
        scope.launch { acceptLoop(adapter) }
    }

    fun stop() {
        if (!running) return
        running = false
        unregisterReceiver()
        closeAll()
        scope.cancel()
    }

    /** 扫描附近设备（已配对设备不受影响，会重新播报一次） */
    fun scan() {
        val adapter = adapter ?: return
        if (!running) return
        publishBondedDevices(adapter)
        try {
            adapter.cancelDiscovery()
            if (!adapter.startDiscovery()) {
                _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_DISABLED))
            }
        } catch (e: SecurityException) {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_PERMISSION_DENIED))
        }
    }

    /** 主叫：向指定设备发起联机请求 */
    fun askConnect(address: String) {
        scope.launch { clientHandshake(address) }
    }

    /**
     * 停止监听（保留当前待裁决连接）
     *
     * 同意联机时必须先调用：对局页要用同一个服务 UUID 重新监听，
     * 若旧监听口还在，主叫的对局连接可能落回这个即将被关闭的 socket 上，随即被判为断线。
     */
    fun stopListening() {
        closeQuietly(serverSocket)
        serverSocket = null
    }

    /**
     * 被叫：同意联机
     *
     * suspend 且返回前完成回写，调用方必须等它返回再 [stop]，
     * 否则裁决字节还没发出去 socket 就被关了，主叫只能干等到超时。
     */
    suspend fun accept() = withContext(Dispatchers.IO) { resolve(BtSignal.AGREE) }

    /** 被叫：拒绝联机 */
    suspend fun reject() = withContext(Dispatchers.IO) { resolve(BtSignal.REJECT) }

    // ---------- 被叫：监听 + 分发给用户裁决 ----------

    private suspend fun acceptLoop(adapter: BluetoothAdapter) {
        val server = try {
            adapter.listenUsingRfcommWithServiceRecord(
                Protocol.BT_SERVICE_NAME,
                UUID.fromString(Protocol.BT_UUID),
            )
        } catch (e: IOException) {
            Log.d(TAG, "listen fail: ${e.message}")
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_CONNECT_FAILED))
            return
        } catch (e: SecurityException) {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_PERMISSION_DENIED))
            return
        }
        serverSocket = server
        Log.d(TAG, "bt listening for incoming connect")
        while (running) {
            val incoming = try {
                server.accept()
            } catch (e: IOException) {
                break // stop() 关闭 socket 打断阻塞
            }
            if (!running) {
                closeQuietly(incoming)
                break
            }
            if (pendingSocket != null) {
                // 忙：立即拒绝，避免请求方空等（对齐局域网的单请求排队语义）
                respond(incoming, BtSignal.REJECT)
                continue
            }
            pendingSocket = incoming
            val device = incoming.remoteDevice
            _events.tryEmit(DiscoveryEvent.HandshakeRequested(deviceName(device), device.address))
        }
        closeQuietly(serverSocket)
        serverSocket = null
    }

    private fun resolve(signal: BtSignal) {
        val socket = pendingSocket ?: return
        pendingSocket = null
        respond(socket, signal)
    }

    private fun respond(socket: BluetoothSocket, signal: BtSignal) {
        try {
            socket.outputStream.write(byteArrayOf(signal.b))
            socket.outputStream.flush()
        } catch (e: IOException) {
            Log.d(TAG, "respond fail: ${e.message}")
        }
        closeQuietly(socket)
    }

    // ---------- 主叫：连接 + 等裁决 ----------

    private fun clientHandshake(address: String) {
        val adapter = adapter ?: run {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_DISABLED))
            return
        }
        // 发现过程会显著拖慢甚至阻塞 RFCOMM 建连，先取消
        try {
            adapter.cancelDiscovery()
        } catch (e: SecurityException) {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_PERMISSION_DENIED))
            return
        } catch (e: Exception) {
            Log.d(TAG, "cancel discovery fail: ${e.message}")
        }
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_CONNECT_FAILED))
            return
        }
        val name = deviceName(device)
        val socket = try {
            device.createRfcommSocketToServiceRecord(UUID.fromString(Protocol.BT_UUID))
        } catch (e: IOException) {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_CONNECT_FAILED))
            return
        } catch (e: SecurityException) {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_PERMISSION_DENIED))
            return
        }
        try {
            socket.connect()
        } catch (e: IOException) {
            // 对端不在联机页 / 不在范围内 / 未配对，都会走到这里
            Log.d(TAG, "handshake connect fail: ${e.message}")
            closeQuietly(socket)
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_CONNECT_FAILED))
            return
        }
        val signal = readSignal(socket)
        closeQuietly(socket)
        when (signal) {
            BtSignal.AGREE.b.toInt() -> _events.tryEmit(DiscoveryEvent.HandshakeAccepted(name, address))
            // REJECT 或对端直接关闭（-1），一律按拒绝处理
            else -> _events.tryEmit(DiscoveryEvent.HandshakeRejected(name, address))
        }
    }

    /**
     * 等对端裁决字节
     *
     * 阻塞读不响应协程取消，靠看门狗关 socket 打断，避免对端应用闪退后永久挂住。
     */
    private fun readSignal(socket: BluetoothSocket): Int {
        val watchdog = scope.launch {
            delay(HANDSHAKE_TIMEOUT_MS)
            Log.d(TAG, "handshake wait timeout")
            closeQuietly(socket)
        }
        return try {
            socket.inputStream.read()
        } catch (e: IOException) {
            -1
        } finally {
            watchdog.cancel()
        }
    }

    // ---------- 系统广播 ----------

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = IntentCompat.getParcelableExtra(
                        intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java,
                    ) ?: return
                    _events.tryEmit(DiscoveryEvent.PeerJoined(deviceName(device), device.address))
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = IntentCompat.getParcelableExtra(
                        intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java,
                    ) ?: return
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    if (state == BluetoothDevice.BOND_BONDED) {
                        _events.tryEmit(DiscoveryEvent.PeerJoined(deviceName(device), device.address))
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_DISABLED))
                    }
                }
            }
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        // 蓝牙栈是独立进程（com.android.bluetooth），Android 14 起用 NOT_EXPORTED 会收不到这些系统广播
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        try {
            appContext.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "receiver already unregistered")
        }
        receiverRegistered = false
    }

    // ---------- 工具 ----------

    private fun publishBondedDevices(adapter: BluetoothAdapter) {
        val bonded = try {
            adapter.bondedDevices
        } catch (e: SecurityException) {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.BT_PERMISSION_DENIED))
            return
        }
        bonded.orEmpty().forEach { _events.tryEmit(DiscoveryEvent.PeerJoined(deviceName(it), it.address)) }
    }

    private fun deviceName(device: BluetoothDevice): String = try {
        device.name
    } catch (e: SecurityException) {
        null
    } ?: device.address

    private fun closeAll() {
        closeQuietly(pendingSocket)
        pendingSocket = null
        closeQuietly(serverSocket)
        serverSocket = null
    }

    private fun closeQuietly(target: BluetoothSocket?) {
        try {
            target?.close()
        } catch (_: IOException) {
        }
    }

    private fun closeQuietly(target: BluetoothServerSocket?) {
        try {
            target?.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        const val TAG = "BtDiscovery"
        const val HANDSHAKE_TIMEOUT_MS = 20_000L

        /** 蓝牙运行时权限随系统版本变化，集中在此处便于 Activity 复用 */
        val REQUIRED_PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            emptyArray()
        }
    }
}
