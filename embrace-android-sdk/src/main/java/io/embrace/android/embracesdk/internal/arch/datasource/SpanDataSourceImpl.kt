package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.spans.SpanService

/**
 * Base class for data sources.
 */
internal abstract class SpanDataSourceImpl(
    destination: SpanService,
    logger: EmbLogger,
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
