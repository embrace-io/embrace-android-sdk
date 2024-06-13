package io.embrace.android.embracesdk.capture.memory

import io.embrace.android.embracesdk.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.logging.EmbLogger

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
) {

    fun onMemoryWarning(timestamp: Long) {
        alterSessionSpan(
            inputValidation = NoInputValidation,
            captureAction = {
                addEvent(SchemaType.MemoryWarning(), timestamp)
            }
        )
    }
}
