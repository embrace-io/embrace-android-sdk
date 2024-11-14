package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class FakeSdkModeBehavior(
    var sdkDisabled: Boolean,
) : SdkModeBehavior {

    override val local: Unit
        get() = throw UnsupportedOperationException()
    override val remote: RemoteConfig
        get() = throw UnsupportedOperationException()

    override fun isSdkDisabled() = sdkDisabled
}
