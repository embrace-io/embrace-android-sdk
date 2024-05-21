package io.embrace.android.embracesdk.internal.api

internal interface BreadcrumbApi {

    /**
     * Adds a breadcrumb.
     *
     * Breadcrumbs track a user's journey through the application and will be shown on the timeline.
     *
     * @param message the name of the breadcrumb to add
     */
    fun addBreadcrumb(message: String)
}
