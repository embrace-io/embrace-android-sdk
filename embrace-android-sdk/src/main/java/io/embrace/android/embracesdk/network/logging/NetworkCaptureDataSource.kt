package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.arch.datasource.LogDataSource
import io.embrace.android.embracesdk.payload.NetworkCapturedCall

internal interface NetworkCaptureDataSource : LogDataSource {

    fun logNetworkCapturedCall(networkCapturedCall: NetworkCapturedCall)
}
