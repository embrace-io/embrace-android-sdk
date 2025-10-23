package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.destination.TraceWriter
import io.embrace.android.embracesdk.internal.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.logging.EmbLogger

/**
 * Base class for data sources.
 */
abstract class SpanDataSourceImpl(
    destination: TraceWriter,
    logger: EmbLogger,
    limitStrategy: LimitStrategy,
) : SpanDataSource, DataSourceImpl<TraceWriter>(
    destination = destination,
    logger = logger,
    limitStrategy = limitStrategy,
) {

    override fun captureSpanData(
        countsTowardsLimits: Boolean,
        inputValidation: () -> Boolean,
        captureAction: TraceWriter.() -> Unit,
    ): Boolean = captureDataImpl(inputValidation, captureAction, countsTowardsLimits)
}
