package com.github.lany192.fivechess.ui.common

import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * 统一启用 edge-to-edge 并把系统栏/挖孔 insets 叠加到根布局 padding 上
 *
 * targetSdk 36 强制内容延伸到系统栏；状态栏图标颜色由 [enableEdgeToEdge] 按系统深浅自动处理。
 * 注意先记录 XML 初始 padding 再叠加，避免 insets 覆盖布局内边距。
 */
fun AppCompatActivity.setupEdgeToEdge(root: View) {
    enableEdgeToEdge()
    val initialPadding = intArrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        v.updatePadding(
            initialPadding[0] + bars.left,
            initialPadding[1] + bars.top,
            initialPadding[2] + bars.right,
            initialPadding[3] + bars.bottom,
        )
        insets
    }
}
