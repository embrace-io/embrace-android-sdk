package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.payload.NetworkCapturedCall

class FakeNetworkCaptureDataSource : NetworkCaptureDataSource {

    val loggedCalls: MutableList<NetworkCapturedCall> = mutableListOf()

    override fun logNetworkCapturedCall(call: NetworkCapturedCall) {
        loggedCalls.add(call)
    }

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
        action: TelemetryDestination.() -> T?,
    ): T? = null
}
