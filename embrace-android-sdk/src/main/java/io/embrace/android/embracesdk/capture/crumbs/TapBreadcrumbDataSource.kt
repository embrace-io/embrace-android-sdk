package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.destination.SpanEventData
import io.embrace.android.embracesdk.arch.destination.SpanEventMapper
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.TapBreadcrumb

/**
 * Captures custom breadcrumbs.
 */
internal class TapBreadcrumbDataSource(
    private val breadcrumbBehavior: BreadcrumbBehavior,
    writer: SessionSpanWriter,
    private val logger: InternalEmbraceLogger
) : DataSourceImpl<SessionSpanWriter>(
    destination = writer,
    logger = logger,
    limitStrategy = UpToLimitStrategy(logger, breadcrumbBehavior::getTapBreadcrumbLimit)
),
    SpanEventMapper<TapBreadcrumb> {

    fun logTap(
        point: Pair<Float?, Float?>,
        element: String,
        timestamp: Long,
        type: TapBreadcrumb.TapBreadcrumbType
    ) {
        try {
            val finalPoint =
                if (breadcrumbBehavior.isTapCoordinateCaptureEnabled()) {
                    point
                } else {
                    Pair(0.0f, 0.0f)
                }
            alterSessionSpan(
                inputValidation = {
                    true
                },
                captureAction = {
                    val crumb = TapBreadcrumb(finalPoint, element, timestamp, type)
                    addEvent(crumb, ::toSpanEventData)
                }
            )
        } catch (ex: Exception) {
            logger.logError("Failed to log tap breadcrumb for element $element", ex)
        }
    }

    override fun toSpanEventData(obj: TapBreadcrumb): SpanEventData {
        return SpanEventData(
            SchemaType.TapBreadcrumb(
                obj.tappedElementName ?: "",
                (obj.type ?: TapBreadcrumb.TapBreadcrumbType.TAP).value,
                obj.location ?: ""
            ),
            obj.timestamp.millisToNanos()
        )
    }
}
