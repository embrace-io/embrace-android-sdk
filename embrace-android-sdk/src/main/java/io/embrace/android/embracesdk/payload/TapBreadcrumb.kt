package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.internal.capture.crumbs.Breadcrumb
import io.embrace.android.embracesdk.payload.TapBreadcrumb.TapBreadcrumbType

/**
 * Breadcrumbs that represent tap events.
 */
@JsonClass(generateAdapter = true)
internal class TapBreadcrumb(
    point: Pair<Float?, Float?>? = null,

    /**
     * Name of the tapped element.
     */
    @Json(name = "tt")
    val tappedElementName: String?,

    /**
     * The timestamp at which the event occurred.
     */
    @Json(name = "ts")
    internal val timestamp: Long,

    /**
     * Type of TapBreadcrumb that categorizes the kind interaction, based on
     * [TapBreadcrumbType] types.
     */
    @Json(name = "t")
    val type: TapBreadcrumbType?
) : Breadcrumb {

    /**
     * Screen position (coordinates) of the tapped element.
     */
    @Json(name = "tl")
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

    @JsonClass(generateAdapter = false)
    internal enum class TapBreadcrumbType(val value: String) {
        @Json(name = "s")
        TAP("tap"),

        @Json(name = "l")
        LONG_PRESS("long_press")
    }
}
