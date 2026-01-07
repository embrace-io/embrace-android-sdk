package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeMutableAttributeContainer
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.createNoopOpenTelemetry
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class EmbLoggerTest {
    private val clock = FakeClock()
    private val openTelemetryClock = FakeOtelKotlinClock(clock)
    private lateinit var eventService: FakeEventService
    private lateinit var sdkLogger: FakeOpenTelemetryLogger

    private lateinit var logger: EmbLogger

    @Before
    fun setup() {
        sdkLogger = FakeOpenTelemetryLogger()
        eventService = FakeEventService()
        logger = EmbLogger(
            impl = sdkLogger,
            eventService = eventService
        )
    }

    @Test
    fun `check log recorded with correct parameters`() {
        val parentCtx = createNoopOpenTelemetry().contextFactory.root()
        val observedTime = openTelemetryClock.now()
        val logTime = clock.tick()
        logger.log(
            body = "test",
            timestamp = logTime,
            observedTimestamp = observedTime,
            context = parentCtx,
            severityNumber = SeverityNumber.FATAL,
            severityText = "DANG",
        ) {
            setStringAttribute("foo", "bar")
        }
        val event = eventService.eventData.single()
        val expectedAttributes = FakeMutableAttributeContainer().apply {
            event.attributes?.invoke(this)
        }
        with(event) {
            assertEquals("test", body)
            assertEquals(logTime, timestamp)
            assertEquals(observedTime, observedTimestamp)
            assertEquals(parentCtx, context)
            assertEquals(SeverityNumber.FATAL, severityNumber)
            assertEquals("DANG", severityText)
            assertEquals("bar", expectedAttributes.attributes["foo"])
        }
    }
}
