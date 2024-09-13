package io.embrace.android.embracesdk.okhttp3

import io.embrace.android.embracesdk.annotation.InternalApi
import java.io.IOException

/**
 * We use the EmbraceCustomPathException to capture the custom path added in the
 * intercept chain process for client errors.
 */
@InternalApi
class EmbraceCustomPathException(val customPath: String, cause: Throwable?) : IOException(cause)
