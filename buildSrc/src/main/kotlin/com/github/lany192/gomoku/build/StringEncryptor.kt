package com.github.lany192.gomoku.build

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.Random

/**
 * 把 class 里的字符串常量替换成 `StrGuard.d(密文, 偏移)` 调用，并生成 StrGuard 本身。
 *
 * 只改写指令里的常量（LDC）。注解值和 invokedynamic 的引导参数（Java 9+ 字符串拼接）必须是真常量，
 * 改不了，所以这两处仍是明文 —— 项目代码里的模板串由 Kotlin 编译成 StringBuilder，不受影响。
 */
object StringEncryptor {

    /** 注入到产物里的解密类。R8 会照常重命名/内联它，无需任何 keep 规则。 */
    const val GUARD_CLASS = "com/github/lany192/gomoku/guard/StrGuard"
    const val GUARD_FILE = "$GUARD_CLASS.class"

    private const val GUARD_METHOD = "d"
    private const val GUARD_DESC = "(Ljava/lang/String;I)Ljava/lang/String;"
    private const val KEY_FIELD = "K"

    class Encrypted(val bytes: ByteArray, val stringCount: Int)

    /**
     * 改写单个 class：每条非空字符串常量都会加密，并带上一个随机偏移，
     * 这样相同明文也不会产生相同密文。
     */
    fun encrypt(classBytes: ByteArray, random: Random): Encrypted {
        // 不给 ClassWriter 传 ClassReader：否则常量池会被原样复制，明文常量会留在 class 里
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        val counter = IntArray(1)
        ClassReader(classBytes).accept(Rewriter(writer, random, counter), 0)
        return Encrypted(writer.toByteArray(), counter[0])
    }

    /** 生成解密类：与 [StringCipher.encrypt] 互逆。 */
    fun guardClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
            GUARD_CLASS,
            null,
            "java/lang/Object",
            null,
        )

        cw.visitField(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, KEY_FIELD, "[B", null, null)
            .visitEnd()

        cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null).apply {
            visitCode()
            pushInt(this, StringCipher.KEY.size)
            visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE)
            visitFieldInsn(Opcodes.PUTSTATIC, GUARD_CLASS, KEY_FIELD, "[B")
            StringCipher.KEY.forEachIndexed { index, byte ->
                visitFieldInsn(Opcodes.GETSTATIC, GUARD_CLASS, KEY_FIELD, "[B")
                pushInt(this, index)
                pushInt(this, byte.toInt() and 0xFF)
                visitInsn(Opcodes.BASTORE)
            }
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        // static String d(String cipher, int offset) {
        //     byte[] b = cipher.getBytes(ISO_8859_1);
        //     for (int i = 0; i < b.length; i++) b[i] = (byte) (b[i] ^ K[(i + offset) % K.length]);
        //     return new String(b, UTF_8);
        // }
        cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, GUARD_METHOD, GUARD_DESC, null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            charsetField(this, "ISO_8859_1")
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "getBytes",
                "(Ljava/nio/charset/Charset;)[B",
                false,
            )
            visitVarInsn(Opcodes.ASTORE, 2)

            val loop = Label()
            val end = Label()
            pushInt(this, 0)
            visitVarInsn(Opcodes.ISTORE, 3)
            visitLabel(loop)
            visitVarInsn(Opcodes.ILOAD, 3)
            visitVarInsn(Opcodes.ALOAD, 2)
            visitInsn(Opcodes.ARRAYLENGTH)
            visitJumpInsn(Opcodes.IF_ICMPGE, end)
            visitVarInsn(Opcodes.ALOAD, 2)
            visitVarInsn(Opcodes.ILOAD, 3)
            visitVarInsn(Opcodes.ALOAD, 2)
            visitVarInsn(Opcodes.ILOAD, 3)
            visitInsn(Opcodes.BALOAD)
            visitFieldInsn(Opcodes.GETSTATIC, GUARD_CLASS, KEY_FIELD, "[B")
            visitVarInsn(Opcodes.ILOAD, 3)
            visitVarInsn(Opcodes.ILOAD, 1)
            visitInsn(Opcodes.IADD)
            visitFieldInsn(Opcodes.GETSTATIC, GUARD_CLASS, KEY_FIELD, "[B")
            visitInsn(Opcodes.ARRAYLENGTH)
            visitInsn(Opcodes.IREM)
            visitInsn(Opcodes.BALOAD)
            visitInsn(Opcodes.IXOR)
            visitInsn(Opcodes.BASTORE)
            visitIincInsn(3, 1)
            visitJumpInsn(Opcodes.GOTO, loop)
            visitLabel(end)

            visitTypeInsn(Opcodes.NEW, "java/lang/String")
            visitInsn(Opcodes.DUP)
            visitVarInsn(Opcodes.ALOAD, 2)
            charsetField(this, "UTF_8")
            visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/String",
                "<init>",
                "([BLjava/nio/charset/Charset;)V",
                false,
            )
            visitInsn(Opcodes.ARETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        cw.visitEnd()
        return cw.toByteArray()
    }

    private class Rewriter(
        writer: ClassVisitor,
        private val random: Random,
        private val counter: IntArray,
    ) : ClassVisitor(Opcodes.ASM9, writer) {

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<String>?,
        ): MethodVisitor {
            val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
            return object : MethodVisitor(Opcodes.ASM9, delegate) {
                override fun visitLdcInsn(value: Any?) {
                    if (value !is String || value.isEmpty()) {
                        super.visitLdcInsn(value)
                        return
                    }
                    val offset = random.nextInt(StringCipher.KEY.size)
                    super.visitLdcInsn(StringCipher.encrypt(value, offset))
                    super.visitLdcInsn(offset)
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, GUARD_CLASS, GUARD_METHOD, GUARD_DESC, false)
                    counter[0]++
                }
            }
        }
    }

    private fun charsetField(mv: MethodVisitor, name: String) {
        mv.visitFieldInsn(
            Opcodes.GETSTATIC,
            "java/nio/charset/StandardCharsets",
            name,
            "Ljava/nio/charset/Charset;",
        )
    }

    private fun pushInt(mv: MethodVisitor, value: Int) {
        when {
            value >= -1 && value <= 5 -> mv.visitInsn(Opcodes.ICONST_0 + value)
            value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE -> mv.visitIntInsn(Opcodes.BIPUSH, value)
            value >= Short.MIN_VALUE && value <= Short.MAX_VALUE -> mv.visitIntInsn(Opcodes.SIPUSH, value)
            else -> mv.visitLdcInsn(value)
        }
    }
}
