package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.spans.ErrorCode

internal fun ErrorCode.fromErrorCode(): ErrorCodeAttribute = when (this) {
    ErrorCode.FAILURE -> ErrorCodeAttribute.Failure
    ErrorCode.USER_ABANDON -> ErrorCodeAttribute.UserAbandon
    ErrorCode.UNKNOWN -> ErrorCodeAttribute.Unknown
}
