package io.embrace.android.embracesdk.internal.capture.telemetry

import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.destination.LogSeverity
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType

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

    override fun trackInternalError(type: InternalErrorType, throwable: Throwable) {
        captureData(NoInputValidation) {
            val schemaType = SchemaType.InternalError(throwable)
            addLog(schemaType, LogSeverity.ERROR, "", true)
        }
    }
}
