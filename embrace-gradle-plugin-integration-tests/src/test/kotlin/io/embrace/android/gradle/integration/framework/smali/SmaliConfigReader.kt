package io.embrace.android.gradle.integration.framework.smali

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.gradle.integration.framework.ApkDisassembler
import io.embrace.android.gradle.integration.framework.findArtifact
import io.embrace.android.gradle.plugin.util.serialization.JsonSerializer
import java.io.File

class SmaliConfigReader {

    fun readSmaliFiles(projectDir: File, classNames: List<String>, buildType: String = "release"): List<File> {
        val apk = findArtifact(projectDir, "build/outputs/apk/$buildType/", ".apk")
        return ApkDisassembler().disassembleClasses(apk, classNames)
    }

    fun readExpectedConfig(resName: String): ExpectedSmaliConfig {
        return JsonSerializer().fromJson(ResourceReader.readResource(resName), ExpectedSmaliConfig.serializer())
    }
}
