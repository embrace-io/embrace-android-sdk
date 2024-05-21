package io.embrace.android.embracesdk.capture.memory

import io.embrace.android.embracesdk.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.destination.SpanEventData
import io.embrace.android.embracesdk.arch.destination.SpanEventMapper
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.MemoryWarning

/**
 * Captures custom breadcrumbs.
 */
internal class MemoryWarningDataSource(
    sessionSpanWriter: SessionSpanWriter,
    logger: EmbLogger
) : DataSourceImpl<SessionSpanWriter>(
    destination = sessionSpanWriter,
    logger = logger,
    limitStrategy = UpToLimitStrategy { EmbraceMemoryService.MAX_CAPTURED_MEMORY_WARNINGS }
),
    SpanEventMapper<MemoryWarning> {

    fun onMemoryWarning(timestamp: Long) {
        alterSessionSpan(
            inputValidation = { true },
            captureAction = {
                val memoryWarning = MemoryWarning(timestamp)
                addEvent(memoryWarning, ::toSpanEventData)
            }
        )
    }

    override fun toSpanEventData(obj: MemoryWarning): SpanEventData {
        return SpanEventData(
            SchemaType.MemoryWarning(),
            obj.timestamp.millisToNanos()
        )
    }
}
