package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.storage.SdkDir
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that the orphan sweeper's allow-lists in embrace-android-storage accept every name the telemetry writers
 * produce, so a change to either writer's format can't silently sweep live telemetry.
 */
internal class SweptEntryNamesTest {

    private val payloadDirs = listOf(
        SdkDir.TELEMETRY_READY,
        SdkDir.TELEMETRY_PENDING_SNAPSHOTS,
        SdkDir.TELEMETRY_PENDING_NATIVE_CRASHES,
        SdkDir.TELEMETRY_LOG_ENVELOPES,
    )

    @Test
    fun `every payload filename is allowed`() {
        val names = SupportedEnvelopeType.entries.flatMap { envelopeType ->
            PayloadType.entries.flatMap { payloadType ->
                listOf("", "user").flatMap { userSessionId ->
                    listOf(true, false).map { complete ->
                        StoredTelemetryMetadata(
                            timestamp = 1_700_000_000_000,
                            uuid = "uuid",
                            processIdentifier = "pid",
                            envelopeType = envelopeType,
                            complete = complete,
                            payloadType = payloadType,
                            userSessionId = userSessionId,
                            sessionPartId = userSessionId,
                        ).filename
                    }
                }
            }
        } + V1_PAYLOAD
        names.forEach { name ->
            assertTrue(StoredTelemetryMetadata.fromFilename(name).isSuccess)
            payloadDirs.forEach { dir ->
                assertTrue("$name rejected in $dir", dir.allowedEntries(listOf(name)))
            }
        }
    }

    @Test
    fun `every session part dir and file is allowed`() {
        SessionPartDirectoryVersion.entries.forEach { version ->
            listOf("", "user").forEach { id ->
                val dirName = SessionPartDirectory(1_700_000_000_000, "uuid", id, id, version).dirName
                val allowed = SdkDir.TELEMETRY_PENDING_SESSION_PARTS.allowedEntries
                assertTrue("$dirName rejected", allowed(listOf(dirName)))
                SessionPartFile.entries.forEach { file ->
                    assertTrue("${file.fileName} rejected", allowed(listOf(dirName, file.fileName)))
                }
            }
        }
    }

    private companion object {
        // no longer written, but still read from older installs
        const val V1_PAYLOAD = "p3_1700000000000_uuid_pid_true_session_v1.json"
    }
}
