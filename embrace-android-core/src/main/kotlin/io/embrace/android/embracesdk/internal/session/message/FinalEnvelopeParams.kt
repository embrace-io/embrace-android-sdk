package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

/**
 * Holds the parameters & logic needed to create a final session object that can be
 * sent to the backend.
 */
class FinalEnvelopeParams(
    val initial: SessionZygote,
    val endType: SessionSnapshotType,
    val logger: EmbLogger,
    continueMonitoring: Boolean,
    crashId: String? = null,
) {

    val crashId: String? = when {
        crashId.isNullOrEmpty() -> null
        else -> crashId
    }

    val startNewSession: Boolean = continueMonitoring
}
