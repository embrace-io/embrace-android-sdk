package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionService

/**
 * Modules containing classes that generate the payloads.
 */
interface PayloadSourceModule {
    val sessionEnvelopeSource: SessionEnvelopeSource
    val logEnvelopeSource: LogEnvelopeSource
    val metadataSource: EnvelopeMetadataSource
    val deviceArchitecture: DeviceArchitecture
    val resourceSource: EnvelopeResourceSource
    val metadataService: MetadataService
    val hostedSdkVersionInfo: HostedSdkVersionInfo
    val rnBundleIdTracker: RnBundleIdTracker
    val payloadResurrectionService: PayloadResurrectionService?
}
