package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehavior

class FakeSdkModeBehavior(
    var sdkDisabled: Boolean,
) : SdkModeBehavior {
    override fun isSdkDisabled() = sdkDisabled
}
