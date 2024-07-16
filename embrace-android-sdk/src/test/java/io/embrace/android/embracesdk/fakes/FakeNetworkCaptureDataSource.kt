package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.network.logging.NetworkCaptureDataSource
import io.embrace.android.embracesdk.payload.NetworkCapturedCall

internal class FakeNetworkCaptureDataSource : NetworkCaptureDataSource {

    val loggedCalls = mutableListOf<NetworkCapturedCall>()

    override fun logNetworkCapturedCall(networkCapturedCall: NetworkCapturedCall) {
        loggedCalls.add(networkCapturedCall)
    }

    override fun captureData(
        inputValidation: () -> Boolean,
        captureAction: LogWriter.() -> Unit
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun enableDataCapture() {
        TODO("Not yet implemented")
    }

    override fun disableDataCapture() {
        TODO("Not yet implemented")
    }

    override fun resetDataCaptureLimits() {
        TODO("Not yet implemented")
    }
}
