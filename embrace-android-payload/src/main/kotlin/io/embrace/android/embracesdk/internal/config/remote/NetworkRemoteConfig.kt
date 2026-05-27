package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configures limit of number of requests for network calls per domain.
 *
 *
 * If the default capture limit is specified as zero, then the config operates in allow-list
 * mode, meaning only specified domains will be tracked.
 */
@Serializable
@JsonClass(generateAdapter = true)
data class NetworkRemoteConfig(

    /**
     * The default request capture limit for non-specified domains.
     */
    val defaultCaptureLimit: Int? = null,

    /**
     * Map of domain suffix to maximum number of requests.
     */
    @SerialName("domains")
    @Json(name = "domains")
    val domainLimits: Map<String, Int>? = null,
)
