package io.embrace.android.embracesdk.compose.internal

import io.embrace.android.embracesdk.Embrace

internal class ComposeInternalErrorLogger {

    fun logError(throwable: Throwable) {
        Embrace.getInstance().logInternalError(
            throwable
        )
    }
}
