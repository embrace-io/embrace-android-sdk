package io.embrace.android.embracesdk.testframework.assertions

import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.getSessionId
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import junit.framework.TestCase.assertFalse

/**
 * Verifies a set of golden files against the payloads emitted during a single test run. How the golden files and payloads are
 * passed in dictates the expected user session and session part IDs in terms of whether they match within the given golden files.
 *
 * The reason we can't simply hardcode the expected IDs is due to race conditions that affect the order in which the UUIDs are generated.
 * Hence, we verify correctness using internal consistency within the payloads with respect to the golden files without having a priori
 * knowledge of what the IDs will actually be. Session parts from the same user session should have different IDs
 */
internal fun EmbracePayloadAssertionInterface.assertPayloadsMatchGoldenFiles(
    vararg userSessions: UserSessionDiff,
    partsWithNoUserSession: List<SessionPartDiff> = emptyList(),
    logsWithNoUserSession: List<LogDiff> = emptyList(),
) {
    val alreadySeenUserSessions = mutableSetOf<String>()
    val alreadySeenSessionParts = mutableSetOf<String>()

    partsWithNoUserSession.forEach { partDiff ->
        verifySessionPart(
            sessionPartDiff = partDiff,
            expectedUserSessionId = "",
            expectedSessionPartId = ""
        )
        partDiff.logs.forEach { logDiff ->
            verifyLog(
                logDiff = logDiff,
                expectedUserSessionId = "",
                expectedSessionPartId = ""
            )
        }
    }

    logsWithNoUserSession.forEach { logDiff ->
        verifyLog(
            logDiff = logDiff,
            expectedUserSessionId = "",
            expectedSessionPartId = ""
        )
    }

    userSessions.forEach { userSession ->
        // Use the first part's userSessionId as the correct one for all the payloads
        val userSessionId: String = userSession.partDiffs.first().envelope.getSessionId().orEmpty().also { newId ->
            assertFalse(
                "Unexpected user session ID. Duplicate of a previously seen one",
                alreadySeenUserSessions.contains(newId)
            )
            alreadySeenUserSessions.add(newId)
        }

        userSession.logsWithoutPart.forEach { logDiff ->
            verifyLog(
                logDiff = logDiff,
                expectedUserSessionId = userSessionId,
                expectedSessionPartId = ""
            )
        }

        userSession.partDiffs.forEach { partDiff ->
            val sessionPartId = partDiff.envelope.getSessionPartId().also { newId ->
                assertFalse(
                    "Unexpected session part ID. Duplicate of a previously seen one",
                    alreadySeenSessionParts.contains(newId)
                )
                alreadySeenSessionParts.add(newId)
            }
            verifySessionPart(
                sessionPartDiff = partDiff,
                expectedUserSessionId = userSessionId,
                expectedSessionPartId = sessionPartId
            )

            partDiff.logs.forEach { logDiff ->
                verifyLog(
                    logDiff = logDiff,
                    expectedUserSessionId = userSessionId,
                    expectedSessionPartId = sessionPartId
                )
            }
        }
    }
}

/**
 * Asserts that the [LogPayload] from [envelope] matches the golden JSON file [goldenFile] with the given user session and session part IDs.
 * Only the payload data (the `logs` list) is compared — not the whole envelope.
 */
internal fun EmbracePayloadAssertionInterface.assertLogPayloadMatchesGoldenFile(
    envelope: Envelope<LogPayload>,
    expectedUserSessionId: String,
    expectedSessionPartId: String,
    goldenFile: String,
) {
    validatePayloadAgainstGoldenFile(
        payload = envelope.data,
        goldenFileName = goldenFile,
        placeholders = mapOf(
            Placeholder.USER_SESSION_ID to expectedUserSessionId,
            Placeholder.SESSION_PART_ID to expectedSessionPartId,
        ),
    )
}

private fun EmbracePayloadAssertionInterface.verifySessionPart(
    sessionPartDiff: SessionPartDiff,
    expectedUserSessionId: String,
    expectedSessionPartId: String,
) {
    validatePayloadAgainstGoldenFile(
        payload = sessionPartDiff.envelope.findSessionSpan(),
        goldenFileName = sessionPartDiff.goldenFile,
        placeholders = mapOf(
            Placeholder.USER_SESSION_ID to expectedUserSessionId,
            Placeholder.SESSION_PART_ID to expectedSessionPartId,
        ),
    )
}

private fun EmbracePayloadAssertionInterface.verifyLog(
    logDiff: LogDiff,
    expectedUserSessionId: String,
    expectedSessionPartId: String,
) {
    validatePayloadAgainstGoldenFile(
        payload = logDiff.envelope.data,
        goldenFileName = logDiff.goldenFile,
        placeholders = mapOf(
            Placeholder.USER_SESSION_ID to expectedUserSessionId,
            Placeholder.SESSION_PART_ID to expectedSessionPartId,
        ),
    )
}

internal enum class Placeholder(val token: String) {
    USER_SESSION_ID("__EMBRACE_TEST_USER_SESSION_ID__"),
    SESSION_PART_ID("__EMBRACE_TEST_SESSION_PART_ID__"),
}

internal data class LogDiff(
    val envelope: Envelope<LogPayload>,
    val goldenFile: String,
)

internal data class SessionPartDiff(
    val envelope: Envelope<SessionPartPayload>,
    val goldenFile: String,
    val logs: List<LogDiff> = emptyList(),
)

internal class UserSessionDiff(
    vararg parts: SessionPartDiff,
    val logsWithoutPart: List<LogDiff> = emptyList(),
) {
    val partDiffs: List<SessionPartDiff> = parts.toList()
}
