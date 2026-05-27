package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Information about the user of the app, provided by the developer performing the integration.
 */
@Serializable
@JsonClass(generateAdapter = true)
data class UserInfo(

    @SerialName("id")
    @Json(name = "id")
    val userId: String? = null,

    @SerialName("em")
    @Json(name = "em")
    val email: String? = null,

    @SerialName("un")
    @Json(name = "un")
    val username: String? = null,

    @SerialName("per")
    @Json(name = "per")
    val personas: Set<String>? = null,
)
