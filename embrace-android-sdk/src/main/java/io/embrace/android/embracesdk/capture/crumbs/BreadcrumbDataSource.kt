package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.logging.EmbLogger

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
        alterSessionSpan(
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
