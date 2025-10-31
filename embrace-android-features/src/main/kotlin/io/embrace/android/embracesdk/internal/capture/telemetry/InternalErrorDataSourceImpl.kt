package io.embrace.android.embracesdk.internal.capture.telemetry

import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.logging.InternalErrorType

/**
 * Tracks internal errors & sends them as OTel logs.
 */
internal class InternalErrorDataSourceImpl(
    args: InstrumentationInstallArgs,
) : InternalErrorDataSource,
    DataSourceImpl(
        args,
        limitStrategy = UpToLimitStrategy { 10 },
    ) {

    override fun trackInternalError(type: InternalErrorType, throwable: Throwable) {
        captureTelemetry {
            val schemaType = SchemaType.InternalError(throwable)
            addLog(schemaType, LogSeverity.ERROR, "", true)
        }
    }
}
