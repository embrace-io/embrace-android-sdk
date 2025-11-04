package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.DeviceArchitecture

fun fakeDeviceArchitecture(arch: String = "arm64-v8a"): DeviceArchitecture {
    return DeviceArchitecture(arch, true)
}
