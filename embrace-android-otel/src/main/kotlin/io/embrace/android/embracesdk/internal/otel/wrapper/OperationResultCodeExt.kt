package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.sdk.common.CompletableResultCode

fun OperationResultCode.toCompletableResultCode(): CompletableResultCode = when (this) {
    OperationResultCode.Failure -> CompletableResultCode.ofFailure()
    OperationResultCode.Success -> CompletableResultCode.ofSuccess()
}
