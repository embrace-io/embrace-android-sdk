package io.embrace.android.gradle.integration.framework

import java.io.File

internal class DecodedApk(
    private val stringTable: Map<String, String>,
    private val smaliFiles: Map<String, File>,
) {
    fun getStringResource(name: String): String? {
        return stringTable[name]
    }

    fun getSmaliFiles(names: List<String>): List<File> = names.map { name ->
        smaliFiles[name] ?: error("Smali file named '$name' not found")
    }
}
