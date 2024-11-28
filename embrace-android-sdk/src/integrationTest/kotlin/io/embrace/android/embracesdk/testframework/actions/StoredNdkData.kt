package io.embrace.android.embracesdk.testframework.actions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import java.io.File

internal data class StoredNativeCrashData(
    val sessionMetadata: StoredTelemetryMetadata,
    val crashMetadata: StoredTelemetryMetadata,
    val nativeCrash: NativeCrashData,
    val sessionEnvelope: Envelope<SessionPayload>,
    val lastHeartbeatMs: Long = sessionMetadata.timestamp + 1000L,
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
    sessionMetadata: StoredTelemetryMetadata,
    crashMetadata: StoredTelemetryMetadata,
    resourceFixtureName: String,
): StoredNativeCrashData {
    val nativeCrashData = serializer.fromJson(
        ResourceReader.readResource(resourceFixtureName),
        NativeCrashData::class.java
    )
    return StoredNativeCrashData(
        sessionMetadata = sessionMetadata,
        nativeCrash = nativeCrashData,
        sessionEnvelope = fakeIncompleteSessionEnvelope(
            startMs = sessionMetadata.timestamp,
            lastHeartbeatTimeMs = sessionMetadata.timestamp + 1000L,
            sessionId = nativeCrashData.sessionId
        ),
        crashMetadata = crashMetadata
    )
}
