package io.embrace.android.embracesdk.testframework.actions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.fakeEmptyLogEnvelope
import io.embrace.android.embracesdk.fakes.fakeEnvelopeMetadata
import io.embrace.android.embracesdk.fakes.fakeEnvelopeResource
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import java.io.File

internal data class StoredNativeCrashData(
    val sessionMetadata: StoredTelemetryMetadata?,
    val crashMetadata: StoredTelemetryMetadata,
    val cachedCrashEnvelopeMetadata: StoredTelemetryMetadata?,
    val nativeCrash: NativeCrashData,
    val sessionEnvelope: Envelope<SessionPayload>?,
    val cachedCrashEnvelope: Envelope<LogPayload>?,
    val lastHeartbeatMs: Long? = if (sessionMetadata != null) sessionMetadata.timestamp + 1000L else null,
) {

    fun getCrashFile(): File {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val outputDir = StorageLocation.NATIVE.asFile(ctx, FakeEmbLogger()).value.apply {
            mkdirs()
        }
        return File(outputDir, crashMetadata.filename)
    }
}

internal fun createStoredNativeCrashData(
    serializer: PlatformSerializer,
    resourceFixtureName: String,
    crashMetadata: StoredTelemetryMetadata,
    createCrashEnvelope: Boolean = true,
    sessionMetadata: StoredTelemetryMetadata? = null,
    envelopeResource: EnvelopeResource = fakeEnvelopeResource,
    envelopeMetadata: EnvelopeMetadata = fakeEnvelopeMetadata,
): StoredNativeCrashData {
    val nativeCrashData = serializer.fromJson(
        ResourceReader.readResource(resourceFixtureName),
        NativeCrashData::class.java
    )
    return StoredNativeCrashData(
        sessionMetadata = sessionMetadata,
        cachedCrashEnvelopeMetadata = if (createCrashEnvelope) {
            StoredTelemetryMetadata(
                timestamp = crashMetadata.timestamp,
                uuid = nativeCrashData.sessionId,
                processId = crashMetadata.processId,
                complete = false,
                envelopeType = SupportedEnvelopeType.CRASH,
            )
        } else {
            null
        },
        nativeCrash = nativeCrashData,
        sessionEnvelope = if (sessionMetadata != null) {
            fakeIncompleteSessionEnvelope(
                startMs = sessionMetadata.timestamp,
                lastHeartbeatTimeMs = sessionMetadata.timestamp + 1000L,
                sessionId = nativeCrashData.sessionId,
                processIdentifier = sessionMetadata.processId,
                resource = envelopeResource,
                metadata = envelopeMetadata
            )
        } else {
            null
        },
        cachedCrashEnvelope = if (createCrashEnvelope) {
            fakeEmptyLogEnvelope(
                resource = envelopeResource,
                metadata = envelopeMetadata
            )
        } else {
            null
        },
        crashMetadata = crashMetadata,
    )
}
