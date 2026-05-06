package io.embrace.android.embracesdk.testframework.assertions

import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

/**
 * Bundle of the three session-id-related attributes the SDK stamps on telemetry. Values are
 * either the empty string (no active session/part) or non-blank UUIDs; they are compared by
 * relationships in tests, not by exact value, since exact values depend on the order in which
 * the seeded test [kotlin.random.Random] is consumed.
 */
internal data class TelemetrySessionIds(
    val userSessionId: String,
    val sessionPartId: String,
    val otelSessionId: String,
)

internal fun Span.extractSessionIds(): TelemetrySessionIds = attributes.toTelemetrySessionIds()

internal fun Log.extractSessionIds(): TelemetrySessionIds = attributes.toTelemetrySessionIds()

internal fun Envelope<SessionPartPayload>.extractSessionIds(): TelemetrySessionIds =
    checkNotNull(getSessionSpan()) { "No session span found in envelope." }.extractSessionIds()

internal fun Envelope<LogPayload>.extractSingleLogSessionIds(): TelemetrySessionIds {
    val log = checkNotNull(data.logs?.singleOrNull()) {
        "Expected exactly one log in payload but got ${data.logs?.size}."
    }
    return log.extractSessionIds()
}

private fun List<Attribute>?.toTelemetrySessionIds(): TelemetrySessionIds = TelemetrySessionIds(
    userSessionId = this?.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_ID).orEmpty(),
    sessionPartId = this?.findAttributeValue(EmbSessionAttributes.EMB_SESSION_PART_ID).orEmpty(),
    otelSessionId = this?.findAttributeValue(SessionAttributes.SESSION_ID).orEmpty(),
)

internal fun TelemetrySessionIds.assertActiveUserSession() {
    assertTrue("Expected user session id to be set, was '$userSessionId'.", userSessionId.isNotBlank())
    assertTrue("Expected session part id to be set, was '$sessionPartId'.", sessionPartId.isNotBlank())
    assertEquals("session.id should match emb.user_session_id.", userSessionId, otelSessionId)
}

internal fun TelemetrySessionIds.assertNoActiveUserSession() {
    assertEquals("Expected empty user session id.", "", userSessionId)
    assertEquals("Expected empty session part id.", "", sessionPartId)
    assertEquals("Expected empty session.id.", "", otelSessionId)
}

internal fun assertPartsOfSameUserSession(first: TelemetrySessionIds, second: TelemetrySessionIds) {
    first.assertActiveUserSession()
    second.assertActiveUserSession()
    assertEquals("Parts should share emb.user_session_id.", first.userSessionId, second.userSessionId)
    assertEquals("Parts should share session.id.", first.otelSessionId, second.otelSessionId)
    assertNotEquals("Session parts should have distinct emb.session_part_id.", first.sessionPartId, second.sessionPartId)
}

internal fun assertDistinctUserSessions(first: TelemetrySessionIds, second: TelemetrySessionIds) {
    first.assertActiveUserSession()
    second.assertActiveUserSession()
    assertNotEquals("Different user sessions should have distinct emb.user_session_id.", first.userSessionId, second.userSessionId)
    assertNotEquals("Different user sessions should have distinct session.id.", first.otelSessionId, second.otelSessionId)
    assertNotEquals("Different user sessions should have distinct emb.session_part_id.", first.sessionPartId, second.sessionPartId)
}
