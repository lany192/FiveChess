package com.github.lany192.gomoku.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 加密项目自己 class 里的字符串常量，接在 release 变体的编译产物上（R8 之前）。
 *
 * AGP 的 toTransform 要求多输入合并成单个 jar，非 class 条目（META-INF 等）原样透传。
 */
abstract class EncryptStringsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputJars: ListProperty<RegularFile>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectories: ListProperty<Directory>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun encrypt() {
        val inputs = LinkedHashMap<String, ByteArray>()
        inputDirectories.get().forEach { directory ->
            val root = directory.asFile
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                inputs.putIfAbsent(file.relativeTo(root).invariantSeparatorsPath, file.readBytes())
            }
        }
        inputJars.get().forEach { jar ->
            ZipFile(jar.asFile).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                    inputs.putIfAbsent(entry.name, zip.getInputStream(entry).use { it.readBytes() })
                }
            }
        }
        check(inputs.isNotEmpty()) { "没有拿到任何 class 输入，字符串加密未生效" }

        val random = Random()
        var classCount = 0
        var stringCount = 0
        val outputs = LinkedHashMap<String, ByteArray>(inputs.size + 1)
        inputs.forEach { (name, bytes) ->
            val encryptable = name.endsWith(".class") &&
                name != StringEncryptor.GUARD_FILE &&
                !name.endsWith("module-info.class")
            if (encryptable) {
                val encrypted = StringEncryptor.encrypt(bytes, random)
                classCount++
                stringCount += encrypted.stringCount
                outputs[name] = encrypted.bytes
            } else {
                outputs[name] = bytes
            }
        }
        outputs[StringEncryptor.GUARD_FILE] = StringEncryptor.guardClass()

        val target = outputJar.get().asFile
        target.parentFile.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            outputs.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        logger.lifecycle("字符串加密：${classCount} 个类、${stringCount} 条字符串常量")
    }
}
