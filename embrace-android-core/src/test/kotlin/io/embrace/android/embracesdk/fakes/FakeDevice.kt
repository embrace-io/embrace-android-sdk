package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.resource.Device

class FakeDevice(
    override var isJailbroken: Boolean? = FakeDeviceInfoValues.JAILBROKEN,
    override var screenResolution: String = FakeDeviceInfoValues.SCREEN_RESOLUTION,
    override val numberOfCores: Int = FakeDeviceInfoValues.NUMBER_OF_CORES,
    override val internalStorageTotalCapacity: Lazy<Long> = lazy { FakeDeviceInfoValues.DISK_TOTAL_CAPACITY },
) : Device
