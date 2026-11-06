package io.embrace.android.gradle.integration.framework.smali

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.gradle.integration.framework.ApkDisassembler
import io.embrace.android.gradle.integration.framework.findArtifact
import io.embrace.android.gradle.plugin.util.serialization.JsonSerializer
import okio.FileSystem
import okio.Path
import java.io.File

class SmaliConfigReader(private val fileSystem: FileSystem) {

    fun readSmaliFiles(projectDir: File, classNames: List<String>, buildType: String = "release"): List<Path> {
        val apk = findArtifact(fileSystem, projectDir, "build/outputs/apk/$buildType/", ".apk")
        return ApkDisassembler(fileSystem).disassembleClasses(apk, classNames)
    }

    fun readExpectedConfig(resName: String): ExpectedSmaliConfig {
        return JsonSerializer().fromJson(ResourceReader.readResourceAsText(resName), ExpectedSmaliConfig.serializer())
    }
}
