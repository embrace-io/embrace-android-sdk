package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.clock.Clock

internal class BreadcrumbApiDelegate(
    private val clock: Clock,
    private val breadcrumbDataSource: Lazy<BreadcrumbDataSource?>,
) : BreadcrumbApi {

    override fun addBreadcrumb(message: String) {
        breadcrumbDataSource.value?.logCustom(message, clock.now())
    }
}
