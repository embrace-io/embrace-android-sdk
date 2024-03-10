package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

/**
 * Loads shared object files.
 */
internal class SharedObjectLoader {

    fun loadEmbraceNative() = try {
        Systrace.traceSynchronous("load-embrace-native-lib") {
            System.loadLibrary("embrace-native")
        }
        true
    } catch (exc: UnsatisfiedLinkError) {
        InternalStaticEmbraceLogger.logError("Failed to load SO file embrace-native", exc)
        false
    }
}
