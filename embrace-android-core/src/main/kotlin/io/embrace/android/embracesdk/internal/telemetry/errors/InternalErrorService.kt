package io.embrace.android.embracesdk.internal.telemetry.errors

import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Reports an internal error to Embrace. An internal error is defined as an exception that was
 * caught within Embrace code & logged to [EmbLogger].
 */
public interface InternalErrorService : InternalErrorHandler {
    public var handler: Provider<InternalErrorHandler?>
}
