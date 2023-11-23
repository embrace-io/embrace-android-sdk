package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData

/**
 * The session message, containing the session itself, as well as performance information about the
 * device which occurred during the session.
 */
internal data class BackgroundActivityMessage<T> @JvmOverloads internal constructor(

    // TODO: this is identical to SessionMessage. We can combine the two classes.

    /**
     * The session information.
     */
    @SerializedName("s")
    val backgroundActivity: T,

    /**
     * The user information.
     */
    @SerializedName("u")
    val userInfo: UserInfo?,

    /**
     * The app information.
     */
    @SerializedName("a")
    val appInfo: AppInfo,

    /**
     * The device information.
     */
    @SerializedName("d")
    val deviceInfo: DeviceInfo,

    /**
     * The device's performance info, such as power, cpu, network.
     */
    @SerializedName("p")
    val performanceInfo: PerformanceInfo,

    /**
     * Breadcrumbs which occurred as part of this session.
     */
    @SerializedName("br")
    val breadcrumbs: Breadcrumbs,

    @SerializedName("spans")
    val spans: List<EmbraceSpanData>?,

    @SerializedName("v")
    val version: Int = ApiClient.MESSAGE_VERSION
)
