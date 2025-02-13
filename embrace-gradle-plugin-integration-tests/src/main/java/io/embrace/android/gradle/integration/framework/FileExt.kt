package io.embrace.android.gradle.integration.framework

import java.io.File

/**
 * Syntactic sugar that constructs a File from the project's build directory.
 */
fun File.buildFile(path: String) = File(this, "build/$path")

/**
 * Syntactic sugar that constructs a File from the project's root directory.
 */
fun File.file(path: String) = File(this, path)
