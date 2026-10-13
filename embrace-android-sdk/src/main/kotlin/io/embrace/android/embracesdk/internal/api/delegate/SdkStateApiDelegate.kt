package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper

internal class SdkStateApiDelegate(
    lazyBootstrapper: Lazy<ModuleInitBootstrapper>,
) : SdkStateApi {

    private val bootstrapper by lazyBootstrapper

    private val sessionIdsProvider get() = bootstrapper.userSessionOrchestrationModule.sessionIdsProvider
    private val deviceIdentifier get() = bootstrapper.configService.deviceId
    private val crashVerifier get() = bootstrapper.featureModule.lastRunCrashVerifier

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
