package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.instrumentation.network.HttpNetworkRequest
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkRequestDataSource

class FakeNetworkRequestDataSource : NetworkRequestDataSource {

    var requests: MutableList<HttpNetworkRequest> = mutableListOf()

    override fun recordNetworkRequest(request: HttpNetworkRequest) {
        requests.add(request)
    }

    override fun onDataCaptureEnabled() {
    }

    override fun onDataCaptureDisabled() {
    }

    override fun resetDataCaptureLimits() {
    }

    override fun <T> captureTelemetry(
        inputValidation: () -> Boolean,
        action: TelemetryDestination.() -> T?,
    ): T? = null
}
