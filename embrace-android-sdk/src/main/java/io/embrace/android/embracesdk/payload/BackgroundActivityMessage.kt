package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData

/**
 * The session message, containing the session itself, as well as performance information about the
 * device which occurred during the session.
 */
@JsonClass(generateAdapter = true)
internal data class BackgroundActivityMessage @JvmOverloads internal constructor(

    /**
     * The session information.
     */
    @Json(name = "s")
    val backgroundActivity: BackgroundActivity,

    /**
     * The user information.
     */
    @Json(name = "u")
    val userInfo: UserInfo?,

    /**
     * The app information.
     */
    @Json(name = "a")
    val appInfo: AppInfo?,

    /**
     * The device information.
     */
    @Json(name = "d")
    val deviceInfo: DeviceInfo?,

    /**
     * The device's performance info, such as power, cpu, network.
     */
    @Json(name = "p")
    val performanceInfo: PerformanceInfo?,

    /**
     * Breadcrumbs which occurred as part of this session.
     */
    @Json(name = "br")
    val breadcrumbs: Breadcrumbs?,

    @Json(name = "spans")
    val spans: List<EmbraceSpanData>?,

    @Json(name = "v")
    val version: Int = ApiClient.MESSAGE_VERSION
)
