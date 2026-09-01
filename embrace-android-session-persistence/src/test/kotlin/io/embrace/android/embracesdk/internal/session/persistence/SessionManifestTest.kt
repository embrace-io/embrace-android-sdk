package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class SessionManifestTest {

    @Test
    fun `fully populated manifest round-trips`() {
        val manifest = SessionManifest(
            format_version = 1,
            envelope_version = "0.1.0",
            envelope_type = "spans",
            user_session_id = "user-session-id",
            session_part_id = "session-part-id",
            shared_lib_symbol_mapping = SharedLibSymbolMapping(
                symbols = mapOf("abc-123-uuid" to "libembrace.so"),
            ),
            resource = ImmutableResourceProto(
                app_version = "1.2.3",
                app_framework = ImmutableResourceProto.AppFramework.NATIVE,
                device_soc_model = "Tensor G3",
            ),
        )

        assertEquals(manifest, SessionManifest.ADAPTER.decode(SessionManifest.ADAPTER.encode(manifest)))
    }

    @Test
    fun `absent symbol mapping stays distinct from an empty one`() {
        val absent = SessionManifest(shared_lib_symbol_mapping = null)
        val empty = SessionManifest(shared_lib_symbol_mapping = SharedLibSymbolMapping())

        assertNull(SessionManifest.ADAPTER.decode(SessionManifest.ADAPTER.encode(absent)).shared_lib_symbol_mapping)
        assertEquals(
            SharedLibSymbolMapping(),
            SessionManifest.ADAPTER.decode(SessionManifest.ADAPTER.encode(empty)).shared_lib_symbol_mapping,
        )
    }

    @Test
    fun `absent resource stays distinct from an empty resource`() {
        val absent = SessionManifest(resource = null)
        val empty = SessionManifest(resource = ImmutableResourceProto())

        assertNull(SessionManifest.ADAPTER.decode(SessionManifest.ADAPTER.encode(absent)).resource)
        assertEquals(
            ImmutableResourceProto(),
            SessionManifest.ADAPTER.decode(SessionManifest.ADAPTER.encode(empty)).resource,
        )
    }

    @Test
    fun `format version survives on an otherwise empty manifest`() {
        val manifest = SessionManifest(format_version = 3)
        assertEquals(3, SessionManifest.ADAPTER.decode(SessionManifest.ADAPTER.encode(manifest)).format_version)
    }
}
