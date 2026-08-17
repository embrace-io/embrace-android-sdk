package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class EnvelopeMetadataMapperTest {

    @Test
    fun `every field maps to its proto counterpart`() {
        assertEquals(fullyPopulatedMetadataProto, fullyPopulatedMetadata.toProto())
    }

    @Test
    fun `null user fields map to absent proto fields`() {
        val proto = EnvelopeMetadata(timezoneDescription = "Europe/London", locale = "en_GB").toProto()
        assertNull(proto.user_id)
        assertNull(proto.email)
        assertNull(proto.username)
    }

    @Test
    fun `empty string fields are preserved`() {
        val proto = EnvelopeMetadata(userId = "", email = "", username = "").toProto()
        assertEquals("", proto.user_id)
        assertEquals("", proto.email)
        assertEquals("", proto.username)
    }

    @Test
    fun `null timezone and locale map to empty strings`() {
        val proto = EnvelopeMetadata().toProto()
        assertEquals("", proto.timezone_description)
        assertEquals("", proto.locale)
    }

    @Test
    fun `null personas map to an empty list`() {
        assertEquals(emptyList<String>(), EnvelopeMetadata(personas = null).toProto().personas)
    }

    @Test
    fun `empty personas map to an empty list`() {
        assertEquals(emptyList<String>(), EnvelopeMetadata(personas = emptySet()).toProto().personas)
    }

    @Test
    fun `personas are preserved`() {
        val personas = linkedSetOf("payer", "first_day", "persona1")
        assertEquals(personas.toList(), EnvelopeMetadata(personas = personas).toProto().personas)
    }

    @Test
    fun `fully populated metadata survives a round trip through the wire format`() {
        val proto = fullyPopulatedMetadata.toProto()
        assertEquals(proto, EnvelopeMetadataProto.ADAPTER.decode(EnvelopeMetadataProto.ADAPTER.encode(proto)))
    }
}
