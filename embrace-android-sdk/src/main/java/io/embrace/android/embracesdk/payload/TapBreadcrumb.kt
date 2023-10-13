package io.embrace.android.embracesdk.payload

import android.util.Pair
import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.capture.crumbs.Breadcrumb
import io.embrace.android.embracesdk.payload.TapBreadcrumb.TapBreadcrumbType

/**
 * Breadcrumbs that represent tap events.
 */
internal class TapBreadcrumb(
    point: Pair<Float?, Float?>?,

    /**
     * Name of the tapped element.
     */
    @SerializedName("tt")
    val tappedElementName: String?,

    /**
     * The timestamp at which the event occurred.
     */
    @SerializedName("ts")
    private val timestamp: Long,

    /**
     * Type of TapBreadcrumb that categorizes the kind interaction, based on
     * [TapBreadcrumbType] types.
     */
    @SerializedName("t")
    val type: TapBreadcrumbType?
) : Breadcrumb {

    /**
     * Screen position (coordinates) of the tapped element.
     */
    @SerializedName("tl")
    var location: String? = null

    init {
        location = if (point != null) {
            val first = point.first?.toInt()?.toFloat() ?: 0.0f
            val second = point.second?.toInt()?.toFloat() ?: 0.0f
            first.toInt().toString() + "," + second.toInt()
        } else {
            "0,0"
        }
    }

    override fun getStartTime(): Long = timestamp

    internal enum class TapBreadcrumbType {
        @SerializedName("s")
        TAP, @SerializedName("l")
        LONG_PRESS
    }
}
