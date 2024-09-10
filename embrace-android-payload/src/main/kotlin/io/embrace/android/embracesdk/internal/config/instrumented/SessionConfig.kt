package io.embrace.android.embracesdk.internal.config.instrumented

import com.squareup.moshi.Json

/**
 * Declares metadata about the app project
 */
@Suppress("FunctionOnlyReturningConstant")
@Swazzled
object SessionConfig {

    /**
     * A whitelist of session components (i.e. Breadcrumbs, Session properties, etc) that should be
     * included in the session payload. The presence of this property denotes that the gating
     * feature is enabled.
     *
     * sdk_config.session.components
     */
    @Json(name = "components")
    fun getSessionComponents(): Set<String>? = null

    /**
     * A list of events (crashes, errors, etc) allowed to send a full session payload if the
     * gating feature is enabled.
     *
     * sdk_config.session.send_full_for
     */
    @Json(name = "send_full_for")
    fun getFullSessionEvents(): Set<String> = emptySet()
}
