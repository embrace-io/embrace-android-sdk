package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.capture.crumbs.Breadcrumb

/**
 * Breadcrumb that represents a fragment that was viewed.
 */
@JsonClass(generateAdapter = true)
internal class FragmentBreadcrumb(
    @Json(name = "n")
    val name: String,

    @Json(name = "st")
    var start: Long,

    @Json(name = "en")
    var endTime: Long? = null
) : Breadcrumb {
    override fun getStartTime(): Long = start

    fun setStartTime(startTime: Long) {
        start = startTime
    }
}
