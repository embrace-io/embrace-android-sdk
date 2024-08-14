package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSource

/**
 * Modules containing classes that generate the payloads.
 */
internal interface PayloadSourceModule {
    val sessionEnvelopeSource: SessionEnvelopeSource
    val logEnvelopeSource: LogEnvelopeSource
    val metadataSource: EnvelopeMetadataSource
    val deviceArchitecture: DeviceArchitecture
    val resourceSource: EnvelopeResourceSource
    val metadataService: MetadataService
    val hostedSdkVersionInfo: HostedSdkVersionInfo
}
