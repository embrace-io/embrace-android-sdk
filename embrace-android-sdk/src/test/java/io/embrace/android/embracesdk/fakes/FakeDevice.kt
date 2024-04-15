package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.envelope.resource.Device
import io.embrace.android.embracesdk.internal.SystemInfo

internal class FakeDevice(
    override var isJailbroken: Boolean? = false,
    override var screenResolution: String = "1920x1080",
    override val numberOfCores: Int = 8,
    override val internalStorageTotalCapacity: Lazy<Long> = lazy { 10000000L },
    override val cpuName: String? = "fake_cpu",
    override val eglInfo: String? = "fake_elg",
    override val systemInfo: SystemInfo = SystemInfo().copy(
        deviceManufacturer = "Samsung",
        deviceModel = "Galaxy S10",
        osName = "android",
        osVersion = "8.0.0",
        androidOsApiLevel = "26"
    ),
) : Device
