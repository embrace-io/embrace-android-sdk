package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.capture.crumbs.Breadcrumb

/**
 * Custom breadcrumbs can be created to reflect events into the user timeline.
 * The max number of characters for this breadcrumb message is
 * [CustomBreadcrumb.BREADCRUMB_MESSAGE_MAX_LENGTH]
 */
internal class CustomBreadcrumb(
    message: String?,

    /**
     * The timestamp at which the event occurred.
     */
    @SerializedName("ts") private val timestamp: Long
) : Breadcrumb {

    /**
     * Message for the custom breadcrumb event.
     * If the message exceeds the [CustomBreadcrumb.BREADCRUMB_MESSAGE_MAX_LENGTH] characters
     * it will be ellipsized.
     */
    @SerializedName("m")
    val message: String?

    init {
        this.message = ellipsizeBreadcrumbMessage(message)
    }

    override fun getStartTime(): Long = timestamp

    private fun ellipsizeBreadcrumbMessage(input: String?): String? {
        return if (input == null || input.length < BREADCRUMB_MESSAGE_MAX_LENGTH) {
            input
        } else {
            input.substring(
                0,
                BREADCRUMB_MESSAGE_MAX_LENGTH - 3
            ) + "..."
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as CustomBreadcrumb

        if (timestamp != other.timestamp) {
            return false
        }
        if (message != other.message) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + (message?.hashCode() ?: 0)
        return result
    }

    companion object {
        private const val BREADCRUMB_MESSAGE_MAX_LENGTH = 256
    }
}
