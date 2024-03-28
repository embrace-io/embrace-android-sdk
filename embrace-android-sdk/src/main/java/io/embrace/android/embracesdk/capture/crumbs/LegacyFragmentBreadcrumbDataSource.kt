package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.payload.FragmentBreadcrumb
import io.embrace.android.embracesdk.utils.filter
import java.util.Collections

/**
 * Captures fragment breadcrumbs.
 */
internal class LegacyFragmentBreadcrumbDataSource(
    private val configService: ConfigService,
    private val clock: Clock,
    private val store: BreadcrumbDataStore<FragmentBreadcrumb> = BreadcrumbDataStore {
        configService.breadcrumbBehavior.getFragmentBreadcrumbLimit()
    }
) : DataCaptureService<List<FragmentBreadcrumb>> by store {

    companion object {

        /**
         * The default limit for how many open tracked fragments are allowed, which can be overridden
         * by [RemoteConfig].
         */
        private const val DEFAULT_VIEW_STACK_SIZE = 20
    }

    internal val fragmentStack: MutableList<FragmentBreadcrumb> = Collections.synchronizedList(ArrayList<FragmentBreadcrumb>())

    fun startFragment(name: String?): Boolean {
        if (name == null) {
            return false
        }
        synchronized(this) {
            if (fragmentStack.size >= DEFAULT_VIEW_STACK_SIZE) {
                return false
            }
            return fragmentStack.add(FragmentBreadcrumb(name, clock.now(), 0))
        }
    }

    fun endFragment(name: String?): Boolean {
        if (name == null) {
            return false
        }
        var start: FragmentBreadcrumb
        val end = FragmentBreadcrumb(name, 0, clock.now())
        synchronized(this) {
            val crumbs = filter(fragmentStack) { crumb: FragmentBreadcrumb -> crumb.name == name }
            if (crumbs.isEmpty()) {
                return false
            }
            start = crumbs[0]
            fragmentStack.remove(start)
        }
        end.setStartTime(start.getStartTime())
        store.tryAddBreadcrumb(end)
        return true
    }

    /**
     * Close all open fragments when the activity closes
     */
    fun onViewClose() {
        if (!configService.breadcrumbBehavior.isActivityBreadcrumbCaptureEnabled()) {
            return
        }
        if (fragmentStack.size == 0) {
            return
        }
        val ts = clock.now()
        synchronized(fragmentStack) {
            for (fragment in fragmentStack) {
                fragment.endTime = ts
                store.tryAddBreadcrumb(fragment)
            }
            fragmentStack.clear()
        }
    }

    override fun cleanCollections() {
        store.cleanCollections()
        fragmentStack.clear()
    }
}
