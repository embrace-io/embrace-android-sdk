package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Span

fun Span.getSessionProperty(key: String): String? {
    return attributes?.findAttributeValue(key.toEmbraceAttributeName())
}
