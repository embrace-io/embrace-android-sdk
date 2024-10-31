package io.embrace.android.embracesdk.internal.capture.crumbs

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.TapBreadcrumb

/**
 * Captures custom breadcrumbs.
 */
class TapDataSource(
    private val breadcrumbBehavior: BreadcrumbBehavior,
    writer: SessionSpanWriter,
    logger: EmbLogger,
) : DataSourceImpl<SessionSpanWriter>(
    destination = writer,
    logger = logger,
    limitStrategy = UpToLimitStrategy(breadcrumbBehavior::getTapBreadcrumbLimit)
) {

    fun logTap(
        point: Pair<Float?, Float?>,
        element: String,
        timestamp: Long,
        type: TapBreadcrumb.TapBreadcrumbType,
    ) {
        captureData(
            inputValidation = NoInputValidation,
            captureAction = {
                val finalPoint = when {
                    breadcrumbBehavior.isViewClickCoordinateCaptureEnabled() -> point
                    else -> Pair(0.0f, 0.0f)
                }
                val coords = run {
                    val first = finalPoint.first?.toInt()?.toFloat() ?: 0.0f
                    val second = finalPoint.second?.toInt()?.toFloat() ?: 0.0f
                    first.toInt().toString() + "," + second.toInt()
                }
                addEvent(SchemaType.Tap(element, type.value, coords), timestamp)
            }
        )
    }
}
