package io.embrace.android.embracesdk.capture.internal.errors

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.datasource.LogDataSourceImpl
import io.embrace.android.embracesdk.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.arch.destination.LogEventData
import io.embrace.android.embracesdk.arch.destination.LogWriter
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.logging.EmbLogger

/**
 * Tracks internal errors & sends them as OTel logs.
 */
internal class InternalErrorDataSourceImpl(
    logWriter: LogWriter,
    logger: EmbLogger,
) : InternalErrorDataSource,
    LogDataSourceImpl(
        destination = logWriter,
        logger = logger,
        limitStrategy = UpToLimitStrategy { 10 },
    ) {

    override fun handleInternalError(throwable: Throwable) {
        alterSessionSpan(NoInputValidation) {
            this.addLog(throwable, true) {
                val schemaType = SchemaType.InternalError(throwable)
                LogEventData(schemaType, Severity.ERROR, "")
            }
        }
    }
}
