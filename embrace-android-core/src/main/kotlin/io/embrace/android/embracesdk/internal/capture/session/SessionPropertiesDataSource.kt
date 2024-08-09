package io.embrace.android.embracesdk.internal.capture.session

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.internal.logging.EmbLogger

public class SessionPropertiesDataSource(
    sessionBehavior: SessionBehavior,
    writer: SessionSpanWriter,
    logger: EmbLogger
) : DataSourceImpl<SessionSpanWriter>(
    destination = writer,
    logger = logger,
    limitStrategy = UpToLimitStrategy { sessionBehavior.getMaxSessionProperties() }
) {
    /**
     * Assume input has already been sanitized
     */
    public fun addProperty(key: String, value: String): Boolean =
        captureData(
            inputValidation = NoInputValidation,
            captureAction = {
                addAttribute(key, value)
            }
        )

    public fun addProperties(properties: Map<String, String>): Boolean {
        properties.entries.forEach { (key, value) -> addProperty(key, value) }
        return true
    }

    public fun removeProperty(key: String): Boolean =
        captureData(
            inputValidation = NoInputValidation,
            captureAction = {
                removeSystemAttribute(key.toSessionPropertyAttributeName())
            }
        )

    private fun SessionSpanWriter.addAttribute(key: String, value: String) =
        addSystemAttribute(SpanAttributeData(key.toSessionPropertyAttributeName(), value))
}
