package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Test

internal class TransitionTypeTest {

    @Test
    fun `END_MANUAL endAttributes`() {
        val attrs = TransitionType.END_MANUAL.endAttributes
        assertEquals("1", attrs[EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART])
        assertEquals(
            EmbSessionAttributes.EmbTerminationReasonValues.MANUAL,
            attrs[EmbSessionAttributes.EMB_TERMINATION_REASON],
        )
    }

    @Test
    fun `INACTIVITY_TIMEOUT endAttributes`() {
        val attrs = TransitionType.INACTIVITY_TIMEOUT.endAttributes
        assertEquals("1", attrs[EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART])
        assertEquals(
            EmbSessionAttributes.EmbTerminationReasonValues.INACTIVITY,
            attrs[EmbSessionAttributes.EMB_TERMINATION_REASON],
        )
    }

    @Test
    fun `INACTIVITY_FOREGROUND endAttributes`() {
        val attrs = TransitionType.INACTIVITY_FOREGROUND.endAttributes
        assertEquals("1", attrs[EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART])
        assertEquals(
            EmbSessionAttributes.EmbTerminationReasonValues.INACTIVITY,
            attrs[EmbSessionAttributes.EMB_TERMINATION_REASON],
        )
    }

    @Test
    fun `MAX_DURATION endAttributes`() {
        val attrs = TransitionType.MAX_DURATION.endAttributes
        assertEquals("1", attrs[EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART])
        assertEquals(
            EmbSessionAttributes.EmbTerminationReasonValues.MAX_DURATION_REACHED,
            attrs[EmbSessionAttributes.EMB_TERMINATION_REASON],
        )
    }

    @Test
    fun `non-final transition types have empty endAttributes`() {
        listOf(
            TransitionType.INITIAL,
            TransitionType.ON_FOREGROUND,
            TransitionType.ON_BACKGROUND,
            TransitionType.CRASH,
        ).forEach { type ->
            assertEquals(emptyMap<String, String>(), type.endAttributes)
        }
    }
}
