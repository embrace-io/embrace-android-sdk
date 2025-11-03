package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.payload.NetworkCapturedCall

class FakeNetworkCaptureDataSource : NetworkCaptureDataSource {

    val loggedCalls: MutableList<NetworkCapturedCall> = mutableListOf()

    override fun logNetworkCapturedCall(networkCapturedCall: NetworkCapturedCall) {
        loggedCalls.add(networkCapturedCall)
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

    override fun handleCrash(crashId: String) {
        throw UnsupportedOperationException()
    }

    override fun captureTelemetry(
        inputValidation: () -> Boolean,
        action: TelemetryDestination.() -> Unit,
    ) {
        TODO("Not yet implemented")
    }
}
