package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.DataCaptureService
import java.util.concurrent.LinkedBlockingDeque

internal class BreadcrumbDataStore<T>(
    private val limit: () -> Int
) : DataCaptureService<List<T>> {

    private val breadcrumbs = LinkedBlockingDeque<T>()

    fun tryAddBreadcrumb(breadcrumb: T) {
        if (!breadcrumbs.isEmpty() && breadcrumbs.size >= limit()) {
            breadcrumbs.removeLast()
        }
        breadcrumbs.push(breadcrumb)
    }

    override fun getCapturedData(): List<T> = breadcrumbs.toList()
    override fun cleanCollections() = breadcrumbs.clear()
}
