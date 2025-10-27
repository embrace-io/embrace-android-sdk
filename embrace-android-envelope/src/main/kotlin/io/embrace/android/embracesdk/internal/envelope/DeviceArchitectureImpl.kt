package io.embrace.android.embracesdk.internal.envelope

import android.os.Build
import android.text.TextUtils

open class DeviceArchitectureImpl : DeviceArchitecture {
    override val architecture: String
        get() = Build.SUPPORTED_ABIS[0]

    override val is32BitDevice: Boolean
        get() = !TextUtils.join(
            ", ",
            Build.SUPPORTED_ABIS
        ).contains("64")
}
