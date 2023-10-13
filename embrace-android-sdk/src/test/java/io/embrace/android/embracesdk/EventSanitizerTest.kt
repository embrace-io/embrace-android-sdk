package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.gating.EventSanitizer
import io.embrace.android.embracesdk.gating.SessionGatingKeys.SESSION_PROPERTIES
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.payload.Event
import org.junit.Assert
import org.junit.Test

internal class EventSanitizerTest {

    @Test
    fun `test if a error-log event sanitize properties`() {
        // enabled components doesn't contain LOG_PROPERTIES
        val components = setOf<String>()

        val errorLogEvent = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 100L,
            type = EmbraceEvent.Type.ERROR_LOG,
            customProperties = mapOf("custom" to 123)
        )

        val sanitizerError = EventSanitizer(errorLogEvent, components)
        val resultLogError = sanitizerError.sanitize()
        Assert.assertNull(resultLogError.customPropertiesMap)
    }

    @Test
    fun `test if a info-log event sanitize properties`() {
        // enabled components doesn't contain LOG_PROPERTIES
        val components = setOf<String>()

        val infoLogEvent = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 100L,
            type = EmbraceEvent.Type.INFO_LOG,
            customProperties = mapOf("custom" to 123)
        )

        val sanitizerInfo = EventSanitizer(infoLogEvent, components)
        val resultInfo = sanitizerInfo.sanitize()
        Assert.assertNull(resultInfo.customPropertiesMap)
    }

    @Test
    fun `test if a warning-log event sanitize properties`() {
        // enabled components doesn't contain LOG_PROPERTIES
        val components = setOf<String>()

        val warningLogEvent = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 100L,
            type = EmbraceEvent.Type.WARNING_LOG,
            customProperties = mapOf("custom" to 123)
        )

        val sanitizerWarning = EventSanitizer(warningLogEvent, components)
        val resultWarning = sanitizerWarning.sanitize()
        Assert.assertNull(resultWarning.customPropertiesMap)
    }

    @Test
    fun `test if a non-log event keeps custom properties`() {
        // enabled components doesn't contain LOG_PROPERTIES
        val components = setOf<String>()

        val noLogEvent = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 100L,
            customProperties = mapOf("custom" to 123),
            type = EmbraceEvent.Type.START
        )

        val result = EventSanitizer(noLogEvent, components).sanitize()
        Assert.assertNotNull(result.customPropertiesMap)
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
            type = EmbraceEvent.Type.INFO_LOG,
            customProperties = mapOf("custom" to 123),
            sessionProperties = mapOf("custom" to "custom"),
            logExceptionType = LogExceptionType.NONE.value
        )

        val sanitizer = EventSanitizer(event, components)
        val result = sanitizer.sanitize()

        // Expected: Same event without sessionProperties
        Assert.assertEquals("123", result.eventId)
        Assert.assertEquals(1000L, result.duration)
        Assert.assertEquals("state", result.appState)
        Assert.assertEquals(EmbraceEvent.Type.INFO_LOG, result.type)
        Assert.assertEquals(null, result.customPropertiesMap)
        Assert.assertEquals(null, result.sessionPropertiesMap)
        Assert.assertEquals(LogExceptionType.NONE.value, result.logExceptionType)
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
            type = EmbraceEvent.Type.START
        )

        val sanitizer = EventSanitizer(event, components)
        val result = sanitizer.sanitize()

        // Expected: Same event
        Assert.assertEquals(event.eventId, result.eventId)
        Assert.assertEquals(event.duration, result.duration)
        Assert.assertEquals(event.appState, result.appState)
        Assert.assertEquals(event.sessionPropertiesMap, result.sessionPropertiesMap)
        Assert.assertEquals(event.logExceptionType, result.logExceptionType)
    }
}
