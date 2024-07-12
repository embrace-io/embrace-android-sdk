package io.embrace.android.embracesdk.capture.internal.errors

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public interface InternalErrorHandler {
    public fun handleInternalError(throwable: Throwable)
}
