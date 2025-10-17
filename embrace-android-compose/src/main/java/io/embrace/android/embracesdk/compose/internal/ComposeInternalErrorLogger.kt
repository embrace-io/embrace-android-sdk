package io.embrace.android.embracesdk.compose.internal

import io.embrace.android.embracesdk.internal.EmbraceInternalApi

internal class ComposeInternalErrorLogger {

    fun logError(throwable: Throwable) {
        EmbraceInternalApi.internalInterface.logInternalError(throwable)
    }
}
