package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mutable attributes about the app, device, and Embrace SDK internal state
 * that are not tied to a session.
 */
@Serializable
@JsonClass(generateAdapter = true)
data class EnvelopeMetadata(
    @SerialName("user_id")
    @Json(name = "user_id")
    val userId: String? = null,

    @SerialName("email")
    @Json(name = "email")
    val email: String? = null,

    @SerialName("username")
    @Json(name = "username")
    val username: String? = null,

    @SerialName("personas")
    @Json(name = "personas")
    val personas: Set<String>? = null,

    @SerialName("timezone_description")
    @Json(name = "timezone_description")
    val timezoneDescription: String? = null,

    @SerialName("locale")
    @Json(name = "locale")
    val locale: String? = null,
)
