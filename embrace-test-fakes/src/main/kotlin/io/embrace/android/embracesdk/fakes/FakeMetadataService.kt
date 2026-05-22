package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.metadata.ClockDrift
import io.embrace.android.embracesdk.internal.capture.metadata.DiskUsage
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.clock.Clock

/**
 * Fake implementation of [MetadataService] that represents an Android device. A [UnsupportedOperationException] will be thrown
 * if you attempt set info about Flutter/Unity/ReactNative on this fake, which is decided for an Android device.
 */
class FakeMetadataService(
    sessionId: String? = null,
    private val wallClock: Clock = FakeClock(),
    private val networkClock: Clock? = null,
    private val gnssClock: Clock? = null,
) : MetadataService {
    private companion object {
        private val diskUsage = DiskUsage(
            appDiskUsage = 10000000L,
            deviceDiskFree = 500000000L
        )
    }

    private var appSessionId: String? = null

    init {
        appSessionId = sessionId
    }

    override fun getDiskUsage(): DiskUsage = diskUsage

    override fun getClockDrift(): ClockDrift? {
        if (networkClock == null && gnssClock == null) {
            return null
        }

        return ClockDrift(
            wallTimeMillis = wallClock.now(),
            networkTimeMillis = networkClock?.now(),
            gnssTimeMillis = gnssClock?.now(),
        )
    }

    override fun precomputeValues() {}
}
