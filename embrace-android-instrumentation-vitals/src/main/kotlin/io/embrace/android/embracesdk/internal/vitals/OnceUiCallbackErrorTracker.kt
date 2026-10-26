package io.embrace.android.embracesdk.internal.vitals

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks only the first failure as [SdkErrorType.UiCallbackFail].
 */
internal class OnceUiCallbackErrorTracker(private val errorHandler: ErrorHandler) {

    private val tracked = AtomicBoolean(false)

    fun track(exc: Throwable) {
        if (tracked.compareAndSet(false, true)) {
            errorHandler.trackSdkError(SdkErrorType.UiCallbackFail, exc)
        }
    }
}
