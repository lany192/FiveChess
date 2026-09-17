package com.github.lany192.fivechess.data.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

/**
 * 蓝牙适配器访问工具
 *
 * 这些读取都需要 BLUETOOTH_CONNECT（API 31+ 起为运行时权限），
 * 统一在这里吞掉 SecurityException 返回 null，避免调用方各自 try/catch。
 */

/** Android 6 起第三方应用拿不到真实蓝牙地址，系统一律回这个占位值 */
private const val PLACEHOLDER_ADDRESS = "02:00:00:00:00:00"

/** 取本机蓝牙适配器；不支持蓝牙或服务缺失时返回 null */
fun bluetoothAdapterOf(context: Context): BluetoothAdapter? =
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

/** 本机蓝牙名称；未设置或无权读取时为 null */
@SuppressLint("MissingPermission")
fun localBluetoothName(adapter: BluetoothAdapter?): String? = try {
    adapter?.name?.takeIf { it.isNotBlank() }
} catch (e: SecurityException) {
    null
}

/**
 * 本机蓝牙地址；不可用时返回 null
 *
 * Android 6 起第三方应用无法获取真实地址（系统只回 [PLACEHOLDER_ADDRESS]，
 * 11 起更是直接要求 LOCAL_MAC_ADDRESS 系统权限），所以联机页要按"可能没有"来渲染。
 */
@SuppressLint("MissingPermission")
fun localBluetoothAddress(adapter: BluetoothAdapter?): String? = try {
    adapter?.address?.takeIf { it.isNotBlank() && it != PLACEHOLDER_ADDRESS }
} catch (e: SecurityException) {
    null
}
