package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.semconv.EmbSpanAttributes

/**
 * Attribute that stores the errorCode in an OpenTelemetry span
 */
sealed class ErrorCodeAttribute(
    override val value: String
) : EmbraceAttribute {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey(EmbSpanAttributes.EMB_ERROR_CODE)

    object Failure : ErrorCodeAttribute("failure")

    object UserAbandon : ErrorCodeAttribute("user_abandon")

    object Unknown : ErrorCodeAttribute("unknown")
}
