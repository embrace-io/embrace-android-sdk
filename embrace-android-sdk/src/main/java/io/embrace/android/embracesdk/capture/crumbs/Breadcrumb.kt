package io.embrace.android.embracesdk.capture.crumbs

/**
 * Describes a user's journey through the application.
 */
internal interface Breadcrumb {

    /**
     * Gets the timestamp of the event.
     *
     * @return the timestamp
     */
    fun getStartTime(): Long
}
