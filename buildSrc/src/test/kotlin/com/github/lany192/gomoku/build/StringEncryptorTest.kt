package com.github.lany192.gomoku.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.Random
import javax.tools.ToolProvider

/**
 * 端到端验证变换结果：编译 fixture → 改写字节码 → 真加载真调用，
 * 顺带验证原始 class 里确实不再有明文（含 StackMapTable 是否被破坏）。
 */
class StringEncryptorTest {

    @Test
    fun `加密后的类仍能在运行期还原字符串`() {
        val workspace = Files.createTempDirectory("string-encrypt-test").toFile()
        try {
            val source = File(workspace, "src/fixture/Fixture.java")
            source.parentFile.mkdirs()
            source.writeText(
                """
                package fixture;

                public class Fixture {
                    public static String chinese() { return "对方已退出"; }
                    public static String ascii() { return "ai_level_v2"; }
                    public static String empty() { return ""; }
                    public static String repeat() { return "对方已退出"; }
                    public static String branch(boolean flag) { return flag ? "黑棋" : "白棋"; }
                    public static String concat(String who) { return "hi, " + who; }
                    public static String builder(String who) {
                        return new StringBuilder("玩家 ").append(who).append(" 获胜").toString();
                    }
                }
                """.trimIndent()
            )
            val classes = File(workspace, "classes")
            classes.mkdirs()
            javac(source, classes)

            val fixture = File(classes, "fixture/Fixture.class")
            val plain = fixture.readBytes()
            assertTrue("自检失败：明文字符串本该能被检出", plain.asLatin1().contains("对方已退出".utf8AsLatin1()))
            assertTrue(plain.asLatin1().contains("ai_level_v2"))

            val encrypted = StringEncryptor.encrypt(plain, Random(1))
            assertTrue("应当加密中文/ASCII/StringBuilder 三处以上的常量", encrypted.stringCount >= 4)

            val cipher = encrypted.bytes.asLatin1()
            assertFalse(cipher.contains("对方已退出".utf8AsLatin1()))
            assertFalse(cipher.contains("ai_level_v2"))
            assertFalse(cipher.contains("玩家 ".utf8AsLatin1()))
            assertFalse(cipher.contains("获胜".utf8AsLatin1()))

            fixture.writeBytes(encrypted.bytes)
            File(classes, StringEncryptor.GUARD_FILE).apply { parentFile.mkdirs() }
                .writeBytes(StringEncryptor.guardClass())

            URLClassLoader(arrayOf(classes.toURI().toURL()), null).use { loader ->
                val loaded = loader.loadClass("fixture.Fixture")
                assertEquals("对方已退出", loaded.getMethod("chinese").invoke(null))
                assertEquals("ai_level_v2", loaded.getMethod("ascii").invoke(null))
                assertEquals("", loaded.getMethod("empty").invoke(null))
                assertEquals("对方已退出", loaded.getMethod("repeat").invoke(null))
                assertEquals("黑棋", loaded.getMethod("branch", Boolean::class.java).invoke(null, true))
                assertEquals("白棋", loaded.getMethod("branch", Boolean::class.java).invoke(null, false))
                assertEquals("hi, 你", loaded.getMethod("concat", String::class.java).invoke(null, "你"))
                assertEquals("玩家 甲 获胜", loaded.getMethod("builder", String::class.java).invoke(null, "甲"))
            }
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun `同一明文配不同偏移得到不同密文`() {
        val text = "ai_level_v2 五子棋"
        val first = StringCipher.encrypt(text, 0)
        val second = StringCipher.encrypt(text, 7)
        assertNotEquals(first, second)
        assertFalse(first.contains("ai_level_v2"))
        assertFalse(second.contains(text))
    }

    private fun javac(source: File, outputDir: File) {
        val compiler = ToolProvider.getSystemJavaCompiler()
        assertNotNull("测试需要完整 JDK（当前 JVM 没有内置编译器）", compiler)
        val exit = compiler!!.run(null, null, null, "--release", "11", "-d", outputDir.path, source.path)
        assertEquals("fixture 编译失败", 0, exit)
    }

    private fun ByteArray.asLatin1(): String = String(this, Charsets.ISO_8859_1)

    /** class 常量池是 modified UTF-8，明文中文在 Latin-1 视角下是这串乱码 */
    private fun String.utf8AsLatin1(): String = String(toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
}
