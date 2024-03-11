package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.envelope.resource.Device

public class FakeDevice(
    override var isJailbroken: Boolean? = false,
    override var screenResolution: String = "1920x1080",
    override val manufacturer: String? = "Samsung",
    override val model: String? = "Galaxy S10",
    override val operatingSystemType: String = "Android",
    override val operatingSystemVersion: String? = "8.0.0",
    override val operatingSystemVersionCode: Int = 26,
    override val numberOfCores: Int = 8,
    override val internalStorageTotalCapacity: Lazy<Long> = lazy { 10000000L },
    override val cpuName: String? = "fake_cpu",
    override val eglInfo: String? = "fake_elg"
) : Device