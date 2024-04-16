package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.ViewBreadcrumb

/**
 * Captures tap breadcrumbs.
 */
internal class LegacyViewBreadcrumbDataSource(
    private val configService: ConfigService,
    private val clock: Clock,
    private val logger: InternalEmbraceLogger,
    private val store: BreadcrumbDataStore<ViewBreadcrumb> = BreadcrumbDataStore {
        configService.breadcrumbBehavior.getViewBreadcrumbLimit()
    },
) : DataCaptureService<List<ViewBreadcrumb>> by store {

    /**
     * Adds the view breadcrumb to the queue.
     *
     * @param screen    name of the screen.
     * @param timestamp time of occurrence of the tap event.
     * @param force     will run no duplication checks on the previous view breadcrumb registry.
     */
    fun addToViewLogsQueue(screen: String?, timestamp: Long, force: Boolean) {
        try {
            val lastCrumb = store.peek()
            val lastScreen = lastCrumb?.screen ?: ""
            if (force || lastCrumb == null || !lastScreen.equals(
                    screen.toString(),
                    ignoreCase = true
                )
            ) {
                lastCrumb?.end = timestamp
                store.tryAddBreadcrumb(ViewBreadcrumb(screen, timestamp))
            }
        } catch (ex: Exception) {
            logger.logError("Failed to add view breadcrumb for $screen", ex)
        }
    }

    fun replaceFirstSessionView(screen: String, timestamp: Long) {
        store.peek()?.apply {
            this.start = timestamp
            this.screen = screen
        }
    }

    fun getLastViewBreadcrumbScreenName(): String? = store.peek()?.screen

    /**
     * Close all open fragments when the activity closes
     */
    fun onViewClose() {
        if (!configService.breadcrumbBehavior.isActivityBreadcrumbCaptureEnabled()) {
            return
        }
        store.peek()?.end = clock.now()
    }
}
