package io.embrace.android.embracesdk.internal.capture.crumbs

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

/**
 * Captures breadcrumbs.
 */
class BreadcrumbDataSource(
    args: InstrumentationArgs,
) : DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy(args.configService.breadcrumbBehavior::getCustomBreadcrumbLimit)
) {

    fun logCustom(message: String, timestamp: Long) {
        captureTelemetry(inputValidation = message::isNotEmpty) {
            val sanitizedMessage = ellipsizeBreadcrumbMessage(message)
            addSessionEvent(SchemaType.Breadcrumb(sanitizedMessage ?: ""), timestamp)
        }
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
