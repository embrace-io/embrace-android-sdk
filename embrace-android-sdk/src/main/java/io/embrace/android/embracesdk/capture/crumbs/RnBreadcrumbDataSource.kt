package io.embrace.android.embracesdk.capture.crumbs

import android.text.TextUtils
import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.RnActionBreadcrumb

/**
 * Captures RN action breadcrumbs.
 */
internal class RnBreadcrumbDataSource(
    private val configService: ConfigService,
    private val logger: EmbLogger,
    private val store: BreadcrumbDataStore<RnActionBreadcrumb> = BreadcrumbDataStore {
        configService.breadcrumbBehavior.getCustomBreadcrumbLimit()
    },
) : DataCaptureService<List<RnActionBreadcrumb>> by store {

    fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String
    ) {
        if (!RnActionBreadcrumb.validateRnBreadcrumbOutputName(output)) {
            logger.logWarning(
                "RN Action output is invalid, the valid values are ${RnActionBreadcrumb.getValidRnBreadcrumbOutputName()}"
            )
            return
        }
        if (TextUtils.isEmpty(name)) {
            logger.logWarning("RN Action name must not be blank")
            return
        }
        try {
            store.tryAddBreadcrumb(
                RnActionBreadcrumb(name, startTime, endTime, properties, bytesSent, output)
            )
        } catch (ex: Exception) {
            logger.logWarning("Failed to log RN Action breadcrumb", ex)
        }
    }
}
