package io.embrace.android.embracesdk.testframework.assertions

import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.assertions.getUserSessionTerminationReason
import io.embrace.android.embracesdk.assertions.isFinalSessionPart
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * Asserts this session part is the final part of its user session, stamped with the given termination [reason].
 */
fun Envelope<SessionPartPayload>.assertFinalPart(reason: String) {
    assertTrue("expected this to be the final session part", isFinalSessionPart())
    assertEquals(reason, getUserSessionTerminationReason())
}

/**
 * Asserts this session part is NOT final and carries no termination reason (i.e. the user session continues).
 */
fun Envelope<SessionPartPayload>.assertNotFinalPart() {
    assertFalse("expected this NOT to be the final session part", isFinalSessionPart())
    assertNull(getUserSessionTerminationReason())
}

/**
 * Asserts that all of the given session parts belong to distinct user sessions (different `emb.user_session_id`).
 */
fun assertDistinctUserSessions(vararg envelopes: Envelope<SessionPartPayload>) {
    val ids = envelopes.map { it.getUserSessionId() }
    assertEquals("expected distinct user session ids but got $ids", ids.size, ids.toSet().size)
}

/**
 * Asserts that all of the given session parts belong to the same user session (same `emb.user_session_id`).
 */
fun assertSameUserSession(vararg envelopes: Envelope<SessionPartPayload>) {
    val ids = envelopes.map { it.getUserSessionId() }.toSet()
    assertEquals("expected one user session id but got $ids", 1, ids.size)
}

/**
 * Asserts the session-id attributes on a log/crash record: both the OTel `session.id` and `emb.user_session_id`
 * carry [expectedUserSessionId], and `emb.session_part_id` equals [expectedSessionPartId]. Pass empty strings or
 * the native `"null"` sentinel to assert the no-part / no-session cases.
 */
fun Log.assertSessionIds(expectedUserSessionId: String, expectedSessionPartId: String) {
    val attrs = checkNotNull(attributes) { "no attributes found on log" }
    assertEquals(expectedUserSessionId, attrs.findAttributeValue(SessionAttributes.SESSION_ID))
    assertEquals(expectedUserSessionId, attrs.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_ID))
    assertEquals(expectedSessionPartId, attrs.findAttributeValue(EmbSessionAttributes.EMB_SESSION_PART_ID))
}
