package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.storage.LegacySdkStorage
import io.embrace.android.embracesdk.internal.storage.SdkStorage
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * Creates the [SdkStorage] that resolves directories to the app's files dir.
 */
@Suppress("EmbraceContextDirAccess")
fun createLegacySdkStorage(context: Context, errorHandler: ErrorHandler): SdkStorage =
    LegacySdkStorage(
        fileSystem = FileSystem.SYSTEM,
        filesDir = context.filesDir.toOkioPath(),
        cacheDir = { context.cacheDir.toOkioPath() },
        errorHandler = errorHandler,
    )
