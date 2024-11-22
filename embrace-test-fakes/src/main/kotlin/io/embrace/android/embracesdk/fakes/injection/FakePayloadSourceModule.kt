package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeLogEnvelopeSource
import io.embrace.android.embracesdk.fakes.FakeLogPayloadSource
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePayloadResurrectionService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeRnBundleIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionEnvelopeSource
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSource
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionPayloadSource
import io.embrace.android.embracesdk.internal.injection.PayloadSourceModule
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionService

class FakePayloadSourceModule(
    override val metadataService: MetadataService = FakeMetadataService(),
    override val deviceArchitecture: DeviceArchitecture = FakeDeviceArchitecture(),
    override val hostedSdkVersionInfo: HostedSdkVersionInfo = HostedSdkVersionInfo(FakePreferenceService()),
    override val resourceSource: FakeEnvelopeResourceSource = FakeEnvelopeResourceSource(),
    override val metadataSource: FakeEnvelopeMetadataSource = FakeEnvelopeMetadataSource(),
    override val rnBundleIdTracker: FakeRnBundleIdTracker = FakeRnBundleIdTracker(),
    override val payloadResurrectionService: PayloadResurrectionService = FakePayloadResurrectionService(),
    sessionPayloadSource: SessionPayloadSource = FakeSessionPayloadSource(),
    logPayloadSource: LogPayloadSource = FakeLogPayloadSource(),
) : PayloadSourceModule {

    private val envelopeResourceSource = FakeEnvelopeResourceSource()
    private val envelopeMetadataSource = FakeEnvelopeMetadataSource()

    override val sessionEnvelopeSource: SessionEnvelopeSource = FakeSessionEnvelopeSource(
        envelopeMetadataSource,
        envelopeResourceSource,
        sessionPayloadSource
    )

    override val logEnvelopeSource: FakeLogEnvelopeSource = FakeLogEnvelopeSource(
        envelopeMetadataSource,
        envelopeResourceSource,
        logPayloadSource
    )
}
