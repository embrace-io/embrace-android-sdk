package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeHostedSdkVersionInfo
import io.embrace.android.embracesdk.fakes.FakeLogEnvelopeSource
import io.embrace.android.embracesdk.fakes.FakeLogPayloadSource
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeRnBundleIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSource
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionPayloadSource
import io.embrace.android.embracesdk.internal.injection.PayloadSourceModule
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.utils.Provider

class FakePayloadSourceModule(
    override val metadataService: MetadataService = FakeMetadataService(),
    override val hostedSdkVersionInfo: HostedSdkVersionInfo = FakeHostedSdkVersionInfo(),
    override val rnBundleIdTracker: FakeRnBundleIdTracker = FakeRnBundleIdTracker(),
    override val payloadResurrectionService: PayloadResurrectionService = FakePayloadResurrectionService(),
    sessionPayloadSource: SessionPayloadSource = FakeSessionPayloadSource(),
    logPayloadSource: LogPayloadSource = FakeLogPayloadSource(),
) : PayloadSourceModule {

    override val resourceSource = FakeEnvelopeResourceSource()
    private val envelopeMetadataSource = FakeEnvelopeMetadataSource()

    override val sessionEnvelopeSource: SessionEnvelopeSource = FakeSessionEnvelopeSource(
        envelopeMetadataSource,
        resourceSource,
        sessionPayloadSource
    )

    override val logEnvelopeSource: FakeLogEnvelopeSource = FakeLogEnvelopeSource(
        envelopeMetadataSource,
        resourceSource,
        logPayloadSource
    )
}

private class FakePayloadResurrectionService : PayloadResurrectionService {

    var resurrectCount: Int = 0

    override fun resurrectOldPayloads(nativeCrashServiceProvider: Provider<NativeCrashService?>) {
        resurrectCount++
    }
}

private class FakeSessionEnvelopeSource(
    private val metadataSource: EnvelopeMetadataSource,
    private val resourceSource: EnvelopeResourceSource,
    private val sessionPayloadSource: SessionPayloadSource,
) : SessionEnvelopeSource {

    override fun getEnvelope(
        endType: SessionSnapshotType,
        startNewSession: Boolean,
        crashId: String?,
    ): Envelope<SessionPayload> {
        return Envelope(
            resourceSource.getEnvelopeResource(),
            metadataSource.getEnvelopeMetadata(),
            "0.1.0",
            "spans",
            sessionPayloadSource.getSessionPayload(endType, startNewSession, crashId)
        )
    }
}
