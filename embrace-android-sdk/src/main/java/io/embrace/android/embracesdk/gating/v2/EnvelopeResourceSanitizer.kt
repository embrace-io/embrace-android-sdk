package io.embrace.android.embracesdk.gating.v2

import io.embrace.android.embracesdk.gating.Sanitizable
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

internal class EnvelopeResourceSanitizer(
    private val resource: EnvelopeResource,
    @Suppress("UnusedPrivateProperty") private val enabledComponents: Set<String>
) : Sanitizable<EnvelopeResource> {

    override fun sanitize(): EnvelopeResource {
        return resource
    }
}
