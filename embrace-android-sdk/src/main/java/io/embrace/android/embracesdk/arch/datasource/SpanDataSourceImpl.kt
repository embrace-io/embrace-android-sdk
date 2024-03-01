package io.embrace.android.embracesdk.arch.datasource

import io.embrace.android.embracesdk.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

/**
 * Base class for data sources.
 */
internal abstract class SpanDataSourceImpl(
    destination: SpanService,
    limitStrategy: LimitStrategy,
    logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : SpanDataSource, DataSourceImpl<SpanService>(
    destination,
    limitStrategy,
    logger
) {

    override fun captureSpanData(
        countsTowardsLimits: Boolean,
        inputValidation: () -> Boolean,
        captureAction: SpanService.() -> Unit
    ): Boolean = captureDataImpl(inputValidation, captureAction, countsTowardsLimits)
}
