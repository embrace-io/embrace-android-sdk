package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.OtelLimitsRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Test

internal class RemoteOtelLimitsConfigTest {

    private val local = OtelLimitsConfigImpl

    @Test
    fun `local limits are used when no remote config is present`() {
        with(RemoteOtelLimitsConfig(local, null)) {
            assertEquals(2000, getMaxInternalNameLength())
            assertEquals(128, getMaxNameLength())
            assertEquals(10, getMaxCustomEventCount())
            assertEquals(11000, getMaxSystemEventCount())
            assertEquals(100, getMaxCustomAttributeCount())
            assertEquals(300, getMaxSystemAttributeCount())
            assertEquals(10, getMaxEventAttributeCount())
            assertEquals(10, getMaxCustomLinkCount())
            assertEquals(100, getMaxSystemLinkCount())
            assertEquals(1000, getMaxInternalAttributeKeyLength())
            assertEquals(2000, getMaxInternalAttributeValueLength())
            assertEquals(128, getMaxCustomAttributeKeyLength())
            assertEquals(1024, getMaxCustomAttributeValueLength())
            assertEquals("exception", getExceptionEventName())
        }
    }

    @Test
    fun `local limits are used when every remote value is unset`() {
        with(RemoteOtelLimitsConfig(local, OtelLimitsRemoteConfig())) {
            assertEquals(local.getMaxInternalNameLength(), getMaxInternalNameLength())
            assertEquals(local.getMaxNameLength(), getMaxNameLength())
            assertEquals(local.getMaxCustomEventCount(), getMaxCustomEventCount())
            assertEquals(local.getMaxSystemEventCount(), getMaxSystemEventCount())
            assertEquals(local.getMaxCustomAttributeCount(), getMaxCustomAttributeCount())
            assertEquals(local.getMaxSystemAttributeCount(), getMaxSystemAttributeCount())
            assertEquals(local.getMaxEventAttributeCount(), getMaxEventAttributeCount())
            assertEquals(local.getMaxCustomLinkCount(), getMaxCustomLinkCount())
            assertEquals(local.getMaxSystemLinkCount(), getMaxSystemLinkCount())
            assertEquals(local.getMaxInternalAttributeKeyLength(), getMaxInternalAttributeKeyLength())
            assertEquals(local.getMaxInternalAttributeValueLength(), getMaxInternalAttributeValueLength())
            assertEquals(local.getMaxCustomAttributeKeyLength(), getMaxCustomAttributeKeyLength())
            assertEquals(local.getMaxCustomAttributeValueLength(), getMaxCustomAttributeValueLength())
            assertEquals(local.getExceptionEventName(), getExceptionEventName())
        }
    }

    @Test
    fun `every remote value overrides the local limit`() {
        val remote = OtelLimitsRemoteConfig(
            maxInternalNameLength = 1,
            maxNameLength = 2,
            maxCustomEventCount = 3,
            maxSystemEventCount = 4,
            maxCustomAttributeCount = 5,
            maxSystemAttributeCount = 6,
            maxEventAttributeCount = 7,
            maxCustomLinkCount = 8,
            maxSystemLinkCount = 9,
            maxInternalAttributeKeyLength = 10,
            maxInternalAttributeValueLength = 11,
            maxCustomAttributeKeyLength = 12,
            maxCustomAttributeValueLength = 13,
            exceptionEventName = "boom",
        )

        with(RemoteOtelLimitsConfig(local, remote)) {
            assertEquals(1, getMaxInternalNameLength())
            assertEquals(2, getMaxNameLength())
            assertEquals(3, getMaxCustomEventCount())
            assertEquals(4, getMaxSystemEventCount())
            assertEquals(5, getMaxCustomAttributeCount())
            assertEquals(6, getMaxSystemAttributeCount())
            assertEquals(7, getMaxEventAttributeCount())
            assertEquals(8, getMaxCustomLinkCount())
            assertEquals(9, getMaxSystemLinkCount())
            assertEquals(10, getMaxInternalAttributeKeyLength())
            assertEquals(11, getMaxInternalAttributeValueLength())
            assertEquals(12, getMaxCustomAttributeKeyLength())
            assertEquals(13, getMaxCustomAttributeValueLength())
            assertEquals("boom", getExceptionEventName())
        }
    }

    @Test
    fun `remote limits that would disable capture are ignored`() {
        val remote = OtelLimitsRemoteConfig(
            maxNameLength = 0,
            maxCustomEventCount = -1,
            maxSystemAttributeCount = 0,
            maxCustomAttributeValueLength = -50,
            exceptionEventName = " ",
        )

        with(RemoteOtelLimitsConfig(local, remote)) {
            assertEquals(local.getMaxNameLength(), getMaxNameLength())
            assertEquals(local.getMaxCustomEventCount(), getMaxCustomEventCount())
            assertEquals(local.getMaxSystemAttributeCount(), getMaxSystemAttributeCount())
            assertEquals(local.getMaxCustomAttributeValueLength(), getMaxCustomAttributeValueLength())
            assertEquals(local.getExceptionEventName(), getExceptionEventName())
        }
    }
}
