package io.embrace.android.embracesdk.spans

import io.embrace.android.embracesdk.arch.schema.ErrorCodeAttribute

internal fun ErrorCode.fromErrorCode(): ErrorCodeAttribute = when (this) {
    ErrorCode.FAILURE -> ErrorCodeAttribute.Failure
    ErrorCode.USER_ABANDON -> ErrorCodeAttribute.UserAbandon
    ErrorCode.UNKNOWN -> ErrorCodeAttribute.Unknown
}
