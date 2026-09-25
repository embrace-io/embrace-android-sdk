package io.embrace.android.embracesdk.internal.limits

import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

internal class TelemetryLimitEnforcerTest {

    private val telemetryService = FakeTelemetryService()

    private fun enforcer(exemptFromValueTruncation: (String) -> Boolean = { false }) =
        TelemetryLimitEnforcer(
            telemetryService = telemetryService,
            exemptFromValueTruncation = exemptFromValueTruncation,
        )

    @Test
    fun `name within the limit is untouched and nothing is reported`() {
        assertEquals("span", enforcer().truncateName("span", maxLength = 10))
        assertEquals(emptyList<Pair<String, AppliedLimitType>>(), telemetryService.appliedLimits)
    }

    @Test
    fun `name over the limit is truncated and reported`() {
        assertEquals("abcde...", enforcer().truncateName("abcdefghij", maxLength = 8))
        assertEquals(listOf("span_name" to AppliedLimitType.TRUNCATE_STRING), telemetryService.appliedLimits)
    }

    @Test
    fun `attributes within the limits are untouched and nothing is reported`() {
        val attributes = mapOf("key" to "value")
        assertEquals(
            attributes,
            enforcer().truncateAttributes(attributes, maxCount = 5, maxKeyLength = 10, maxValueLength = 10),
        )
        assertEquals(emptyList<Pair<String, AppliedLimitType>>(), telemetryService.appliedLimits)
    }

    @Test
    fun `attributes over the count limit drop the trailing entries and are reported`() {
        val attributes = mapOf("a" to "1", "b" to "2", "c" to "3")
        assertEquals(
            mapOf("a" to "1", "b" to "2"),
            enforcer().truncateAttributes(attributes, maxCount = 2, maxKeyLength = 10, maxValueLength = 10),
        )
        assertEquals(listOf("span_attribute" to AppliedLimitType.TRUNCATE_ATTRIBUTES), telemetryService.appliedLimits)
    }

    @Test
    fun `over-long attribute keys and values are truncated and reported separately`() {
        assertEquals(
            mapOf("abcde..." to "vwxyz..."),
            enforcer().truncateAttributes(
                attributes = mapOf("abcdefghij" to "vwxyz12345"),
                maxCount = 5,
                maxKeyLength = 8,
                maxValueLength = 8,
            ),
        )
        assertEquals(
            listOf(
                "span_attribute_key" to AppliedLimitType.TRUNCATE_STRING,
                "span_attribute_value" to AppliedLimitType.TRUNCATE_STRING,
            ),
            telemetryService.appliedLimits,
        )
    }

    @Test
    fun `exempt attribute keys keep their full value`() {
        val enforcer = enforcer(exemptFromValueTruncation = { it == "stacktrace" })
        assertEquals(
            "stacktrace" to "vwxyz12345",
            enforcer.truncateAttribute("stacktrace", "vwxyz12345", maxKeyLength = 10, maxValueLength = 8),
        )
        assertEquals(
            "other" to "vwxyz...",
            enforcer.truncateAttribute("other", "vwxyz12345", maxKeyLength = 10, maxValueLength = 8),
        )
        assertEquals(listOf("span_attribute_value" to AppliedLimitType.TRUNCATE_STRING), telemetryService.appliedLimits)
    }

    @Test
    fun `capCount leaves a list within the limit alone`() {
        val items = listOf(1, 2, 3)
        assertEquals(items, enforcer().capCount(items, max = 3, telemetryType = "span_event"))
        assertEquals(emptyList<Pair<String, AppliedLimitType>>(), telemetryService.appliedLimits)
    }

    @Test
    fun `capCount drops the excess and reports it against the given telemetry type`() {
        assertEquals(listOf(1, 2), enforcer().capCount(listOf(1, 2, 3, 4), max = 2, telemetryType = "span_link"))
        assertEquals(listOf("span_link" to AppliedLimitType.DROP), telemetryService.appliedLimits)
    }

    @Test
    fun `otelLimits defaults to the instrumented config`() {
        assertSame(OtelLimitsConfigImpl, enforcer().otelLimits)
    }
}
