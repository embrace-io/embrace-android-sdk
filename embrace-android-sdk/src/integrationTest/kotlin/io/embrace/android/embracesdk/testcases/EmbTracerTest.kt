package io.embrace.android.embracesdk.testcases

import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.opentelemetry.EmbTracer
import io.embrace.android.embracesdk.recordSession
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.Tracer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [UPSIDE_DOWN_CAKE])
@RunWith(AndroidJUnit4::class)
internal class EmbTracerTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(
        harnessSupplier = {
            IntegrationTestRule.newHarness(startImmediately = false)
        }
    )

    private lateinit var spanExporter: FakeSpanExporter
    private lateinit var embTracer: Tracer
    private lateinit var clock: FakeClock

    @Before
    fun setup() {
        spanExporter = FakeSpanExporter()
        with(testRule) {
            clock = harness.overriddenClock
            embrace.addSpanExporter(spanExporter)
            startSdk(context = harness.overriddenCoreModule.context)
            embTracer = checkNotNull(embrace.getTracer("EmbTracerTest"))
        }
    }

    @Test
    fun `check correctness of external Tracer`() {
        assertTrue(embTracer is EmbTracer)
    }

    @Test
    fun `span created exports correctly`() {
        with(testRule) {
            var startTimeMs: Long? = null
            var endTimeMs: Long? = null
            val sessionMessage = harness.recordSession {
                val spanBuilder = embTracer.spanBuilder("external-span")
                startTimeMs = harness.overriddenClock.now()
                val span = spanBuilder.startSpan()
                endTimeMs = harness.overriddenClock.tick()
                span.end()
            }
            val spans = checkNotNull(sessionMessage?.spans)
            val recordedSpan = spans.single { it.name == "external-span" }
            assertEmbraceSpanData(
                span = recordedSpan,
                expectedStartTimeMs = checkNotNull(startTimeMs),
                expectedEndTimeMs = checkNotNull(endTimeMs),
                expectedParentId = SpanId.getInvalid(),
                key = true
            )
            assertTrue("Timed out waiting for the span to be exported", spanExporter.awaitSpanExport(2))
            val exportedSpan = spanExporter.exportedSpans.single { it.name == "external-span" }
            assertEquals(recordedSpan, EmbraceSpanData(exportedSpan))
        }
    }
}