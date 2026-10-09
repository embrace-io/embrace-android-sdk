package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService

/**
 * Used when the SDK could not be created, so there is nowhere to send telemetry.
 */
internal object NoopInternalTelemetryService : InternalTelemetryService {
    override fun onPublicApiCalled(name: String) {}
    override fun logStorageTelemetry(storageTelemetry: Map<String, String>) {}
    override fun trackAppliedLimit(telemetryType: String, limitType: AppliedLimitType) {}
    override fun getAndClearTelemetryAttributes(): Map<String, String> = emptyMap()
}
