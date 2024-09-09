package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.ErrorCode.FAILURE
import io.embrace.android.embracesdk.spans.ErrorCode.UNKNOWN
import io.embrace.android.embracesdk.spans.ErrorCode.USER_ABANDON
import java.util.Locale

/**
 * Attribute that stores the [ErrorCode] in an OpenTelemetry span
 */
sealed class ErrorCodeAttribute(
    errorCode: ErrorCode
) : FixedAttribute {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey(id = "error_code")
    override val value: String = errorCode.name.lowercase(Locale.ENGLISH)

    object Failure : ErrorCodeAttribute(FAILURE)

    object UserAbandon : ErrorCodeAttribute(USER_ABANDON)

    object Unknown : ErrorCodeAttribute(UNKNOWN)

    fun ErrorCode.fromErrorCode(): ErrorCodeAttribute = when (this) {
        FAILURE -> Failure
        USER_ABANDON -> UserAbandon
        UNKNOWN -> Unknown
    }
}
