package io.embrace.android.embracesdk.internal.capture.crumbs

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.logging.EmbLogger

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
) {

    fun logCustom(message: String, timestamp: Long) {
        captureData(
            inputValidation = {
                message.isNotEmpty()
            },
            captureAction = {
                val sanitizedMessage = ellipsizeBreadcrumbMessage(message)
                addEvent(SchemaType.Breadcrumb(sanitizedMessage ?: ""), timestamp)
            }
        )
    }

    private fun ellipsizeBreadcrumbMessage(input: String?): String? {
        return if (input == null || input.length < BREADCRUMB_MESSAGE_MAX_LENGTH) {
            input
        } else {
            input.substring(
                0,
                BREADCRUMB_MESSAGE_MAX_LENGTH - 3
            ) + "..."
        }
    }

    companion object {
        private const val BREADCRUMB_MESSAGE_MAX_LENGTH = 256
    }
}
