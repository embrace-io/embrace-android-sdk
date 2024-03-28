package io.embrace.android.embracesdk.capture.crumbs

import android.text.TextUtils
import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.CustomBreadcrumb

/**
 * Captures custom breadcrumbs.
 */
internal class LegacyCustomBreadcrumbDataSource(
    private val configService: ConfigService,
    private val store: BreadcrumbDataStore<CustomBreadcrumb> = BreadcrumbDataStore {
        configService.breadcrumbBehavior.getCustomBreadcrumbLimit()
    },
    private val logger: InternalEmbraceLogger
) : DataCaptureService<List<CustomBreadcrumb>> by store {

    fun logCustom(message: String, timestamp: Long) {
        if (TextUtils.isEmpty(message)) {
            logger.logWarning("Breadcrumb message must not be blank")
            return
        }
        try {
            store.tryAddBreadcrumb(CustomBreadcrumb(message, timestamp))
        } catch (ex: Exception) {
            logger.logError("Failed to log custom breadcrumb with message $message", ex)
        }
    }
}
