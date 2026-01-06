package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.session.SessionToken
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

/**
 * Holds the parameters & logic needed to create a final session object that can be
 * sent to the backend.
 */
class FinalEnvelopeParams(
    val initial: SessionToken,
    val endType: SessionSnapshotType,
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
