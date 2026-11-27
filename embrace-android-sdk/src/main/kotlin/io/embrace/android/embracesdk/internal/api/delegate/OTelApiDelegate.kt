package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.opentelemetry.kotlin.OpenTelemetry

internal class OTelApiDelegate(
    private val otelSdkWrapper: Lazy<OtelSdkWrapper>,
) : OTelApi {

    override fun getOpenTelemetryKotlin(): OpenTelemetry =
        otelSdkWrapper.value.openTelemetryKotlin
}
