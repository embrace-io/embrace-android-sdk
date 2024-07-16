package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.payload.SessionZygote
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

/**
 * Holds the parameters & logic needed to create a final session object that can be
 * sent to the backend.
 */
internal class FinalEnvelopeParams(
    val initial: SessionZygote,
    val endType: SessionSnapshotType,
    val logger: EmbLogger,
    backgroundActivityEnabled: Boolean,
    crashId: String? = null,
) {

    val crashId: String? = when {
        crashId.isNullOrEmpty() -> null
        else -> crashId
    }

    val startNewSession: Boolean = endType.shouldStartNewSession && backgroundActivityEnabled
}
