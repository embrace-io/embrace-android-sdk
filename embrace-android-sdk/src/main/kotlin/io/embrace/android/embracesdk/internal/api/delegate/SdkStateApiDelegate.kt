package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.injection.ModuleGraph

internal class SdkStateApiDelegate(
    private val moduleGraph: ModuleGraph,
) : SdkStateApi {

    private val sessionIdsProvider get() = moduleGraph.userSessionOrchestrationModule.sessionIdsProvider
    private val deviceIdentifier get() = moduleGraph.configService.deviceId
    private val crashVerifier get() = moduleGraph.featureModule.lastRunCrashVerifier

    override val isStarted: Boolean = true

    override val deviceId: String
        get() = deviceIdentifier

    override val currentUserSessionId: String
        get() = sessionIdsProvider.getCurrentUserSessionId()

    override val lastRunEndState: LastRunEndState
        get() {
            return if (isStarted) {
                if (crashVerifier.didLastRunCrash()) {
                    LastRunEndState.CRASH
                } else {
                    LastRunEndState.CLEAN_EXIT
                }
            } else {
                LastRunEndState.INVALID
            }
        }
}
