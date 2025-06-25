package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.opentelemetry.kotlin.aliases.OtelJavaCompletableResultCode

internal fun StoreDataResult.toCompleteableResultCode() = when (this) {
    StoreDataResult.SUCCESS -> OtelJavaCompletableResultCode.ofSuccess()
    StoreDataResult.FAILURE -> OtelJavaCompletableResultCode.ofFailure()
}
