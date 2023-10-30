package io.embrace.android.embracesdk.okhttp3

import io.embrace.android.embracesdk.InternalApi
import java.io.IOException

/**
 * We use the EmbraceCustomPathException to capture the custom path added in the
 * intercept chain process for client errors.
 */
@InternalApi
public class EmbraceCustomPathException(public val customPath: String, cause: Throwable?) : IOException(cause)
