package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.model.ReadableLogRecord
import io.opentelemetry.sdk.logs.data.LogRecordData

@OptIn(ExperimentalApi::class)
internal fun LogRecordData.toReadableLogRecord(): ReadableLogRecord {
    TODO("not implemented yet")
}
