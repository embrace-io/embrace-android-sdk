package io.embrace.android.embracesdk.internal.vitals

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.utils.concurrent.OneShot

/**
 * Tracks only the first failure as [SdkErrorType.UiCallbackFail].
 */
internal class OnceUiCallbackErrorTracker(private val errorHandler: ErrorHandler) {

    private val trackOnce = OneShot()

    fun track(exc: Throwable) {
        trackOnce {
            errorHandler.trackSdkError(SdkErrorType.UiCallbackFail, exc)
        }
    }
}
