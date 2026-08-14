package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class SessionPartDirectoryTest {

    private companion object {
        private const val TIMESTAMP = 1726739283136L
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val SESSION_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }

    @Test
    fun `construct dirname with session ids`() {
        assertEquals(
            "${TIMESTAMP}_${UUID}_${USER_SESSION_ID}_$SESSION_PART_ID",
            SessionPartDirectory(
                timestamp = TIMESTAMP,
                uuid = UUID,
                userSessionId = USER_SESSION_ID,
                sessionPartId = SESSION_PART_ID,
            ).dirName,
        )
    }

    @Test
    fun `construct dirname with empty session ids encodes none`() {
        assertEquals(
            "${TIMESTAMP}_${UUID}_none_none",
            SessionPartDirectory(
                timestamp = TIMESTAMP,
                uuid = UUID,
            ).dirName,
        )
    }

    @Test
    fun `construct dirname with only one empty session id`() {
        assertEquals(
            "${TIMESTAMP}_${UUID}_${USER_SESSION_ID}_none",
            SessionPartDirectory(
                timestamp = TIMESTAMP,
                uuid = UUID,
                userSessionId = USER_SESSION_ID,
                sessionPartId = "",
            ).dirName,
        )
    }

    @Test
    fun `from valid dirname`() {
        val input = "${TIMESTAMP}_${UUID}_${USER_SESSION_ID}_$SESSION_PART_ID"
        with(checkNotNull(SessionPartDirectory.fromDirName(input))) {
            assertEquals(input, dirName)
            assertEquals(TIMESTAMP, timestamp)
            assertEquals(UUID, uuid)
            assertEquals(USER_SESSION_ID, userSessionId)
            assertEquals(SESSION_PART_ID, sessionPartId)
        }
    }

    @Test
    fun `from valid dirname with none decodes to empty session ids`() {
        with(checkNotNull(SessionPartDirectory.fromDirName("${TIMESTAMP}_${UUID}_none_none"))) {
            assertEquals(TIMESTAMP, timestamp)
            assertEquals(UUID, uuid)
            assertEquals("", userSessionId)
            assertEquals("", sessionPartId)
        }
    }

    @Test
    fun `dirname with a dashless uuid round trips`() {
        val dashlessUuid = "1234567890ABCDEF1234567890ABCDEF"
        val original = SessionPartDirectory(
            timestamp = TIMESTAMP,
            uuid = dashlessUuid,
            userSessionId = USER_SESSION_ID,
            sessionPartId = SESSION_PART_ID,
        )
        val decoded = checkNotNull(SessionPartDirectory.fromDirName(original.dirName))
        assertEquals(original, decoded)
        assertEquals(dashlessUuid, decoded.uuid)
    }

    @Test
    fun `from invalid dirname`() {
        val badDirNames = listOf(
            "",
            "foo",
            "embrace_payloads",
            "${TIMESTAMP}_$UUID",
            "${TIMESTAMP}_${UUID}_${USER_SESSION_ID}_${SESSION_PART_ID}_extra",
            "notATimestamp_${UUID}_none_none",
            "${TIMESTAMP}__none_none",
        )
        badDirNames.forEach { dirName ->
            assertNull("Dirname should fail: $dirName", SessionPartDirectory.fromDirName(dirName))
        }
    }

    @Test
    fun `round trip preserves all fields`() {
        val original = SessionPartDirectory(
            timestamp = TIMESTAMP,
            uuid = UUID,
            userSessionId = USER_SESSION_ID,
            sessionPartId = SESSION_PART_ID,
        )
        val decoded = checkNotNull(SessionPartDirectory.fromDirName(original.dirName))
        assertEquals(original, decoded)
        assertEquals(original.dirName, decoded.dirName)
    }

    @Test
    fun `round trip with empty ids preserves empty after decode`() {
        val original = SessionPartDirectory(timestamp = TIMESTAMP, uuid = UUID)
        val decoded = checkNotNull(SessionPartDirectory.fromDirName(original.dirName))
        assertEquals("", decoded.userSessionId)
        assertEquals("", decoded.sessionPartId)
        assertEquals(original.dirName, decoded.dirName)
    }

    @Test
    fun `sorting orders by timestamp then uuid`() {
        val first = SessionPartDirectory(timestamp = 1L, uuid = "aaa")
        val second = SessionPartDirectory(timestamp = 1L, uuid = "bbb")
        val third = SessionPartDirectory(timestamp = 2L, uuid = "aaa")
        val fourth = SessionPartDirectory(timestamp = 10L, uuid = "aaa")

        val sorted = listOf(third, fourth, second, first).sortedWith(SessionPartDirectory.comparator)
        assertEquals(listOf(first, second, third, fourth), sorted)
    }

    @Test
    fun `sorting is stable when timestamp and uuid match`() {
        val first = SessionPartDirectory(timestamp = TIMESTAMP, uuid = UUID, userSessionId = USER_SESSION_ID)
        val second = SessionPartDirectory(timestamp = TIMESTAMP, uuid = UUID, sessionPartId = SESSION_PART_ID)

        val input = listOf(first, second)
        assertEquals(input, input.sortedWith(SessionPartDirectory.comparator))
    }
}
