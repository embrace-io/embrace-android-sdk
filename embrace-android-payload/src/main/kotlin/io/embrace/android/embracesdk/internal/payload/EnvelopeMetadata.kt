package io.embrace.android.embracesdk.internal.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mutable attributes about the app, device, and Embrace SDK internal state
 * that are not tied to a session.
 */
@Serializable
data class EnvelopeMetadata(
    @SerialName("user_id")
    val userId: String? = null,

    @SerialName("email")
    val email: String? = null,

    @SerialName("username")
    val username: String? = null,

    @SerialName("personas")
    val personas: Set<String>? = null,

    @SerialName("timezone_description")
    val timezoneDescription: String? = null,

    @SerialName("locale")
    val locale: String? = null,
)
