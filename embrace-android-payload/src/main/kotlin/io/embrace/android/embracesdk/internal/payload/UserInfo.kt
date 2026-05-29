package io.embrace.android.embracesdk.internal.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Information about the user of the app, provided by the developer performing the integration.
 */
@Serializable
data class UserInfo(

    @SerialName("id")
    val userId: String? = null,

    @SerialName("em")
    val email: String? = null,

    @SerialName("un")
    val username: String? = null,

    @SerialName("per")
    val personas: Set<String>? = null,
)
