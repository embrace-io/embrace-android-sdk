package io.embrace.android.embracesdk.internal.delivery.storage

import android.content.Context
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.io.File

enum class StorageLocation(private val dir: String) {

    /**
     * A complete payload that is ready to send
     */
    PAYLOAD("embrace_payloads"),

    /**
     * An incomplete cached payload that is not ready to send
     */
    CACHE("embrace_cache"),

    /**
     * Native Embrace crash reports
     */
    NATIVE("embrace_native");

    /**
     * Get the directory as a [File] object
     */
    fun asFile(ctx: Context, logger: EmbLogger): Lazy<File> = lazy {
        try {
            File(ctx.filesDir, dir).apply(File::mkdirs)
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.PAYLOAD_STORAGE_FAIL, exc)
            File(ctx.cacheDir, dir).apply(File::mkdirs)
        }
    }
}
