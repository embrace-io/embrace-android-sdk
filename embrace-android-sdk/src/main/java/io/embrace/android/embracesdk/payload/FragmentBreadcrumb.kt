package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.capture.crumbs.Breadcrumb

/**
 * Breadcrumb that represents a fragment that was viewed.
 */
internal class FragmentBreadcrumb(
    @SerializedName("n")
    val name: String,

    @SerializedName("st")
    var start: Long,

    @SerializedName("en")
    var endTime: Long
) : Breadcrumb {
    override fun getStartTime(): Long = start

    fun setStartTime(startTime: Long) {
        start = startTime
    }
}
