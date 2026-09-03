package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class EnvelopeMetadataMapperTest {

    @Test
    fun `every field maps to its proto counterpart`() {
        assertEquals(fullyPopulatedMetadataProto, fullyPopulatedMetadata.toProto(fullyPopulatedMutableResourceProto))
    }

    @Test
    fun `the format version is stamped on the proto`() {
        assertEquals(FORMAT_VERSION, EnvelopeMetadata().toProto(MutableResourceProto()).format_version)
    }

    @Test
    fun `null user fields map to absent proto fields`() {
        val proto = EnvelopeMetadata(timezoneDescription = "Europe/London", locale = "en_GB").toProto(MutableResourceProto())
        assertNull(proto.user_id)
        assertNull(proto.email)
        assertNull(proto.username)
    }

    @Test
    fun `empty string fields are preserved`() {
        val proto = EnvelopeMetadata(userId = "", email = "", username = "").toProto(MutableResourceProto())
        assertEquals("", proto.user_id)
        assertEquals("", proto.email)
        assertEquals("", proto.username)
    }

    @Test
    fun `null timezone and locale map to empty strings`() {
        val proto = EnvelopeMetadata().toProto(MutableResourceProto())
        assertEquals("", proto.timezone_description)
        assertEquals("", proto.locale)
    }

    @Test
    fun `null personas map to an empty list`() {
        assertEquals(emptyList<String>(), EnvelopeMetadata(personas = null).toProto(MutableResourceProto()).personas)
    }

    @Test
    fun `empty personas map to an empty list`() {
        assertEquals(emptyList<String>(), EnvelopeMetadata(personas = emptySet()).toProto(MutableResourceProto()).personas)
    }

    @Test
    fun `personas are preserved`() {
        val personas = linkedSetOf("payer", "first_day", "persona1")
        assertEquals(personas.toList(), EnvelopeMetadata(personas = personas).toProto(MutableResourceProto()).personas)
    }

    @Test
    fun `fully populated metadata survives a round trip through the wire format`() {
        val proto = fullyPopulatedMetadata.toProto(fullyPopulatedMutableResourceProto)
        assertEquals(proto, EnvelopeMetadataProto.ADAPTER.decode(EnvelopeMetadataProto.ADAPTER.encode(proto)))
    }

    @Test
    fun `every proto field maps to its payload counterpart`() {
        assertEquals(fullyPopulatedMetadata, fullyPopulatedMetadataProto.toPayload())
    }

    @Test
    fun `absent proto user fields map to null`() {
        val metadata = EnvelopeMetadataProto().toPayload()
        assertNull(metadata.userId)
        assertNull(metadata.email)
        assertNull(metadata.username)
    }

    @Test
    fun `empty string proto fields are preserved`() {
        val metadata = EnvelopeMetadataProto(user_id = "", email = "", username = "").toPayload()
        assertEquals("", metadata.userId)
        assertEquals("", metadata.email)
        assertEquals("", metadata.username)
    }

    @Test
    fun `empty timezone and locale map to null`() {
        val metadata = EnvelopeMetadataProto().toPayload()
        assertNull(metadata.timezoneDescription)
        assertNull(metadata.locale)
    }

    @Test
    fun `empty proto personas map to null`() {
        assertNull(EnvelopeMetadataProto(personas = emptyList()).toPayload().personas)
    }

    @Test
    fun `duplicate personas are deduped on read`() {
        val personas = listOf("payer", "first_day", "payer")
        assertEquals(setOf("payer", "first_day"), EnvelopeMetadataProto(personas = personas).toPayload().personas)
    }

    @Test
    fun `fully populated metadata survives a round trip through the mappers`() {
        assertEquals(fullyPopulatedMetadata, fullyPopulatedMetadata.toProto(fullyPopulatedMutableResourceProto).toPayload())
    }

    @Test
    fun `metadata with no populated fields survives a round trip through the mappers`() {
        assertEquals(EnvelopeMetadata(), EnvelopeMetadata().toProto(MutableResourceProto()).toPayload())
    }

    @Test
    fun `the mutable resource half is carried on the proto`() {
        assertEquals(
            fullyPopulatedMutableResourceProto,
            EnvelopeMetadata().toProto(fullyPopulatedMutableResourceProto).resource,
        )
    }
}
