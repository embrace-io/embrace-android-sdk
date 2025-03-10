package io.embrace.android.gradle.integration.framework

import brut.androlib.ApkDecoder
import brut.androlib.Config
import brut.directory.ExtFile
import org.w3c.dom.Element
import java.io.File
import java.nio.file.Files
import java.util.LinkedList
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Disassembles an APK using APK tool and inspects the contents.
 */
internal class ApkDisassembler {

    private lateinit var outDir: File

    fun disassembleApk(apkFile: File): DecodedApk {
        if (!apkFile.exists()) {
            error("APK file does not exist at ${apkFile.path}")
        }

        outDir = decodeApk(apkFile)
        outDir.exists()

        val stringTable = readStringTable(outDir)
        val smaliFiles = readSmaliFiles(outDir)
        return DecodedApk(stringTable, smaliFiles)
    }

    private fun decodeApk(apkFile: File): File {
        val outDir = Files.createTempDirectory("decoded_apk").toFile()
        val config = Config().apply {
            isForceDelete = true
        }
        ApkDecoder(ExtFile(apkFile), config).decode(outDir)
        return outDir
    }

    private fun readStringTable(outDir: File): Map<String, String> {
        val stringTableFile = File(outDir, "res/values/strings.xml")
        if (!stringTableFile.exists()) {
            return emptyMap()
        }
        stringTableFile.inputStream().buffered().use { stream ->
            val table = mutableMapOf<String, String>()
            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = builder.parse(stream)
            doc.documentElement.normalize()
            val nodes = doc.getElementsByTagName("string")

            for (i in 0 until nodes.length) {
                val entry = nodes.item(i)
                if (entry is Element) {
                    val name = entry.getAttribute("name")
                    val value = entry.textContent
                    table[name] = value
                }
            }
            return table
        }
    }

    private fun readSmaliFiles(outDir: File): Map<String, File> {
        val smaliDir = File(outDir, "smali")
        if (!smaliDir.exists() || !smaliDir.isDirectory) {
            return emptyMap()
        }
        val result = mutableMapOf<String, File>()
        val queue = LinkedList<File>().apply { add(smaliDir) }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            current.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    queue.add(file)
                } else if (file.isFile) {
                    val key = file.absolutePath.substringAfter(smaliDir.absolutePath)
                        .removeSuffix(".smali")
                    result[key] = file
                }
            }
        }
        return result
    }
}
