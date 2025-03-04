package io.embrace.android.gradle.integration.framework

import java.io.File

fun findArtifact(projectDir: File, buildDir: String, suffix: String): File {
    return File(projectDir, buildDir)
        .listFiles { _, name -> name.endsWith(suffix) }
        ?.single()
        ?: error("File not found")
}
