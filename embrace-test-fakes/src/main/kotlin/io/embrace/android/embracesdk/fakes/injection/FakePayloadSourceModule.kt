package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeHostedSdkVersionInfo
import io.embrace.android.embracesdk.fakes.FakeLogEnvelopeSource
import io.embrace.android.embracesdk.fakes.FakeLogPayloadSource
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeRnBundleIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionPartPayloadSource
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSource
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartPayloadSource
import io.embrace.android.embracesdk.internal.injection.PayloadSourceModule
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType
import io.embrace.android.embracesdk.internal.utils.Provider

class FakePayloadSourceModule(
    override val metadataService: MetadataService = FakeMetadataService(),
    override val hostedSdkVersionInfo: HostedSdkVersionInfo = FakeHostedSdkVersionInfo(),
    override val rnBundleIdTracker: FakeRnBundleIdTracker = FakeRnBundleIdTracker(),
    override val payloadResurrectionService: PayloadResurrectionService = FakePayloadResurrectionService(),
    partPayloadSource: SessionPartPayloadSource = FakeSessionPartPayloadSource(),
    logPayloadSource: LogPayloadSource = FakeLogPayloadSource(),
) : PayloadSourceModule {

    override val resourceSource = FakeEnvelopeResourceSource()
    private val envelopeMetadataSource = FakeEnvelopeMetadataSource()

    override val sessionPartEnvelopeSource: SessionPartEnvelopeSource = FakeSessionPartEnvelopeSource(
        envelopeMetadataSource,
        resourceSource,
        partPayloadSource
    )

    override val logEnvelopeSource: FakeLogEnvelopeSource = FakeLogEnvelopeSource(
        envelopeMetadataSource,
        resourceSource,
        logPayloadSource
    )
}

private class FakePayloadResurrectionService : PayloadResurrectionService {

    var resurrectCount: Int = 0
    private val completionListeners = mutableListOf<() -> Unit>()

    override fun addResurrectionCompleteListener(listener: () -> Unit) {
        completionListeners.add(listener)
    }

    override fun resurrectOldPayloads(nativeCrashServiceProvider: Provider<NativeCrashService?>) {
        resurrectCount++
        completionListeners.forEach { it() }
    }
}

private class FakeSessionPartEnvelopeSource(
    private val metadataSource: EnvelopeMetadataSource,
    private val resourceSource: EnvelopeResourceSource,
    private val partPayloadSource: SessionPartPayloadSource,
) : SessionPartEnvelopeSource {

    override fun getEnvelope(
        endType: SessionPartSnapshotType,
        startNewSession: Boolean,
        crashId: String?,
    ): Envelope<SessionPartPayload> {
        return Envelope(
            resourceSource.getEnvelopeResource(),
            metadataSource.getEnvelopeMetadata(),
            "0.1.0",
            "spans",
            partPayloadSource.getSessionPartPayload(endType, startNewSession, crashId)
        )
    }
}
