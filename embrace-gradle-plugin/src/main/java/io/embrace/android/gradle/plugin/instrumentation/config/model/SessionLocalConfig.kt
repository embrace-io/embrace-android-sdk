package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

/**
 * Represents the session configuration element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
data class SessionLocalConfig(

    /**
     * A whitelist of session components (i.e. Breadcrumbs, Session properties, etc) that should be
     * included in the session payload. The presence of this property denotes that the gating
     * feature is enabled.
     */
    @Json(name = "components")
    val sessionComponents: List<String>? = null,

    /**
     * A list of events (crashes, errors, etc) allowed to send a full session payload if the
     * gating feature is enabled.
     */
    @Json(name = "send_full_for")
    val fullSessionEvents: List<String>? = null
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
