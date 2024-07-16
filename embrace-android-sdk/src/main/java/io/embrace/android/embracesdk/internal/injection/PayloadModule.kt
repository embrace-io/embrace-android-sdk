package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.capture.envelope.session.SessionEnvelopeSource

/**
 * Modules containing classes that generate the payloads.
 */
internal interface PayloadModule {
    val sessionEnvelopeSource: SessionEnvelopeSource
    val logEnvelopeSource: LogEnvelopeSource
}
