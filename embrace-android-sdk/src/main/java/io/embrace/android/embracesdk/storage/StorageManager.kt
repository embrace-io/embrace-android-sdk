package io.embrace.android.embracesdk.storage

import java.io.File
import java.io.FilenameFilter

internal interface StorageManager {

    val cacheDirectory: Lazy<File>
    val filesDirectory: Lazy<File>

    fun getFile(name: String, fallback: Boolean): File

    fun listFiles(filter: FilenameFilter): List<File>
}
