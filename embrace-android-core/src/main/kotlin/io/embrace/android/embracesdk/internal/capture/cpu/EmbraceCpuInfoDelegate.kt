package io.embrace.android.embracesdk.internal.capture.cpu

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType

/**
 * This class is responsible for getting the CPU name and EGL name from the native library.
 * Make sure to update the JNI call in cpuinfo.c with any method or package name changes.
 */
internal class EmbraceCpuInfoDelegate(
    private val sharedObjectLoader: SharedObjectLoader,
    private val logger: EmbLogger,
    private val cpuInfoNdkDelegate: CpuInfoNdkDelegate = EmbraceCpuInfoNdkDelegate(),
) : CpuInfoDelegate {

    override fun getCpuName(): String? {
        return if (sharedObjectLoader.loadEmbraceNative()) {
            try {
                cpuInfoNdkDelegate.getNativeCpuName()
            } catch (exception: LinkageError) {
                logger.trackInternalError(InternalErrorType.NATIVE_READ_FAIL, exception)
                null
            }
        } else {
            null
        }
    }

    override fun getEgl(): String? {
        return if (sharedObjectLoader.loadEmbraceNative()) {
            try {
                cpuInfoNdkDelegate.getNativeEgl()
            } catch (exception: LinkageError) {
                logger.trackInternalError(InternalErrorType.NATIVE_READ_FAIL, exception)
                null
            }
        } else {
            null
        }
    }
}
