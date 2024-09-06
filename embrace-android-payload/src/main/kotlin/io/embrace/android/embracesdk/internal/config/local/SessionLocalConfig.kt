package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents the session configuration element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
class SessionLocalConfig(

    /**
     * A whitelist of session components (i.e. Breadcrumbs, Session properties, etc) that should be
     * included in the session payload. The presence of this property denotes that the gating
     * feature is enabled.
     */
    @Json(name = "components") val sessionComponents: Set<String>? = null,

    /**
     * A list of events (crashes, errors, etc) allowed to send a full session payload if the
     * gating feature is enabled.
     */
    @Json(name = "send_full_for") val fullSessionEvents: Set<String>? = null
)
