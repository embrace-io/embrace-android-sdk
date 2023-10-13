package io.embrace.android.embracesdk.config.remote

import com.google.gson.annotations.SerializedName

/**
 * Configures limit of number of requests for network calls per domain.
 *
 *
 * If the default capture limit is specified as zero, then the config operates in allow-list
 * mode, meaning only specified domains will be tracked.
 */
internal data class NetworkRemoteConfig(

    /**
     * The default request capture limit for non-specified domains.
     */
    val defaultCaptureLimit: Int? = null,

    /**
     * Map of domain suffix to maximum number of requests.
     */
    @SerializedName("domains")
    val domainLimits: Map<String, Int>? = null
)
