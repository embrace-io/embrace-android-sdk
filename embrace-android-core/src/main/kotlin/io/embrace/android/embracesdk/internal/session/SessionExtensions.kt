package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.capture.session.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Span

fun Span.getSessionProperty(key: String): String? {
    return attributes?.findAttributeValue(key.toSessionPropertyAttributeName())
}

fun Map<String, String>.getSessionProperty(key: String): String? = this[key.toSessionPropertyAttributeName()]
