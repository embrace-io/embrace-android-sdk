package io.embrace.android.embracesdk.internal.limits

import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SpanLimitsTest {

    private val config = OtelLimitsConfigImpl

    @Test
    fun `internal limits are resolved from the system flavour of each limit`() {
        with(SpanLimits.Internal) {
            assertTrue(internal)
            assertEquals(config.getMaxInternalNameLength(), maxNameLength)
            assertEquals(config.getMaxSystemEventCount(), maxEventCount)
            assertEquals(config.getMaxSystemLinkCount(), maxLinkCount)
            assertEquals(config.getMaxSystemAttributeCount(), maxAttributeCount)
            assertEquals(config.getMaxInternalAttributeKeyLength(), maxAttributeKeyLength)
            assertEquals(config.getMaxInternalAttributeValueLength(), maxAttributeValueLength)
        }
    }

    @Test
    fun `custom limits are resolved from the custom flavour of each limit`() {
        with(SpanLimits.Custom) {
            assertFalse(internal)
            assertEquals(config.getMaxNameLength(), maxNameLength)
            assertEquals(config.getMaxCustomEventCount(), maxEventCount)
            assertEquals(config.getMaxCustomLinkCount(), maxLinkCount)
            assertEquals(config.getMaxCustomAttributeCount(), maxAttributeCount)
            assertEquals(config.getMaxCustomAttributeKeyLength(), maxAttributeKeyLength)
            assertEquals(config.getMaxCustomAttributeValueLength(), maxAttributeValueLength)
        }
    }

    @Test
    fun `internal telemetry is allowed more than custom telemetry`() {
        assertTrue(SpanLimits.Internal.maxNameLength > SpanLimits.Custom.maxNameLength)
        assertTrue(SpanLimits.Internal.maxEventCount > SpanLimits.Custom.maxEventCount)
        assertTrue(SpanLimits.Internal.maxLinkCount > SpanLimits.Custom.maxLinkCount)
        assertTrue(SpanLimits.Internal.maxAttributeCount > SpanLimits.Custom.maxAttributeCount)
        assertTrue(SpanLimits.Internal.maxAttributeKeyLength > SpanLimits.Custom.maxAttributeKeyLength)
        assertTrue(SpanLimits.Internal.maxAttributeValueLength > SpanLimits.Custom.maxAttributeValueLength)
    }

    @Test
    fun `of selects the flavour matching the internal flag`() {
        assertSame(SpanLimits.Internal, SpanLimits.of(internal = true))
        assertSame(SpanLimits.Custom, SpanLimits.of(internal = false))
    }
}
