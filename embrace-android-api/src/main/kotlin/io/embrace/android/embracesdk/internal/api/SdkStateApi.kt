package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.annotation.InternalApi

@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(InternalApi::class)
public interface SdkStateApi {

    /**
     * Whether or not the SDK has been started.
     *
     * @return true if the SDK is started, false otherwise
     */
    public val isStarted: Boolean

    public val deviceId: String

    public val currentUserSessionId: String?

    public val lastRunEndState: LastRunEndState
}
