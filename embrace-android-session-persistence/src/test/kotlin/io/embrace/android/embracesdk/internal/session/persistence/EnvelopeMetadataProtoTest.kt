package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class EnvelopeMetadataProtoTest {

    @Test
    fun `fully populated metadata round-trips`() {
        val metadata = EnvelopeMetadataProto(
            user_id = "user-id",
            email = "user@example.com",
            username = "username",
            personas = listOf("persona1", "persona2"),
            timezone_description = "Europe/London",
            locale = "en_GB",
        )
        assertEquals(metadata, EnvelopeMetadataProto.ADAPTER.decode(EnvelopeMetadataProto.ADAPTER.encode(metadata)))
    }

    @Test
    fun `unset user fields decode back as null`() {
        val metadata = EnvelopeMetadataProto(
            timezone_description = "Europe/London",
            locale = "en_GB",
        )

        val decoded = EnvelopeMetadataProto.ADAPTER.decode(EnvelopeMetadataProto.ADAPTER.encode(metadata))
        assertNull(decoded.user_id)
        assertNull(decoded.email)
        assertNull(decoded.username)
        assertEquals("Europe/London", decoded.timezone_description)
        assertEquals("en_GB", decoded.locale)
    }

    @Test
    fun `empty personas round-trips as empty`() {
        val metadata = EnvelopeMetadataProto()
        val decoded = EnvelopeMetadataProto.ADAPTER.decode(EnvelopeMetadataProto.ADAPTER.encode(metadata))
        assertEquals(emptyList<String>(), decoded.personas)
    }
}
