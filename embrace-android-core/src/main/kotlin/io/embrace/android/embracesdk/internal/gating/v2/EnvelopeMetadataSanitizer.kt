package io.embrace.android.embracesdk.internal.gating.v2

import io.embrace.android.embracesdk.internal.gating.Sanitizable
import io.embrace.android.embracesdk.internal.gating.SessionGatingKeys
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata

internal class EnvelopeMetadataSanitizer(
    private val metadata: EnvelopeMetadata,
    private val enabledComponents: Set<String>,
) : Sanitizable<EnvelopeMetadata> {

    override fun sanitize(): EnvelopeMetadata {
        if (!shouldSendUserPersonas()) {
            return metadata.copy(personas = null)
        }
        return metadata
    }

    private fun shouldSendUserPersonas() =
        enabledComponents.contains(SessionGatingKeys.USER_PERSONAS)
}
