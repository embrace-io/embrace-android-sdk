package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.logs.LogExceptionType

internal class ExceptionData(
    val name: String?,
    val message: String?,
    val stacktrace: String?,
    val logExceptionType: LogExceptionType? = null
)
