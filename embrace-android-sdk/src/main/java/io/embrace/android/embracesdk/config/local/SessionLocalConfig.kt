package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

/**
 * Represents the session configuration element specified in the Embrace config file.
 */
internal class SessionLocalConfig(

    /**
     * Specify a maximum time before a session is allowed to exist before it is ended.
     */
    @SerializedName("max_session_seconds")
    val maxSessionSeconds: Int? = null,

    /**
     * End session messages are sent asynchronously.
     */
    @SerializedName("async_end")
    val asyncEnd: Boolean? = null,

    /**
     * A whitelist of session components (i.e. Breadcrumbs, Session properties, etc) that should be
     * included in the session payload. The presence of this property denotes that the gating
     * feature is enabled.
     */
    @SerializedName("components")
    val sessionComponents: Set<String>? = null,

    /**
     * A list of events (crashes, errors, etc) allowed to send a full session payload if the
     * gating feature is enabled.
     */
    @SerializedName("send_full_for")
    val fullSessionEvents: Set<String>? = null,

    /**
     * Local/Internal logs with ERROR severity are going to be captured as part of our session payload tp monitor potential issues
     */
    @SerializedName("error_log_strict_mode")
    val sessionEnableErrorLogStrictMode: Boolean? = null
)
