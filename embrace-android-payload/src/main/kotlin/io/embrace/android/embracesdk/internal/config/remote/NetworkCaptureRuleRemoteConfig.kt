package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val NETWORK_BODY_RULE_DEFAULT_MAX_COUNT = 5
private const val NETWORK_BODY_RULE_DEFAULT_MAX_SIZE_BYTES = 102400L

/**
 * Criteria to determine if a network body call should be captured or not.
 */
@Serializable
@JsonClass(generateAdapter = true)
data class NetworkCaptureRuleRemoteConfig(

    /**
     * Rule id
     */
    @SerialName("id")
    @Json(name = "id")
    val id: String,

    /**
     * Duration of the network call in milliseconds. Disregard if it is less than 5000ms.
     */
    @SerialName("duration")
    @Json(name = "duration")
    val duration: Long?,

    /**
     * Http method to be captured.
     */
    @SerialName("method")
    @Json(name = "method")
    val method: String,

    /**
     * Url regex. If the url matches this the call must be captured.
     */
    @SerialName("url")
    @Json(name = "url")
    val urlRegex: String,

    /**
     * Remaining milliseconds until the rule expires.
     */
    @SerialName("expires_in")
    @Json(name = "expires_in")
    val expiresIn: Long = 0,

    /**
     * Maximum size of the network body. The data must be trimmed if it exceeds it.
     */
    @SerialName("max_size")
    @Json(name = "max_size")
    val maxSize: Long = NETWORK_BODY_RULE_DEFAULT_MAX_SIZE_BYTES,

    /**
     * How many times this rule should be applied.
     */
    @SerialName("max_count")
    @Json(name = "max_count")
    val maxCount: Int = NETWORK_BODY_RULE_DEFAULT_MAX_COUNT,

    /**
     * Status codes to be captured.
     * -1 for capturing fail network requests.
     */
    @SerialName("status_codes")
    @Json(name = "status_codes")
    val statusCodes: Set<Int> = setOf(),

)
