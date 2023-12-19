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
internal data class SessionMessage @JvmOverloads internal constructor(

    /**
     * The session information.
     */
    @Json(name = "s")
    val session: Session,

    /**
     * The user information.
     */
    @Json(name = "u")
    val userInfo: UserInfo? = null,

    /**
     * The app information.
     */
    @Json(name = "a")
    val appInfo: AppInfo? = null,

    /**
     * The device information.
     */
    @Json(name = "d")
    val deviceInfo: DeviceInfo? = null,

    /**
     * The device's performance info, such as power, cpu, network.
     */
    @Json(name = "p")
    val performanceInfo: PerformanceInfo? = null,

    /**
     * Breadcrumbs which occurred as part of this session.
     */
    @Json(name = "br")
    val breadcrumbs: Breadcrumbs? = null,

    @Json(name = "spans")
    val spans: List<EmbraceSpanData>? = null,

    @Json(name = "v")
    val version: Int = ApiClient.MESSAGE_VERSION
)
