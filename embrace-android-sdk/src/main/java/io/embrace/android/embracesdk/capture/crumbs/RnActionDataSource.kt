package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.datasource.SpanDataSourceImpl
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.logging.EmbLogger

internal open class RnActionDataSource(
    breadcrumbBehavior: BreadcrumbBehavior,
    spanService: SpanService,
    logger: EmbLogger
) : SpanDataSourceImpl(
    spanService,
    logger,
    UpToLimitStrategy(logger) { breadcrumbBehavior.getCustomBreadcrumbLimit() }
) {
    open fun logRnAction(
        name: String?,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String?
    ): Boolean = captureSpanData(
        countsTowardsLimits = true,
        inputValidation = { !name.isNullOrEmpty() },
        captureAction = {
            val schemaType = SchemaType.ReactNativeAction(
                checkNotNull(name),
                checkNotNull(output),
                bytesSent,
                properties
            )
            recordCompletedSpan(
                name = schemaType.fixedObjectName,
                startTimeMs = startTime,
                endTimeMs = endTime,
                type = schemaType.telemetryType,
                attributes = schemaType.attributes()
            )
        }
    )
}
