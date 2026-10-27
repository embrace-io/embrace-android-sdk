package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.concurrent.OneShot
import java.util.concurrent.atomic.AtomicBoolean

internal class SharedObjectLoaderImpl(
    private val errorHandler: ErrorHandler,
) : SharedObjectLoader {
    override val loaded = AtomicBoolean(false)
    private val reportLoadFailureOnce = OneShot()

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
                    EmbTrace.trace("load-embrace-native-lib") {
                        System.loadLibrary("embrace-native")
                    }
                    loaded.set(true)
                } catch (exc: UnsatisfiedLinkError) {
                    reportLoadFailureOnce {
                        errorHandler.trackSdkError(
                            type = SdkErrorType.NativeReadFail,
                            throwable = exc,
                        )
                    }
                    return false
                }
            }

            return true
        }
    }
}
