package io.embrace.android.embracesdk.gating.v2

import io.embrace.android.embracesdk.gating.Sanitizable
import io.embrace.android.embracesdk.internal.payload.SessionPayload

internal class SessionPayloadSanitizer(
    private val payload: SessionPayload,
    @Suppress("UnusedPrivateProperty") private val enabledComponents: Set<String>
) : Sanitizable<SessionPayload> {

    override fun sanitize(): SessionPayload {
        return payload
    }
}
