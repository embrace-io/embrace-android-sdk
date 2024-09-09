package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.payload.NetworkCapturedCall

class FakeNetworkCaptureDataSource : NetworkCaptureDataSource {

    val loggedCalls: MutableList<NetworkCapturedCall> = mutableListOf()

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
