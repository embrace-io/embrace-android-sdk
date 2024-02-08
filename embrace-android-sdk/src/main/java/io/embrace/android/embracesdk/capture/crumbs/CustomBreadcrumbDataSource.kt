package io.embrace.android.embracesdk.capture.crumbs

import android.text.TextUtils
import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.CustomBreadcrumb
import java.util.concurrent.LinkedBlockingDeque

/**
 * Captures custom breadcrumbs.
 */
internal class CustomBreadcrumbDataSource(
    private val configService: ConfigService,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : DataCaptureService<List<CustomBreadcrumb>> {

    private val customBreadcrumbs = LinkedBlockingDeque<CustomBreadcrumb>()

    fun logCustom(message: String, timestamp: Long) {
        if (TextUtils.isEmpty(message)) {
            logger.logWarning("Breadcrumb message must not be blank")
            return
        }
        try {
            val limit = configService.breadcrumbBehavior.getCustomBreadcrumbLimit()
            tryAddBreadcrumb(customBreadcrumbs, CustomBreadcrumb(message, timestamp), limit)
        } catch (ex: Exception) {
            logger.logError("Failed to log custom breadcrumb with message $message", ex)
        }
    }

    private fun <T> tryAddBreadcrumb(
        breadcrumbs: LinkedBlockingDeque<T>,
        breadcrumb: T,
        limit: Int
    ) {
        if (!breadcrumbs.isEmpty() && breadcrumbs.size >= limit) {
            breadcrumbs.removeLast()
        }
        breadcrumbs.push(breadcrumb)
    }

    override fun getCapturedData(): List<CustomBreadcrumb> = customBreadcrumbs.toList()
    override fun cleanCollections() = customBreadcrumbs.clear()
}
