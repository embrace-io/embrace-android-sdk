package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.instrumentation.network.HttpNetworkRequest
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkCaptureDataSource

class FakeNetworkCaptureDataSource : NetworkCaptureDataSource {

    val requests: MutableList<HttpNetworkRequest> = mutableListOf()

    override fun recordNetworkRequest(request: HttpNetworkRequest) {
        requests.add(request)
    }

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean = true

    override fun onDataCaptureEnabled() {
        TODO("Not yet implemented")
    }

    override fun onDataCaptureDisabled() {
        TODO("Not yet implemented")
    }

    override fun resetDataCaptureLimits() {
        TODO("Not yet implemented")
    }

    override fun <T> captureTelemetry(
        inputValidation: () -> Boolean,
        invalidInputCallback: () -> Unit,
        action: TelemetryDestination.() -> T?,
    ): T? = null
}
