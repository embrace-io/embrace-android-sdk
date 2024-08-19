package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSource
import io.embrace.android.embracesdk.internal.payload.NetworkCapturedCall

public interface NetworkCaptureDataSource : LogDataSource {

    public fun logNetworkCapturedCall(networkCapturedCall: NetworkCapturedCall)
}
