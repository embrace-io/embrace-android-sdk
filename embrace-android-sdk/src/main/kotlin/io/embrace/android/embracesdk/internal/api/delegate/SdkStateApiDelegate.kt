package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper

internal class SdkStateApiDelegate(
    lazyBootstrapper: Lazy<ModuleInitBootstrapper>,
    private val sdkCallChecker: SdkCallChecker,
) : SdkStateApi {

    private val bootstrapper by lazyBootstrapper

    private val sessionIdsProvider get() = bootstrapper.userSessionOrchestrationModule.sessionIdsProvider
    private val deviceIdentifier get() = bootstrapper.configService.deviceId
    private val crashVerifier get() = bootstrapper.featureModule.lastRunCrashVerifier

    override val isStarted: Boolean
        get() = sdkCallChecker.started.get()

    override val deviceId: String
        get() {
            return if (sdkCallChecker.check("get_device_id")) {
                deviceIdentifier
            } else {
                ""
            }
        }

    override val currentUserSessionId: String?
        get() {
            if (sdkCallChecker.check("get_current_session_id")) {
                return sessionIdsProvider.getCurrentUserSessionId()
            }
            return null
        }

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
