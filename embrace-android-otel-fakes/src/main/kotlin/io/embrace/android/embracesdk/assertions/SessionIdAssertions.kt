package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

private val EMB_UUID_REGEX = Regex("[0-9a-fA-F]{32}")

/**
 * Session IDs extracted from attributes, returned by [assertSessionIds] so callers can
 * make further comparisons without re-reading the attributes.
 */
data class SessionIds(
    val userSessionId: String,
    val partId: String,
)


/**
 * Same assertions for an attribute map (e.g. from an OTel export assertion).
 */
fun Map<String, String?>.assertSessionIds(): SessionIds {
    val userSessionId = get(EmbSessionAttributes.EMB_USER_SESSION_ID)
    val sessionId = get(SessionAttributes.SESSION_ID)
    val partId = get(EmbSessionAttributes.EMB_SESSION_PART_ID)

    assertTrue(
        "emb.user_session_id must be a 32-char hex UUID but was: $userSessionId",
        EMB_UUID_REGEX.matches(userSessionId ?: ""),
    )
    assertTrue(
        "session.id must be a 32-char hex UUID but was: $sessionId",
        EMB_UUID_REGEX.matches(sessionId ?: ""),
    )
    assertTrue(
        "emb.session_part_id must be a 32-char hex UUID but was: $partId",
        EMB_UUID_REGEX.matches(partId ?: ""),
    )
    assertEquals("session.id must equal emb.user_session_id", userSessionId, sessionId)
    assertNotEquals("emb.session_part_id must not equal emb.user_session_id", partId, userSessionId)

    return SessionIds(
        userSessionId = checkNotNull(userSessionId),
        partId = checkNotNull(partId),
    )
}
