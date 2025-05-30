package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.SessionTimestamps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SpanAutoTerminationTest {

    private companion object {
        private const val ROOT_HANGING_SPAN = "root_a"
        private const val ROOT_START_SPAN = "root_b"
        private const val CHILD_START_SPAN_A = "child_a"
        private const val CHILD_START_SPAN_B = "child_b"
        private const val CHILD_START_SPAN_C = "child_c"
        private const val CHILD_START_SPAN_D = "child_d"
        private const val CHILD_START_SPAN_E = "child_e"
        private const val ROOT_CREATE_SPAN = "root_c"
        private const val ROOT_RECORD_SPAN = "root_d"
        private const val ROOT_RECORD_COMPLETED_SPAN = "root_e"
        private const val ROOT_STOPPED_SPAN = "root_f"
        private const val BG_SPAN = "bg_span"
    }

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `auto termination feature`() {
        var firstSessionTimestamps: SessionTimestamps? = null
        var secondSessionTimestamps: SessionTimestamps? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true
                )
            ),
            testCaseAction = {
                var hangingSpan: EmbraceSpan? = null

                // first session
                firstSessionTimestamps = recordSession {
                    // start a span without auto termination
                    hangingSpan = embrace.startSpan(
                        ROOT_HANGING_SPAN,
                        autoTerminationMode = AutoTerminationMode.NONE
                    )

                    // start a span with children and auto termination
                    val parent = embrace.startSpan(
                        ROOT_START_SPAN,
                        autoTerminationMode = AutoTerminationMode.ON_BACKGROUND
                    )
                    embrace.startSpan(
                        CHILD_START_SPAN_A,
                        parent = parent,
                        autoTerminationMode = AutoTerminationMode.ON_BACKGROUND
                    )
                    val childB = embrace.startSpan(CHILD_START_SPAN_B, parent = parent)
                    embrace.startSpan(CHILD_START_SPAN_C, parent = childB)

                    val childD = embrace.startSpan(CHILD_START_SPAN_D, parent = parent)
                    embrace.startSpan(CHILD_START_SPAN_E, parent = childD)
                    childD?.stop()

                    // create a span with auto termination
                    embrace.createSpan(
                        ROOT_CREATE_SPAN,
                        autoTerminationMode = AutoTerminationMode.ON_BACKGROUND
                    )?.start()

                    // record a span
                    embrace.recordSpan(ROOT_RECORD_SPAN) {
                        embrace.addBreadcrumb("Hello, world!")
                    }

                    // record a completed span
                    embrace.recordCompletedSpan(
                        ROOT_RECORD_COMPLETED_SPAN,
                        clock.now() - 1000,
                        clock.now()
                    )

                    // stop a span with auto termination
                    embrace.createSpan(
                        ROOT_STOPPED_SPAN,
                        autoTerminationMode = AutoTerminationMode.ON_BACKGROUND
                    )?.apply {
                        start()
                        stop()
                    }
                }

                // background activity
                embrace.startSpan(BG_SPAN, autoTerminationMode = AutoTerminationMode.ON_BACKGROUND)

                // second session
                secondSessionTimestamps = recordSession {
                    // stop a span without auto termination
                    hangingSpan?.stop()
                }
            },
            assertAction = {
                val message = getSessionEnvelopes(2)
                checkNotNull(firstSessionTimestamps).assertFirstSpans(message[0])
                checkNotNull(secondSessionTimestamps).assertSecondSpans(message[1])
            }
        )
    }

    private fun SessionTimestamps.assertFirstSpans(
        first: Envelope<SessionPayload>,
    ) {
        // startSpan() with children
        val rootb = first.findSpanByName(ROOT_START_SPAN)
        assertEquals(endTimeMs.millisToNanos(), rootb.endTimeNanos)

        val childa = first.findSpanByName(CHILD_START_SPAN_A)
        assertEquals(endTimeMs.millisToNanos(), childa.endTimeNanos)

        val childb = first.findSpanByName(CHILD_START_SPAN_B)
        assertEquals(endTimeMs.millisToNanos(), childb.endTimeNanos)

        val childc = first.findSpanByName(CHILD_START_SPAN_C)
        assertEquals(endTimeMs.millisToNanos(), childc.endTimeNanos)

        val childd = first.findSpanByName(CHILD_START_SPAN_D)
        assertEquals(actionTimeMs.millisToNanos(), childd.endTimeNanos)

        val childe = first.findSpanByName(CHILD_START_SPAN_E)
        assertEquals(endTimeMs.millisToNanos(), childe.endTimeNanos)

        // createSpan()
        val rootc = first.findSpanByName(ROOT_CREATE_SPAN)
        assertEquals(endTimeMs.millisToNanos(), rootc.endTimeNanos)

        // recordSpan()
        val rootd = first.findSpanByName(ROOT_RECORD_SPAN)
        assertEquals(rootd.startTimeNanos, rootd.endTimeNanos)

        // recordCompletedSpan()
        val roote = first.findSpanByName(ROOT_RECORD_COMPLETED_SPAN)
        assertNotNull(roote.endTimeNanos)

        // stopped span
        val rootf = first.findSpanByName(ROOT_STOPPED_SPAN)
        assertEquals(rootf.startTimeNanos, rootf.endTimeNanos)

        // non-terminated span
        val hangingSpan =
            checkNotNull(first.data.spanSnapshots?.single { it.name == ROOT_HANGING_SPAN })
        assertNull(hangingSpan.endTimeNanos)
    }

    private fun SessionTimestamps.assertSecondSpans(
        second: Envelope<SessionPayload>,
    ) {
        val roota = second.findSpanByName(ROOT_HANGING_SPAN)
        assertEquals(actionTimeMs.millisToNanos(), roota.endTimeNanos)

        // spans in background activity are not auto-terminated until the next session ends
        val bgSpan = second.findSpanByName(BG_SPAN)
        assertEquals(endTimeMs.millisToNanos(), bgSpan.endTimeNanos)
    }
}
