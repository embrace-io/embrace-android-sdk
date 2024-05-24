package io.embrace.android.embracesdk.capture.internal.errors

import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Reports an internal error to Embrace. An internal error is defined as an exception that was
 * caught within Embrace code & logged to [EmbLogger].
 */
internal interface InternalErrorService : InternalErrorHandler {
    var internalErrorDataSource: Provider<InternalErrorDataSource?>
}
