package com.github.lany192.gomoku.build

/**
 * 构建期字符串加密。
 *
 * 与产物里注入的 StrGuard.d(String, int) 必须是同一套算法（逐字节异或，自反），
 * 只改其一会让产物中的所有字符串变成乱码。
 */
object StringCipher {

    /** 16 字节密钥。只影响新构建的产物，不涉及任何持久化数据。 */
    val KEY = byteArrayOf(
        0x3B, 0x71, 0x5D, 0x26, 0x4C, 0x19, 0x6E, 0x2A,
        0x57, 0x2F, 0x68, 0x11, 0x39, 0x4A, 0x1D, 0x74,
    )

    /**
     * 明文 → 密文：先按 UTF-8 取字节异或，再把每个字节映射成 0..255 的字符，
     * 这样密文可以当成普通 String 常量放进常量池（常量池用 modified UTF-8，能装下任意字节）。
     */
    fun encrypt(text: String, offset: Int): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        for (i in bytes.indices) {
            bytes[i] = (bytes[i].toInt() xor KEY[(i + offset) % KEY.size].toInt()).toByte()
        }
        return String(bytes, Charsets.ISO_8859_1)
    }
}
