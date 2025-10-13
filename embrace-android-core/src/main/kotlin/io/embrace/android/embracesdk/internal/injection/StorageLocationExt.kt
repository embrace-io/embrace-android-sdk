package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.io.File

/**
 * Get the directory as a [File] object
 */
fun StorageLocation.asFile(ctx: Context, logger: EmbLogger): Lazy<File> = lazy {
    try {
        File(ctx.filesDir, dir).apply(File::mkdirs)
    } catch (exc: Throwable) {
        logger.trackInternalError(InternalErrorType.PAYLOAD_STORAGE_FAIL, exc)
        File(ctx.cacheDir, dir).apply(File::mkdirs)
    }
}
