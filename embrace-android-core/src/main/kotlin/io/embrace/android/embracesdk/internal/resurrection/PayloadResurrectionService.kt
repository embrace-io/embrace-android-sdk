package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.session.UserSessionRestoreDecision
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * This service finds cached payloads from previous process launches & sends them to the
 * [IntakeService]. If the [IntakeService] accepts the cached payload, this service will then
 * delete the cached payload.
 */
interface PayloadResurrectionService {

    /**
     * Registers a listener that will be called when [resurrectOldPayloads] ends with [ResurrectionOutcome.COMPLETE], at which time
     * all tombstones are processed and deleted. It isn't called for a pass that ends with [ResurrectionOutcome.INTERRUPTED].
     */
    fun addResurrectionCompleteListener(listener: () -> Unit)

    /**
     * Resurrects any payloads that were cached in a previous process & sends them to the [IntakeService], and returns how the
     * pass ended. Only after a [ResurrectionOutcome.COMPLETE] pass can payloads recorded since be delivered: one that is
     * [ResurrectionOutcome.INTERRUPTED] has left payloads on disk that must go out first, on the next launch.
     *
     * [userSessionRestoreDecisionProvider] provides this SDK instance's decision about whether to continue the persisted user session
     * at startup or terminate it implicitly. The provider returning null implies that there was no persisted user session at startup.
     *
     * [pendingSessionPartIdsProvider] provides the IDs of session parts still on disk for the session part reader to resurrect.
     */
    fun resurrectOldPayloads(
        nativeCrashServiceProvider: Provider<NativeCrashService?>,
        userSessionRestoreDecisionProvider: Provider<UserSessionRestoreDecision?> = { null },
        pendingSessionPartIdsProvider: Provider<Set<String>> = { emptySet() },
    ): ResurrectionOutcome
}
