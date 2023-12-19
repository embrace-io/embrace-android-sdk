package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.capture.crumbs.Breadcrumb

/**
 * Breadcrumb that represents the display event for a View.
 */
@JsonClass(generateAdapter = true)
internal class ViewBreadcrumb(
    /**
     * The screen name for the view breadcrumb.
     */
    screen: String?,

    /**
     * The timestamp at which the view started.
     */
    @Json(name = "st")
    val start: Long?,

    /**
     * The timestamp at which the view ended.
     */
    @Json(name = "en")
    var end: Long? = null
) : Breadcrumb {

    @Json(name = "vn")
    val screen: String

    init {
        this.screen = screen ?: FALLBACK_SCREEN_NAME
    }

    override fun getStartTime(): Long = start ?: 0

    companion object {
        private const val FALLBACK_SCREEN_NAME = "Unknown screen"
    }
}
