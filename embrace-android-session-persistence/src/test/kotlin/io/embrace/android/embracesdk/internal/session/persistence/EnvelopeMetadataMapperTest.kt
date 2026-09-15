package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class EnvelopeMetadataMapperTest {

    private companion object {
        private const val ENVELOPE_VERSION = "0.1.0"
        private const val ENVELOPE_TYPE = "spans"
        private const val TIMESTAMP = 1726739283136L
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val SESSION_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        private val partDirectory = SessionPartDirectory(
            timestamp = TIMESTAMP,
            uuid = UUID,
            userSessionId = USER_SESSION_ID,
            sessionPartId = SESSION_PART_ID,
        )
    }

    @Test
    fun `every field maps to its proto counterpart`() {
        assertEquals(fullyPopulatedMetadataProto(), fullyPopulatedMetadata.mapToProto(fullyPopulatedResourceProto))
    }

    @Test
    fun `the format version is stamped on the proto`() {
        assertEquals(FORMAT_VERSION, EnvelopeMetadata().mapToProto().format_version)
    }

    @Test
    fun `the session part identity is carried on the proto`() {
        with(EnvelopeMetadata().mapToProto()) {
            assertEquals(ENVELOPE_VERSION, envelope_version)
            assertEquals(ENVELOPE_TYPE, envelope_type)
            assertEquals(USER_SESSION_ID, user_session_id)
            assertEquals(SESSION_PART_ID, session_part_id)
        }
    }

    @Test
    fun `empty session ids are carried as empty strings`() {
        val anonymous = SessionPartDirectory(timestamp = TIMESTAMP, uuid = UUID)
        with(EnvelopeMetadata().mapToProto(directory = anonymous)) {
            assertEquals("", user_session_id)
            assertEquals("", session_part_id)
        }
    }

    @Test
    fun `the symbol mapping is carried on the proto`() {
        val symbols = SharedLibSymbolMapping(symbols = mapOf("libembrace.so" to "abc-123-uuid"))
        assertEquals(symbols, EnvelopeMetadata().mapToProto(sharedLibSymbolMapping = symbols).shared_lib_symbol_mapping)
        assertNull(EnvelopeMetadata().mapToProto(sharedLibSymbolMapping = null).shared_lib_symbol_mapping)
    }

    @Test
    fun `the resource is carried on the proto`() {
        assertEquals(fullyPopulatedResourceProto, EnvelopeMetadata().mapToProto(fullyPopulatedResourceProto).resource)
    }

    @Test
    fun `null user fields map to absent proto fields`() {
        val proto = EnvelopeMetadata(timezoneDescription = "Europe/London", locale = "en_GB").mapToProto()
        assertNull(proto.user_id)
        assertNull(proto.email)
        assertNull(proto.username)
    }

    @Test
    fun `empty string fields are preserved`() {
        val proto = EnvelopeMetadata(userId = "", email = "", username = "").mapToProto()
        assertEquals("", proto.user_id)
        assertEquals("", proto.email)
        assertEquals("", proto.username)
    }

    @Test
    fun `null timezone and locale map to empty strings`() {
        val proto = EnvelopeMetadata().mapToProto()
        assertEquals("", proto.timezone_description)
        assertEquals("", proto.locale)
    }

    @Test
    fun `null personas map to an empty list`() {
        assertEquals(emptyList<String>(), EnvelopeMetadata(personas = null).mapToProto().personas)
    }

    @Test
    fun `empty personas map to an empty list`() {
        assertEquals(emptyList<String>(), EnvelopeMetadata(personas = emptySet()).mapToProto().personas)
    }

    @Test
    fun `personas are preserved`() {
        val personas = linkedSetOf("payer", "first_day", "persona1")
        assertEquals(personas.toList(), EnvelopeMetadata(personas = personas).mapToProto().personas)
    }

    @Test
    fun `fully populated metadata survives a round trip through the wire format`() {
        val proto = fullyPopulatedMetadata.mapToProto(fullyPopulatedResourceProto)
        assertEquals(proto, SessionMetadata.ADAPTER.decode(SessionMetadata.ADAPTER.encode(proto)))
    }

    @Test
    fun `every proto field maps to its payload counterpart`() {
        assertEquals(fullyPopulatedMetadata, fullyPopulatedMetadataProto().toPayload())
    }

    @Test
    fun `absent proto user fields map to null`() {
        val metadata = SessionMetadata().toPayload()
        assertNull(metadata.userId)
        assertNull(metadata.email)
        assertNull(metadata.username)
    }

    @Test
    fun `empty string proto fields are preserved`() {
        val metadata = SessionMetadata(user_id = "", email = "", username = "").toPayload()
        assertEquals("", metadata.userId)
        assertEquals("", metadata.email)
        assertEquals("", metadata.username)
    }

    @Test
    fun `empty timezone and locale map to null`() {
        val metadata = SessionMetadata().toPayload()
        assertNull(metadata.timezoneDescription)
        assertNull(metadata.locale)
    }

    @Test
    fun `empty proto personas map to null`() {
        assertNull(SessionMetadata(personas = emptyList()).toPayload().personas)
    }

    @Test
    fun `duplicate personas are deduped on read`() {
        val personas = listOf("payer", "first_day", "payer")
        assertEquals(setOf("payer", "first_day"), SessionMetadata(personas = personas).toPayload().personas)
    }

    @Test
    fun `fully populated metadata survives a round trip through the mappers`() {
        assertEquals(fullyPopulatedMetadata, fullyPopulatedMetadata.mapToProto(fullyPopulatedResourceProto).toPayload())
    }

    @Test
    fun `metadata with no populated fields survives a round trip through the mappers`() {
        assertEquals(EnvelopeMetadata(), EnvelopeMetadata().mapToProto().toPayload())
    }

    private fun EnvelopeMetadata.mapToProto(
        resource: ResourceProto = ResourceProto(),
        directory: SessionPartDirectory = partDirectory,
        sharedLibSymbolMapping: SharedLibSymbolMapping? = null,
    ): SessionMetadata = toProto(
        directory = directory,
        envelopeVersion = ENVELOPE_VERSION,
        envelopeType = ENVELOPE_TYPE,
        sharedLibSymbolMapping = sharedLibSymbolMapping,
        resource = resource,
    )
}
