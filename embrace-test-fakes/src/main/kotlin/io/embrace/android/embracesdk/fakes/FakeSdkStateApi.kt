package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.internal.api.SdkStateApi

class FakeSdkStateApi(
    override val isStarted: Boolean = true
) : SdkStateApi {
    override val deviceId: String
        get() = TODO("Not yet implemented")
    override val currentSessionId: String?
        get() = TODO("Not yet implemented")
    override val lastRunEndState: LastRunEndState
        get() = TODO("Not yet implemented")
}
