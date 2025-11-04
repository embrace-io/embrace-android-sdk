package io.embrace.android.embracesdk.internal

import android.os.Build
import android.text.TextUtils

class DeviceArchitecture(
    val architecture: String = Build.SUPPORTED_ABIS[0],
    val is32BitDevice: Boolean = !TextUtils.join(
        ", ",
        Build.SUPPORTED_ABIS
    ).contains("64"),
)
