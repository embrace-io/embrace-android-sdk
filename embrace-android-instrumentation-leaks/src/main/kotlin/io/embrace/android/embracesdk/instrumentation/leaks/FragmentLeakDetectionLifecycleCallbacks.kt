package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot

/**
 * Tracks Fragment and Fragment View leaks by registering itself, recursively, on every [FragmentActivity]'s
 * [FragmentManager]. Only ever constructed from behind [createFragmentSupport]'s presence check.
 */
internal class FragmentLeakDetectionLifecycleCallbacks(
    private val leakDetector: LeakDetector,
    private val activeSessionIdsProvider: () -> SessionIdsSnapshot,
) : FragmentManager.FragmentLifecycleCallbacks(), FragmentSupport {

    private val pendingViews = FragmentViewMap()

    override fun onActivityCreated(activity: Activity) {
        if (activity is FragmentActivity) {
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(this, true)
        }
    }

    override fun onFragmentAttached(fm: FragmentManager, f: Fragment, context: Context) {
        leakDetector.trackOpened(f)
    }

    override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
        leakDetector.trackClosed(f, LeakContext(FRAGMENT_OBJECT_TYPE, activeSessionIdsProvider()))
    }

    override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
        pendingViews.opened(f, v)
        leakDetector.trackOpened(v)
    }

    override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
        val view = pendingViews.closed(f) ?: return
        leakDetector.trackClosed(view, LeakContext(FRAGMENT_VIEW_OBJECT_TYPE, activeSessionIdsProvider()))
    }

    private companion object {

        /**
         * Reported as [LeakContext.objectType].
         */
        const val FRAGMENT_OBJECT_TYPE = "fragment"

        /**
         * Reported as [LeakContext.objectType].
         */
        const val FRAGMENT_VIEW_OBJECT_TYPE = "fragment_view"
    }
}
