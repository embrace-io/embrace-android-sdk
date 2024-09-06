package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Information about the user of the app, provided by the developer performing the integration.
 */
@JsonClass(generateAdapter = true)
data class UserInfo(

    @Json(name = "id")
    val userId: String? = null,

    @Json(name = "em")
    val email: String? = null,

    @Json(name = "un")
    val username: String? = null,

    @Json(name = "per")
    val personas: Set<String>? = null
)
