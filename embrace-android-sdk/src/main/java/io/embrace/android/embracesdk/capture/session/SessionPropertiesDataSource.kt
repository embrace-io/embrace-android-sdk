package io.embrace.android.embracesdk.capture.session

import io.embrace.android.embracesdk.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger

internal class SessionPropertiesDataSource(
    sessionBehavior: SessionBehavior,
    writer: SessionSpanWriter,
    logger: InternalEmbraceLogger
) : DataSourceImpl<SessionSpanWriter>(
    destination = writer,
    logger = logger,
    limitStrategy = UpToLimitStrategy(logger) { sessionBehavior.getMaxSessionProperties() }
) {
    /**
     * Assume input has already been sanitized
     */
    fun addProperty(key: String, value: String): Boolean =
        alterSessionSpan(
            inputValidation = { true },
            captureAction = {
                addCustomAttribute(SpanAttributeData(key, value))
            }
        )

    fun removeProperty(key: String): Boolean =
        alterSessionSpan(
            inputValidation = { true },
            captureAction = {
                removeCustomAttribute(key)
            }
        )
}
