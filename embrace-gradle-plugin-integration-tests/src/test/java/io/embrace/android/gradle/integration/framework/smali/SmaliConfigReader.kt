package io.embrace.android.gradle.integration.framework.smali

import com.squareup.moshi.Moshi
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.gradle.integration.framework.ApkDisassembler
import io.embrace.android.gradle.integration.framework.findArtifact
import okio.buffer
import okio.source
import java.io.File

class SmaliConfigReader {

    fun readSmaliFiles(projectDir: File, classNames: List<String>): List<File> {
        val apk = findArtifact(projectDir, "build/outputs/apk/release/", ".apk")
        val decodedApk = ApkDisassembler().disassembleApk(apk)
        val smaliFiles = decodedApk.getSmaliFiles(classNames)
        return smaliFiles
    }

    fun readExpectedConfig(resName: String): ExpectedSmaliConfig {
        val adapter = Moshi.Builder().build().adapter(ExpectedSmaliConfig::class.java)
        return ResourceReader.readResource(resName).source().buffer().use {
            checkNotNull(adapter.fromJson(it))
        }
    }
}
