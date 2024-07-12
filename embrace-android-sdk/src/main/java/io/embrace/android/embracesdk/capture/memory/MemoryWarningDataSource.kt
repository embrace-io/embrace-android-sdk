package io.embrace.android.embracesdk.capture.memory

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
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
        captureData(
            inputValidation = NoInputValidation,
            captureAction = {
                addEvent(SchemaType.MemoryWarning(), timestamp)
            }
        )
    }
}
