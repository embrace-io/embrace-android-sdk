package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.DeviceArchitecture

class FakeDeviceArchitecture(
    override var architecture: String = "arm64-v8a",
    override var is32BitDevice: Boolean = true
) : DeviceArchitecture
