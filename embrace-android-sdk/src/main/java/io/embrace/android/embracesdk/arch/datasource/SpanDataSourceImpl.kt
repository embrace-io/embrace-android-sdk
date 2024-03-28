package io.embrace.android.embracesdk.arch.datasource

import io.embrace.android.embracesdk.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger

/**
 * Base class for data sources.
 */
internal abstract class SpanDataSourceImpl(
    destination: SpanService,
    logger: InternalEmbraceLogger,
    limitStrategy: LimitStrategy
) : SpanDataSource, DataSourceImpl<SpanService>(
    destination = destination,
    logger = logger,
    limitStrategy = limitStrategy,
) {

    override fun captureSpanData(
        countsTowardsLimits: Boolean,
        inputValidation: () -> Boolean,
        captureAction: SpanService.() -> Unit
    ): Boolean = captureDataImpl(inputValidation, captureAction, countsTowardsLimits)
}
