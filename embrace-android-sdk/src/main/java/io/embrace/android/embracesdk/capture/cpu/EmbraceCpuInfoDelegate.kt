package io.embrace.android.embracesdk.capture.cpu

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger

internal class EmbraceCpuInfoDelegate(
    private val sharedObjectLoader: SharedObjectLoader,
    private val logger: InternalEmbraceLogger
) : CpuInfoDelegate {

    override fun getCpuName(): String? {
        return if (sharedObjectLoader.loadEmbraceNative()) {
            try {
                getNativeCpuName()
            } catch (exception: LinkageError) {
                logger.logError("Could not get the CPU name. Exception: $exception", exception)
                null
            }
        } else null
    }

    override fun getElg(): String? {
        return if (sharedObjectLoader.loadEmbraceNative()) {
            try {
                getNativeEgl()
            } catch (exception: LinkageError) {
                logger.logError("Could not get the EGL name. Exception: $exception", exception)
                null
            }
        } else null
    }

    private external fun getNativeCpuName(): String

    private external fun getNativeEgl(): String
}
