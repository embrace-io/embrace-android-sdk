package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Mutable attributes about the app, device, and Embrace SDK internal state
 * that are not tied to a session.
 */
@JsonClass(generateAdapter = true)
data class EnvelopeMetadata(
    @Json(name = "user_id")
    val userId: String? = null,

    @Json(name = "email")
    val email: String? = null,

    @Json(name = "username")
    val username: String? = null,

    @Json(name = "personas")
    val personas: Set<String>? = null,

    @Json(name = "timezone_description")
    val timezoneDescription: String? = null,

    @Json(name = "locale")
    val locale: String? = null,
)
