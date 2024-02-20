package io.embrace.android.embracesdk.arch

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

    override fun startSpan(
        inputValidation: () -> Boolean,
        captureAction: SpanService.() -> Unit
    ): Boolean = captureDataImpl(inputValidation, captureAction)

    override fun stopSpan(
        inputValidation: () -> Boolean,
        captureAction: SpanService.() -> Unit
    ): Boolean = captureDataImpl(inputValidation, captureAction, false)
}
