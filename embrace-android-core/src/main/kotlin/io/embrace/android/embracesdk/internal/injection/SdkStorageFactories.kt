package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.storage.LegacySdkStorage
import io.embrace.android.embracesdk.internal.storage.SdkStorage
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Creates the [SdkStorage] that resolves directories to the app's files dir.
 */
fun createLegacySdkStorage(filesDir: File, cacheDir: () -> File, errorHandler: ErrorHandler): SdkStorage =
    LegacySdkStorage(
        fileSystem = FileSystem.SYSTEM,
        filesDir = filesDir.toOkioPath(),
        cacheDir = { cacheDir().toOkioPath() },
        errorHandler = errorHandler,
    )
