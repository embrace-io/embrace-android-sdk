package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.payload.NetworkCapturedCall

interface NetworkCaptureDataSource : DataSource {

    fun logNetworkCapturedCall(networkCapturedCall: NetworkCapturedCall)
}
