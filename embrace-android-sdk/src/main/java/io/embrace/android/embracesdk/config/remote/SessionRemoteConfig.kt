package io.embrace.android.embracesdk.config.remote

import com.google.gson.annotations.SerializedName

/**
 * It serves as a session controller components. It determines if session may be ended in
 * the background. It also determines which components will be sent as part of the
 * session payload. This feature may be enabled/disabled.
 */
internal data class SessionRemoteConfig(
    @SerializedName("enable")
    val isEnabled: Boolean? = null,

    @SerializedName("async_end")
    val endAsync: Boolean? = null,

    /**
     * A list of session components (i.e. Breadcrumbs, Session properties, etc) that will be
     * included in the session payload. If components list exists, the services should restrict
     * the data that is provided to the session.
     */
    @SerializedName("components")
    val sessionComponents: Set<String>? = null,

    /**
     * A list of session components allowed to send a full session payload (only if "components"
     * exists)
     */
    @SerializedName("send_full_for")
    val fullSessionEvents: Set<String>? = null
)
