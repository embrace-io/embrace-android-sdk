package io.embrace.android.embracesdk.okhttp3

import java.io.IOException

/**
 * We use the EmbraceCustomPathException to capture the custom path added in the
 * intercept chain process for client errors.
 */
class EmbraceCustomPathException(val customPath: String, cause: Throwable?) : IOException(cause)
