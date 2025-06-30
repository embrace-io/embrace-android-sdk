package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.sdk.common.CompletableResultCode

fun CompletableResultCode.toOperationResultCode(): OperationResultCode = when (this) {
    CompletableResultCode.ofSuccess() -> OperationResultCode.Success
    else -> OperationResultCode.Failure
}
