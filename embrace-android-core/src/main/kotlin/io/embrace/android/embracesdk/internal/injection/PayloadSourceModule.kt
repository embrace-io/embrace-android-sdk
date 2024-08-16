package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSource

/**
 * Modules containing classes that generate the payloads.
 */
public interface PayloadSourceModule {
    public val sessionEnvelopeSource: SessionEnvelopeSource
    public val logEnvelopeSource: LogEnvelopeSource
    public val metadataSource: EnvelopeMetadataSource
    public val deviceArchitecture: DeviceArchitecture
    public val resourceSource: EnvelopeResourceSource
    public val metadataService: MetadataService
    public val hostedSdkVersionInfo: HostedSdkVersionInfo
    public val rnBundleIdTracker: RnBundleIdTracker
}
