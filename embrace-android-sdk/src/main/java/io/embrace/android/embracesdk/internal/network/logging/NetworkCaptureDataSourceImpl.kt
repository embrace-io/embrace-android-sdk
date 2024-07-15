package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSourceImpl
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.NetworkCapturedCall

internal class NetworkCaptureDataSourceImpl(
    private val logWriter: LogWriter,
    logger: EmbLogger
) : NetworkCaptureDataSource,
    LogDataSourceImpl(
        destination = logWriter,
        logger = logger,
        limitStrategy = NoopLimitStrategy,
    ) {

    /**
     * Creates a log with data from a captured network request.
     *
     * @param networkCapturedCall the captured network information
     */
    override fun logNetworkCapturedCall(networkCapturedCall: NetworkCapturedCall) {
        return logWriter.addLog(SchemaType.NetworkCapturedRequest(networkCapturedCall), Severity.INFO, networkCapturedCall.networkId)
    }
}
