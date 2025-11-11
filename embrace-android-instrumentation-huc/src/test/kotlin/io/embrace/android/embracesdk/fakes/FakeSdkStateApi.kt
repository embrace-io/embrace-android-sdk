package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.internal.api.SdkStateApi

class FakeSdkStateApi(
    override val isStarted: Boolean = true,
) : SdkStateApi {
    override val deviceId: String = "fake-id"
    override val currentSessionId: String? = null
    override val lastRunEndState: LastRunEndState = LastRunEndState.INVALID
}
