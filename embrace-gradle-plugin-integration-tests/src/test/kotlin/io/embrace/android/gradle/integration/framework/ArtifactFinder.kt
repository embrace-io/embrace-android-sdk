package io.embrace.android.gradle.integration.framework

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

fun findArtifact(fileSystem: FileSystem, projectDir: File, buildDir: String, suffix: String): Path {
    val dir = projectDir.toOkioPath() / buildDir
    return fileSystem.listOrNull(dir)
        ?.single { it.name.endsWith(suffix) }
        ?: error("File not found")
}
