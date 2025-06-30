package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanData
import io.embrace.opentelemetry.kotlin.logging.model.ReadableLogRecord
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableSpan

@ExperimentalApi
fun ReadableLogRecord.toLogRecordData(): OtelJavaLogRecordData {
    TODO("Not yet implemented")
}

@ExperimentalApi
fun ReadableSpan.toSpanData(): OtelJavaSpanData {
    TODO("Not yet implemented")
}
