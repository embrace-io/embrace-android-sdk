package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType

/**
 * Holds the parameters & logic needed to create a final session object that can be
 * sent to the backend.
 */
class FinalEnvelopeParams(
    val initial: SessionPartToken,
    val endType: SessionPartSnapshotType,
    val logger: InternalLogger,
    continueMonitoring: Boolean,
    crashId: String? = null,
) {

    val crashId: String? = when {
        crashId.isNullOrEmpty() -> null
        else -> crashId
    }

    val startNewSession: Boolean = continueMonitoring
}
