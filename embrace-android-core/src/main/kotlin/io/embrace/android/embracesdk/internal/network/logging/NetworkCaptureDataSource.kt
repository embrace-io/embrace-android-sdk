package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSource
import io.embrace.android.embracesdk.internal.payload.NetworkCapturedCall

interface NetworkCaptureDataSource : LogDataSource {

    fun logNetworkCapturedCall(networkCapturedCall: NetworkCapturedCall)
}
