package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.util.concurrent.atomic.AtomicBoolean

class SharedObjectLoaderImpl(
    private val logger: EmbLogger,
) : SharedObjectLoader {
    override val loaded = AtomicBoolean(false)
    private val loggedFailure = AtomicBoolean(false)

    /**
     * Load Embrace native binary if necessary
     */
    override fun loadEmbraceNative(): Boolean {
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
                    if (!loggedFailure.getAndSet(true)) {
                        logger.trackInternalError(
                            type = InternalErrorType.NATIVE_READ_FAIL,
                            throwable = exc
                        )
                    }
                    return false
                }
            }

            return true
        }
    }
}
