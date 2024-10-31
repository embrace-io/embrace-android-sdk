package io.embrace.android.embracesdk.internal.gating

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload

interface GatingService {

    /**
     * Sanitizes a v2 session message before sending it to the backend based on the Gating configuration.
     * Breadcrumbs, session properties, ANRs, logs, etc can be removed from the session payload.
     * This method should be called before send the session message to the ApiClient class.
     *
     * @param hasCrash if any crash occurred
     * @param envelope to be sanitized
     */
    fun gateSessionEnvelope(
        hasCrash: Boolean,
        envelope: Envelope<SessionPayload>
    ): Envelope<SessionPayload>
}
