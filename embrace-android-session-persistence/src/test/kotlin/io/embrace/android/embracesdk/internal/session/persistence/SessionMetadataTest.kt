package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class SessionMetadataTest {

    @Test
    fun `fully populated metadata round-trips`() {
        val metadata = SessionMetadata(
            format_version = 1,
            envelope_version = "0.1.0",
            envelope_type = "spans",
            user_session_id = "user-session-id",
            session_part_id = "session-part-id",
            shared_lib_symbol_mapping = SharedLibSymbolMapping(
                symbols = mapOf("abc-123-uuid" to "libembrace.so"),
            ),
            resource = ResourceProto(
                app_version = "1.2.3",
                app_framework = ResourceProto.AppFramework.NATIVE,
                device_soc_model = "Tensor G3",
                screen_resolution = "1080x2400",
            ),
            user_id = "user-id",
            email = "user@example.com",
            username = "username",
            personas = listOf("persona1", "persona2"),
            timezone_description = "Europe/London",
            locale = "en_GB",
        )
        assertEquals(metadata, roundTrip(metadata))
    }

    @Test
    fun `unset user fields decode back as null`() {
        val decoded = roundTrip(
            SessionMetadata(timezone_description = "Europe/London", locale = "en_GB"),
        )
        assertNull(decoded.user_id)
        assertNull(decoded.email)
        assertNull(decoded.username)
        assertEquals("Europe/London", decoded.timezone_description)
        assertEquals("en_GB", decoded.locale)
    }

    @Test
    fun `empty personas round-trips as empty`() {
        assertEquals(emptyList<String>(), roundTrip(SessionMetadata()).personas)
    }

    @Test
    fun `absent symbol mapping stays distinct from an empty one`() {
        assertNull(roundTrip(SessionMetadata(shared_lib_symbol_mapping = null)).shared_lib_symbol_mapping)
        assertEquals(
            SharedLibSymbolMapping(),
            roundTrip(SessionMetadata(shared_lib_symbol_mapping = SharedLibSymbolMapping())).shared_lib_symbol_mapping,
        )
    }

    @Test
    fun `absent resource stays distinct from an empty resource`() {
        assertNull(roundTrip(SessionMetadata(resource = null)).resource)
        assertEquals(ResourceProto(), roundTrip(SessionMetadata(resource = ResourceProto())).resource)
    }

    @Test
    fun `format version survives on an otherwise empty message`() {
        assertEquals(3, roundTrip(SessionMetadata(format_version = 3)).format_version)
    }

    private fun roundTrip(metadata: SessionMetadata): SessionMetadata =
        SessionMetadata.ADAPTER.decode(SessionMetadata.ADAPTER.encode(metadata))
}
