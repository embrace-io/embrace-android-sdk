package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.gating.EventSanitizer
import io.embrace.android.embracesdk.internal.gating.SessionGatingKeys.SESSION_PROPERTIES
import io.embrace.android.embracesdk.internal.payload.Event
import io.embrace.android.embracesdk.internal.payload.EventType
import io.embrace.android.embracesdk.internal.utils.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class EventSanitizerTest {

    @Test
    fun `test if a error-log event sanitize properties`() {
        // enabled components doesn't contain LOG_PROPERTIES
        val components = setOf<String>()

        val errorLogEvent = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 100L,
            type = EventType.ERROR_LOG,
            customProperties = mapOf("custom" to 123)
        )

        val sanitizerError = EventSanitizer(errorLogEvent, components)
        val resultLogError = sanitizerError.sanitize()
        assertNull(resultLogError.customProperties)
    }

    @Test
    fun `test if a info-log event sanitize properties`() {
        // enabled components doesn't contain LOG_PROPERTIES
        val components = setOf<String>()

        val infoLogEvent = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 100L,
            type = EventType.INFO_LOG,
            customProperties = mapOf("custom" to 123)
        )

        val sanitizerInfo = EventSanitizer(infoLogEvent, components)
        val resultInfo = sanitizerInfo.sanitize()
        assertNull(resultInfo.customProperties)
    }

    @Test
    fun `test if a warning-log event sanitize properties`() {
        // enabled components doesn't contain LOG_PROPERTIES
        val components = setOf<String>()

        val warningLogEvent = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 100L,
            type = EventType.WARNING_LOG,
            customProperties = mapOf("custom" to 123)
        )

        val sanitizerWarning = EventSanitizer(warningLogEvent, components)
        val resultWarning = sanitizerWarning.sanitize()
        assertNull(resultWarning.customProperties)
    }

    @Test
    fun `test if a non-log event keeps custom properties`() {
        // enabled components doesn't contain LOG_PROPERTIES
        val components = setOf<String>()

        val noLogEvent = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 100L,
            customProperties = mapOf("custom" to 123),
            type = EventType.START
        )

        val result = EventSanitizer(noLogEvent, components).sanitize()
        assertNotNull(result.customProperties)
    }

    @Test
    fun `test if it sanitizes session properties`() {
        // enabled components doesn't include SESSION_PROPERTIES
        val components = setOf<String>()

        val event = Event(
            eventId = "123",
            timestamp = 100L,
            duration = 1000L,
            appState = "state",
            type = EventType.INFO_LOG,
            customProperties = mapOf("custom" to 123),
            sessionProperties = mapOf("custom" to "custom"),
            logExceptionType = LogExceptionType.NONE.value
        )

        val sanitizer = EventSanitizer(event, components)
        val result = sanitizer.sanitize()

        // Expected: Same event without sessionProperties
        assertEquals("123", result.eventId)
        assertEquals(1000L, result.duration)
        assertEquals("state", result.appState)
        assertEquals(EventType.INFO_LOG, result.type)
        assertEquals(null, result.customProperties)
        assertEquals(null, result.sessionProperties)
        assertEquals(LogExceptionType.NONE.value, result.logExceptionType)
    }

    @Test
    fun `test if it keeps session properties`() {
        val components = setOf(SESSION_PROPERTIES)

        val event = Event(
            eventId = "123",
            timestamp = 100L,
            duration = 1000L,
            appState = "state",
            sessionProperties = mapOf("custom" to "custom"),
            logExceptionType = LogExceptionType.NONE.value,
            type = EventType.START
        )

        val sanitizer = EventSanitizer(event, components)
        val result = sanitizer.sanitize()

        // Expected: Same event
        assertEquals(event.eventId, result.eventId)
        assertEquals(event.duration, result.duration)
        assertEquals(event.appState, result.appState)
        assertEquals(event.sessionProperties, result.sessionProperties)
        assertEquals(event.logExceptionType, result.logExceptionType)
    }
}
