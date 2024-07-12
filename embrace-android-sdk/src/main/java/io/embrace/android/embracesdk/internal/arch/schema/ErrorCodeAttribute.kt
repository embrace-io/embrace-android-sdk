package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.spans.ErrorCode
import java.util.Locale

/**
 * Attribute that stores the [ErrorCode] in an OpenTelemetry span
 */
internal sealed class ErrorCodeAttribute(
    errorCode: ErrorCode
) : FixedAttribute {
    override val key = EmbraceAttributeKey(id = "error_code")
    override val value: String = errorCode.name.toLowerCase(Locale.ENGLISH)

    internal object Failure : ErrorCodeAttribute(ErrorCode.FAILURE)

    internal object UserAbandon : ErrorCodeAttribute(ErrorCode.USER_ABANDON)

    internal object Unknown : ErrorCodeAttribute(ErrorCode.UNKNOWN)
}
