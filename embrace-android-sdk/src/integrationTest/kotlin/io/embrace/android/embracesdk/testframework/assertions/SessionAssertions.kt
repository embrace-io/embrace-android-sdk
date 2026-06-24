package io.embrace.android.embracesdk.testframework.assertions

import io.embrace.android.embracesdk.assertions.SessionIds
import io.embrace.android.embracesdk.assertions.getOtelSessionId
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.getSessionPartNumber
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.assertions.getUserSessionNumber
import io.embrace.android.embracesdk.assertions.getUserSessionPartIndex
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
import org.junit.Assert.assertNotEquals
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
 * Asserts a single session part's user-session number, and optionally its session part number and part index
 */
fun Envelope<SessionPartPayload>.assertSessionOrdinals(
    userSessionNumber: Int,
    sessionPartNumber: Int? = null,
    partIndex: Int? = null,
) {
    assertEquals("unexpected emb.user_session_number", userSessionNumber.toString(), getUserSessionNumber())
    if (sessionPartNumber != null) {
        assertEquals("unexpected emb.session_part_number", sessionPartNumber.toString(), getSessionPartNumber())
    }
    if (partIndex != null) {
        assertEquals("unexpected emb.user_session_part_index", partIndex.toString(), getUserSessionPartIndex())
    }
}

/**
 * Asserts the user session numbers and optionally the session part numbers across a chronologically sorted list of session part envelopes.
 */
fun assertUserSessionNumbers(
    envelopes: List<Envelope<SessionPartPayload>>,
    userSessionNumbers: List<Int>,
    sessionPartNumbers: List<Int>? = null,
) {
    assertEquals(
        "expected ${userSessionNumbers.size} session-part envelopes but got ${envelopes.size}",
        userSessionNumbers.size,
        envelopes.size,
    )
    if (sessionPartNumbers != null) {
        assertEquals(
            "envelope count does not match the user session numbers to be asserted",
            userSessionNumbers.size,
            sessionPartNumbers.size,
        )
    }
    envelopes.forEachIndexed { index, envelope ->
        envelope.assertSessionOrdinals(
            userSessionNumber = userSessionNumbers[index],
            sessionPartNumber = sessionPartNumbers?.get(index),
        )
    }
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

/**
 * Asserts the session part payload's session span has all session-related IDs: emb.user_session_id, session.id, and emb.session_part_id
 * The first two are always equal, while the session part ID should not equal the other two.
 */
fun Envelope<SessionPartPayload>.assertSessionIds(): SessionIds {
    val otelSessionId = getOtelSessionId()
    val userSessionId = getUserSessionId()
    val sessionPartId = getSessionPartId()
    assertEquals("session.id must equal emb.user_session_id", userSessionId, otelSessionId)
    assertNotEquals("emb.session_part_id must not equal emb.user_session_id", sessionPartId, userSessionId)
    return SessionIds(userSessionId = userSessionId, partId = sessionPartId)
}

/**
 * Asserts a log has session.id and emb.user_session_id which are the same value.
 */
fun Log.assertSessionIdsConsistent() {
    val attrs = checkNotNull(attributes) { "no attributes found on log" }
    val otelSessionId = attrs.findAttributeValue(SessionAttributes.SESSION_ID)
    assertFalse("session.id must be present on the log", otelSessionId.isNullOrBlank())
    assertEquals(
        "session.id must equal emb.user_session_id on the log",
        otelSessionId,
        attrs.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_ID)
    )
}
