package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.instrumentation.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider

internal class SdkStateApiDelegate(
    private val deviceIdentifier: Lazy<String>,
    private val sessionIdsProvider: SessionIdsProvider,
    private val crashVerifier: LastRunCrashVerifier,
) : SdkStateApi {

    override val isStarted: Boolean = true

    override val deviceId: String
        get() = deviceIdentifier.value

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
