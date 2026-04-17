package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject

internal class SdkStateApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : SdkStateApi {

    private val sessionIdProvider by embraceImplInject(sdkCallChecker) {
        bootstrapper.userSessionOrchestrationModule.sessionIdProvider
    }
    private val deviceIdentifier by embraceImplInject(sdkCallChecker) {
        bootstrapper.configService.deviceId
    }
    private val crashVerifier by embraceImplInject(sdkCallChecker) { bootstrapper.featureModule.lastRunCrashVerifier }

    override val isStarted: Boolean
        get() = sdkCallChecker.started.get()

    override val deviceId: String
        get() {
            return if (sdkCallChecker.check("get_device_id")) {
                deviceIdentifier ?: ""
            } else {
                ""
            }
        }

    override val currentUserSessionId: String?
        get() {
            val localSessionIdProvider = sessionIdProvider
            if (localSessionIdProvider != null && sdkCallChecker.check("get_current_session_id")) {
                return localSessionIdProvider.getCurrentUserSessionId()
            }
            return null
        }

    override val lastRunEndState: LastRunEndState
        get() {
            return if (isStarted && crashVerifier != null) {
                if (crashVerifier?.didLastRunCrash() == true) {
                    LastRunEndState.CRASH
                } else {
                    LastRunEndState.CLEAN_EXIT
                }
            } else {
                LastRunEndState.INVALID
            }
        }
}
