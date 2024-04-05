package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.TapBreadcrumb

/**
 * Captures tap breadcrumbs.
 */
internal class LegacyTapBreadcrumbDataSource(
    private val configService: ConfigService,
    private val logger: InternalEmbraceLogger,
    private val store: BreadcrumbDataStore<TapBreadcrumb> = BreadcrumbDataStore {
        configService.breadcrumbBehavior.getTapBreadcrumbLimit()
    },
) : DataCaptureService<List<TapBreadcrumb>> by store {

    fun logTap(
        point: Pair<Float?, Float?>,
        element: String,
        timestamp: Long,
        type: TapBreadcrumb.TapBreadcrumbType
    ) {
        try {
            val finalPoint =
                if (!configService.breadcrumbBehavior.isTapCoordinateCaptureEnabled()) {
                    Pair(0.0f, 0.0f)
                } else {
                    point
                }
            store.tryAddBreadcrumb(TapBreadcrumb(finalPoint, element, timestamp, type))
        } catch (ex: Exception) {
            logger.logError("Failed to log tap breadcrumb for element $element", ex)
        }
    }
}
