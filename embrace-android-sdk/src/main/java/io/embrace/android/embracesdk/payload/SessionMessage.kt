package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData

/**
 * The session message, containing the session itself, as well as performance information about the
 * device which occurred during the session.
 */
internal data class SessionMessage @JvmOverloads internal constructor(

    /**
     * The session information.
     */
    @SerializedName("s")
    val session: Session,

    /**
     * The user information.
     */
    @SerializedName("u")
    val userInfo: UserInfo? = null,

    /**
     * The app information.
     */
    @SerializedName("a")
    val appInfo: AppInfo? = null,

    /**
     * The device information.
     */
    @SerializedName("d")
    val deviceInfo: DeviceInfo? = null,

    /**
     * The device's performance info, such as power, cpu, network.
     */
    @SerializedName("p")
    val performanceInfo: PerformanceInfo? = null,

    /**
     * Breadcrumbs which occurred as part of this session.
     */
    @SerializedName("br")
    val breadcrumbs: Breadcrumbs? = null,

    @SerializedName("spans")
    val spans: List<EmbraceSpanData>? = null,

    @SerializedName("v")
    val version: Int = ApiClient.MESSAGE_VERSION
)
