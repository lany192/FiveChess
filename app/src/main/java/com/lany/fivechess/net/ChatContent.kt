package com.lany.fivechess.net

import java.text.SimpleDateFormat
import java.util.*

class ChatContent(var connector: ConnectionItem, var content: String) {
    var time: String = SimpleDateFormat("yyyy-M-d HH:mm:ss", Locale.getDefault()).format(Date())
}
