package io.embrace.android.embracesdk.internal.logging

public interface InternalErrorHandler {
    public fun handleInternalError(throwable: Throwable)
}
