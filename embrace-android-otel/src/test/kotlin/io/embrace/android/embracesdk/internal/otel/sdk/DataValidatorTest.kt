package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class DataValidatorTest {
    private lateinit var telemetryService: FakeTelemetryService
    private lateinit var dataValidator: DataValidator

    @Before
    fun setup() {
        telemetryService = FakeTelemetryService()
        dataValidator = DataValidator(telemetryService = telemetryService)
    }

    @Test
    fun `attributes at the count limit are returned with identical contents`() {
        listOf(false, true).forEach { internal ->
            val max = maxAttributeCount(internal)
            val input = createAttributes(max)

            val result = dataValidator.truncateAttributes(input, internal)

            assertEquals(input, result)
        }
        assertTrue(telemetryService.appliedLimits.isEmpty())
    }

    @Test
    fun `attributes over the count limit keep exactly the max entries`() {
        listOf(false, true).forEach { internal ->
            val max = maxAttributeCount(internal)
            val input = createAttributes(max + 5)

            val result = dataValidator.truncateAttributes(input, internal)

            assertEquals(max, result.size)
            assertEquals(input.entries.take(max).associate { it.key to it.value }, result)
        }
        assertEquals(
            listOf(
                "span_attribute" to AppliedLimitType.TRUNCATE_ATTRIBUTES,
                "span_attribute" to AppliedLimitType.TRUNCATE_ATTRIBUTES,
            ),
            telemetryService.appliedLimits,
        )
    }

    @Test
    fun `event attributes over the count limit keep exactly the max event attribute count`() {
        val max = dataValidator.otelLimitsConfig.getMaxEventAttributeCount()
        val input = createAttributes(max + 5)

        val event = checkNotNull(
            dataValidator.createTruncatedSpanEvent(
                name = "event",
                timestampMs = 1000L,
                internal = false,
                attributes = input,
            ),
        )

        assertEquals(input.entries.take(max).associate { it.key to it.value }, event.attributes)
    }

    @Test
    fun `attribute keys and values are truncated to the configured lengths`() {
        with(dataValidator.otelLimitsConfig) {
            assertKeyAndValueTruncation(
                internal = false,
                maxKeyLength = getMaxCustomAttributeKeyLength(),
                maxValueLength = getMaxCustomAttributeValueLength(),
            )
            assertKeyAndValueTruncation(
                internal = true,
                maxKeyLength = getMaxInternalAttributeKeyLength(),
                maxValueLength = getMaxInternalAttributeValueLength(),
            )
        }
    }

    @Test
    fun `truncated attributes are not affected by later mutation of the input map`() {
        val input = createAttributes(3).toMutableMap()
        val expected = input.toMap()

        val truncated = dataValidator.truncateAttributes(input, internal = false)
        val event = checkNotNull(
            dataValidator.createTruncatedSpanEvent(
                name = "event",
                timestampMs = 1000L,
                internal = false,
                attributes = input,
            ),
        )

        input["key0"] = "mutated"
        input.remove("key1")
        input["added"] = "value"

        assertEquals(expected, truncated)
        assertEquals(expected, event.attributes)
    }

    @Test
    fun `attributes returned with validation bypassed are not affected by later mutation of the input map`() {
        dataValidator = DataValidator(bypassValidation = { true }, telemetryService = telemetryService)
        val input = createAttributes(3).toMutableMap()
        val expected = input.toMap()

        val truncated = dataValidator.truncateAttributes(input, internal = false)
        val event = checkNotNull(
            dataValidator.createTruncatedSpanEvent(
                name = "event",
                timestampMs = 1000L,
                internal = false,
                attributes = input,
            ),
        )

        input["key0"] = "mutated"
        input.remove("key1")
        input["added"] = "value"

        assertEquals(expected, truncated)
        assertEquals(expected, event.attributes)
    }

    private fun assertKeyAndValueTruncation(internal: Boolean, maxKeyLength: Int, maxValueLength: Int) {
        val maxLengthKey = "k".repeat(maxKeyLength)
        val maxLengthValue = "v".repeat(maxValueLength)
        val tooLongKey = "k".repeat(maxKeyLength + 10)
        val tooLongValue = "v".repeat(maxValueLength + 10)
        val truncatedKey = "k".repeat(maxKeyLength - TRUNCATION_SUFFIX.length) + TRUNCATION_SUFFIX
        val truncatedValue = "v".repeat(maxValueLength - TRUNCATION_SUFFIX.length) + TRUNCATION_SUFFIX

        // a map is truncated entry by entry
        val result = dataValidator.truncateAttributes(
            mapOf(
                tooLongKey to "value",
                "long-value" to tooLongValue,
                maxLengthKey to maxLengthValue,
            ),
            internal,
        )
        assertEquals(
            mapOf(
                truncatedKey to "value",
                "long-value" to truncatedValue,
                maxLengthKey to maxLengthValue,
            ),
            result,
        )

        // as is a single attribute
        assertEquals(truncatedKey to truncatedValue, dataValidator.truncateAttribute(tooLongKey, tooLongValue, internal))
        assertEquals(maxLengthKey to maxLengthValue, dataValidator.truncateAttribute(maxLengthKey, maxLengthValue, internal))
    }

    private fun maxAttributeCount(internal: Boolean): Int = if (internal) {
        dataValidator.otelLimitsConfig.getMaxSystemAttributeCount()
    } else {
        dataValidator.otelLimitsConfig.getMaxCustomAttributeCount()
    }

    private fun createAttributes(count: Int): Map<String, String> =
        (0 until count).associate { "key$it" to "value$it" }

    private companion object {
        const val TRUNCATION_SUFFIX = "..."
    }
}
