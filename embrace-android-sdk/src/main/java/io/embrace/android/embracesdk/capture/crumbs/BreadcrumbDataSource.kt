package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.destination.SpanEventData
import io.embrace.android.embracesdk.arch.destination.SpanEventMapper
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.CustomBreadcrumb

/**
 * Captures breadcrumbs.
 */
internal class BreadcrumbDataSource(
    breadcrumbBehavior: BreadcrumbBehavior,
    writer: SessionSpanWriter,
    logger: EmbLogger
) : DataSourceImpl<SessionSpanWriter>(
    destination = writer,
    logger = logger,
    limitStrategy = UpToLimitStrategy(breadcrumbBehavior::getCustomBreadcrumbLimit)
),
    SpanEventMapper<CustomBreadcrumb> {

    fun logCustom(message: String, timestamp: Long) {
        alterSessionSpan(
            inputValidation = {
                message.isNotEmpty()
            },
            captureAction = {
                val crumb = CustomBreadcrumb(message, timestamp)
                addEvent(crumb, ::toSpanEventData)
            }
        )
    }

    override fun toSpanEventData(obj: CustomBreadcrumb): SpanEventData {
        return SpanEventData(
            SchemaType.Breadcrumb(obj.message ?: ""),
            obj.timestamp.millisToNanos()
        )
    }
}
