package io.embrace.android.gradle.plugin.util.compression

import java.io.File

interface FileCompressor {

    fun compress(inputFile: File, outputFile: File): File?
}
