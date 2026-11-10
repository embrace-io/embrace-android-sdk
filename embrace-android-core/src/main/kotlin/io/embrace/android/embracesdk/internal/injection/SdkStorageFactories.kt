package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.storage.LegacySdkStorage
import io.embrace.android.embracesdk.internal.storage.LegacyStorageMigrator
import io.embrace.android.embracesdk.internal.storage.SdkStorage
import io.embrace.android.embracesdk.internal.storage.createSdkStorage
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

@Suppress("EmbraceContextDirAccess")
fun createSdkStorage(
    context: Context,
    errorHandler: ErrorHandler,
    fileSystem: FileSystem = FileSystem.SYSTEM,
): SdkStorage = createSdkStorage(fileSystem, context.noBackupFilesDir.toOkioPath(), errorHandler)

@Suppress("EmbraceContextDirAccess")
fun createLegacyStorageMigrator(
    context: Context,
    errorHandler: ErrorHandler,
    fileSystem: FileSystem = FileSystem.SYSTEM,
): LegacyStorageMigrator = LegacyStorageMigrator(
    fileSystem = fileSystem,
    filesDir = { context.filesDir.toOkioPath() },
    cacheDir = { context.cacheDir.toOkioPath() },
    noBackupFilesDir = { context.noBackupFilesDir.toOkioPath() },
    errorHandler = errorHandler,
)
