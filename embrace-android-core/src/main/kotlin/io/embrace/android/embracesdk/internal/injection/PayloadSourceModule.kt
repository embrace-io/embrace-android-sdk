package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionService

/**
 * Modules containing classes that generate the payloads.
 */
interface PayloadSourceModule {
    val sessionEnvelopeSource: SessionEnvelopeSource
    val logEnvelopeSource: LogEnvelopeSource
    val metadataService: MetadataService
    val hostedSdkVersionInfo: HostedSdkVersionInfo
    val rnBundleIdTracker: RnBundleIdTracker
    val payloadResurrectionService: PayloadResurrectionService?
}
