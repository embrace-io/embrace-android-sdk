package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Component to load native binaries
 */
internal class SharedObjectLoader(
    private val logger: InternalEmbraceLogger
) {
    val loaded = AtomicBoolean(false)

    /**
     * Load Embrace native binary if necessary
     */
    fun loadEmbraceNative(): Boolean {
        if (loaded.get()) {
            return true
        }
        synchronized(loaded) {
            if (!loaded.get()) {
                try {
                    Systrace.traceSynchronous("load-embrace-native-lib") {
                        System.loadLibrary("embrace-native")
                    }
                    loaded.set(true)
                } catch (exc: UnsatisfiedLinkError) {
                    logger.logError("Failed to load SO file embrace-native", exc)
                    return false
                }
            }

            return true
        }
    }
}
