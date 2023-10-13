package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.capture.crumbs.Breadcrumb

/**
 * Breadcrumb that represents the display event for a View.
 */
internal class ViewBreadcrumb(
    /**
     * The screen name for the view breadcrumb.
     */
    screen: String?,

    /**
     * The timestamp at which the view started.
     */
    @SerializedName("st")
    val start: Long?,

    /**
     * The timestamp at which the view ended.
     */
    @SerializedName("en")
    var end: Long? = null
) : Breadcrumb {

    @SerializedName("vn")
    val screen: String

    init {
        this.screen = screen ?: FALLBACK_SCREEN_NAME
    }

    override fun getStartTime(): Long = start ?: 0

    companion object {
        private const val FALLBACK_SCREEN_NAME = "Unknown screen"
    }
}
