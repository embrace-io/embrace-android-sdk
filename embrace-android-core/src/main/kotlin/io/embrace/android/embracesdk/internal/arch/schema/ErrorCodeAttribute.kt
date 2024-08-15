package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.ErrorCode.FAILURE
import io.embrace.android.embracesdk.spans.ErrorCode.UNKNOWN
import io.embrace.android.embracesdk.spans.ErrorCode.USER_ABANDON
import java.util.Locale

/**
 * Attribute that stores the [ErrorCode] in an OpenTelemetry span
 */
public sealed class ErrorCodeAttribute(
    errorCode: ErrorCode
) : FixedAttribute {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey(id = "error_code")
    override val value: String = errorCode.name.lowercase(Locale.ENGLISH)

    public object Failure : ErrorCodeAttribute(FAILURE)

    public object UserAbandon : ErrorCodeAttribute(USER_ABANDON)

    public object Unknown : ErrorCodeAttribute(UNKNOWN)

    public fun ErrorCode.fromErrorCode(): ErrorCodeAttribute = when (this) {
        FAILURE -> Failure
        USER_ABANDON -> UserAbandon
        UNKNOWN -> Unknown
    }
}
