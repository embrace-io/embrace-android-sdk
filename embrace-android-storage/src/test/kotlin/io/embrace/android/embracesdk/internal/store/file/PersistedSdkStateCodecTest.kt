package io.embrace.android.embracesdk.internal.store.file

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoWriter
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PersistedSdkStateCodecTest {

    private val emptyState = PersistedSdkState(deviceId = "device", installTimestampMs = 1000L)

    private val fullState = PersistedSdkState(
        deviceId = "device",
        installTimestampMs = 1000L,
        userId = "",
        userEmail = "user@example.com",
        userName = "user-name",
        userPersonas = setOf("payer", "first_day"),
        sessionProperties = mapOf("key" to "value"),
        userSession = UserSessionMetadata.Classified(2000L, "session-id", 3L, 4L, 5L, 6, 7000L, true),
        hostedSdkVersion = "1.2.3",
        hostedPlatformVersion = "0.79.0",
        unityBuildIdNumber = "unity-build",
        javaScriptPatchNumber = "patch",
        rnBundleUrl = "assets://index.android.bundle",
        rnBundleId = "bundle-id",
    )

    @Test
    fun `state round trips`() {
        listOf(
            emptyState,
            emptyState.copy(userPersonas = emptySet()),
            emptyState.copy(sessionProperties = mapOf("big" to "x".repeat(70_000))),
            fullState,
        ).forEach { assertEquals(it, decode(encode(it))) }
        assertTrue(encode(fullState).size < 1024)
    }

    @Test
    fun `newer file keeps known values and its unknown fields survive a rewrite`() {
        val newer = encode(fullState)
        ProtoAdapter.STRING.encodeWithTag(ProtoWriter(newer), 100, "from a newer SDK")
        val decoded = PersistedSdkStateCodec.decode(newer)
        assertEquals(fullState, decoded.toPersistedSdkState("device", 1000L))

        val rewritten = encode(fullState.copy(userId = "changed"), base = decoded)
        assertEquals(decoded.unknownFields, PersistedSdkStateCodec.decode(rewritten).unknownFields)
    }

    @Test
    fun `unknown fields in nested messages survive a rewrite`() {
        val newer = fullState.toProto(PersistedSdkStateProto()).let {
            it.copy(
                user_personas = it.user_personas?.copy(unknownFields = unknownField("persona extra")),
                user_session = it.user_session?.copy(unknownFields = unknownField("session extra")),
            )
        }
        val decoded = PersistedSdkStateCodec.decode(Buffer().also { PersistedSdkStateCodec.encode(newer, it) })

        val rewritten = PersistedSdkStateCodec.decode(encode(fullState.copy(userId = "changed"), base = decoded))
        assertEquals(unknownField("persona extra"), rewritten.user_personas?.unknownFields)
        assertEquals(unknownField("session extra"), rewritten.user_session?.unknownFields)
    }

    @Test
    fun `unknown user session fields are dropped when the user session changes`() {
        val base = fullState.toProto(PersistedSdkStateProto()).let {
            it.copy(user_session = it.user_session?.copy(unknownFields = unknownField("session extra")))
        }
        val newSession = UserSessionMetadata.Classified(8000L, "new-session-id", 4L, 4L, 5L, 0, 8000L, false)
        val rewritten = PersistedSdkStateCodec.decode(encode(fullState.copy(userSession = newSession), base = base))
        assertEquals(ByteString.EMPTY, rewritten.user_session?.unknownFields)
    }

    @Test
    fun `bad bytes throw`() {
        val full = encode(fullState)
        listOf(
            Buffer(),
            Buffer().write("ORDS".encodeUtf8()),
            Buffer().write(full.readByteString(full.size - 5)),
        ).forEach { assertThrows(IOException::class.java) { PersistedSdkStateCodec.decode(it) } }
    }

    private fun encode(state: PersistedSdkState, base: PersistedSdkStateProto = PersistedSdkStateProto()) =
        Buffer().also { PersistedSdkStateCodec.encode(state.toProto(base), it) }

    private fun unknownField(value: String): ByteString =
        Buffer().also { ProtoAdapter.STRING.encodeWithTag(ProtoWriter(it), 100, value) }.readByteString()

    private fun decode(buffer: Buffer): PersistedSdkState =
        PersistedSdkStateCodec.decode(buffer).toPersistedSdkState("device", 1000L)
}
