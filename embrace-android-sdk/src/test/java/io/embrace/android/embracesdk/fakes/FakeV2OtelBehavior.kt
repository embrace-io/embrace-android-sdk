package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.config.remote.OTelRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig

internal fun fakeV2OtelBehavior() = fakeOTelBehavior {
    RemoteConfig(oTelConfig = OTelRemoteConfig(isDevEnabled = true))
}
