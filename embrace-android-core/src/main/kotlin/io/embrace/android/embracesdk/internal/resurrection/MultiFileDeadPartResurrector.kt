package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.UserSessionRestoreDecision
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Resurrects a dead session part's data persisted in multi-file mode by turning it into a complete
 * session part payload file that can be taken in by the intake service.
 *
 * A native crash recorded for a resurrected part is attached to it straight away, but only sent and
 * deleted once the part has been successfully stored by the intake service. A part that is not
 * stored can then be retried without any of the associated data being prematurely deleted.
 *
 * The resurrection itself is done by [SessionPartResurrector]; this works out what it needs for one
 * part at a time, as [PayloadResurrectionServiceImpl] does across every cached payload at launch.
 * Only single-file mode caches session payloads, so once that mode is removed, these can be merged.
 */
class MultiFileDeadPartResurrector(
    private val resurrector: SessionPartResurrector,
    private val nativeCrashServiceProvider: Provider<NativeCrashService?>,
    private val restoreDecisionProvider: Provider<UserSessionRestoreDecision?>,
) {

    /**
     * Returns what [deadPart] should be delivered as, or null if it does not hold exactly one
     * session part span. [processIdentifier] is the process that persisted it, and
     * [isLastPartOfUserSession] is whether no later part of the same user session is on disk.
     */
    fun resurrect(
        deadPart: Envelope<SessionPartPayload>,
        directory: SessionPartDirectory,
        processIdentifier: String,
        isLastPartOfUserSession: Boolean,
    ): ResurrectedPart? {
        val restoreDecision = restoreDecisionProvider()?.takeIf { it.userSessionId == directory.userSessionId }
        val terminationReason = if (isLastPartOfUserSession) {
            (restoreDecision as? UserSessionRestoreDecision.Terminated)?.reason
        } else {
            null
        }
        val nativeCrashService = nativeCrashServiceProvider()
        var sendNativeCrash: (() -> Unit)? = null
        val envelope = resurrector.resurrect(
            deadPart = deadPart,
            processIdentifier = processIdentifier,
            nativeCrashService = nativeCrashService,
            nativeCrashProvider = { sessionPartId ->
                nativeCrashService?.getNativeCrashes()?.firstOrNull { it.sessionPartId == sessionPartId }
            },
            onNativeCrashProcessed = { nativeCrash -> nativeCrashService?.deleteNativeCrash(nativeCrash) },
            userSessionTerminationReason = terminationReason,
            isBackgroundOnly = restoreDecision?.backgroundOnly == true,
            sendNativeCrash = { send -> sendNativeCrash = send },
        ) ?: return null
        return ResurrectedPart(envelope) { sendNativeCrash?.invoke() }
    }

    /**
     * A resurrected session part: the [envelope] to deliver, and the work to do once it has been
     * stored, which sends and deletes its native crash.
     */
    class ResurrectedPart(
        val envelope: Envelope<SessionPartPayload>,
        val afterStored: () -> Unit,
    )
}
