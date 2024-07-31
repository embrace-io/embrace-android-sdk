package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.SessionZygote
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

/**
 * Holds the parameters & logic needed to create a final session object that can be
 * sent to the backend.
 */
public class FinalEnvelopeParams(
    public val initial: SessionZygote,
    public val endType: SessionSnapshotType,
    public val logger: EmbLogger,
    continueMonitoring: Boolean,
    crashId: String? = null,
) {

    public val crashId: String? = when {
        crashId.isNullOrEmpty() -> null
        else -> crashId
    }

    public val startNewSession: Boolean = continueMonitoring
}
