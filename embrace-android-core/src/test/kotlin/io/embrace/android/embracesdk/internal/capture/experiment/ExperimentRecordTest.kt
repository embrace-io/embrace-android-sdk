package io.embrace.android.embracesdk.internal.capture.experiment

import org.junit.Assert.assertEquals
import org.junit.Test

internal class ExperimentRecordTest {

    @Test
    fun `serializes an open experiment with a variant`() {
        val record = ExperimentRecord(
            kind = ExperimentKind.EXPERIMENT,
            id = "id",
            variant = "variant",
            startTimeMs = 100L,
            endTimeMs = null,
        )
        assertEquals("e:id:variant:100", record.serialize())
    }

    @Test
    fun `serializes an open variant-less experiment`() {
        val record = ExperimentRecord(
            kind = ExperimentKind.EXPERIMENT,
            id = "id",
            variant = null,
            startTimeMs = 100L,
            endTimeMs = null,
        )
        assertEquals("e:id::100", record.serialize())
    }

    @Test
    fun `serializes an open feature flag`() {
        val record = ExperimentRecord(
            kind = ExperimentKind.FEATURE_FLAG,
            id = "id",
            variant = null,
            startTimeMs = 100L,
            endTimeMs = null,
        )
        assertEquals("f:id::100", record.serialize())
    }

    @Test
    fun `serializes an ended record`() {
        val record = ExperimentRecord(
            kind = ExperimentKind.EXPERIMENT,
            id = "id",
            variant = "variant",
            startTimeMs = 100L,
            endTimeMs = 200L,
        )
        assertEquals("e:id:variant:100:200", record.serialize())
    }

    @Test
    fun `percent-escapes id and variant while leaving ampersand and pipe unchanged`() {
        val record = ExperimentRecord(
            kind = ExperimentKind.EXPERIMENT,
            id = "id%:;&|",
            variant = "variant%:;&|",
            startTimeMs = 100L,
            endTimeMs = null,
        )
        assertEquals("e:id%25%3A%3B&|:variant%25%3A%3B&|:100", record.serialize())
    }

    @Test
    fun `escapes percent before delimiters so a pre-existing escape sequence round-trips`() {
        val record = ExperimentRecord(
            kind = ExperimentKind.EXPERIMENT,
            id = "id%3A",
            variant = null,
            startTimeMs = 100L,
            endTimeMs = null,
        )
        assertEquals("e:id%253A::100", record.serialize())
    }
}
