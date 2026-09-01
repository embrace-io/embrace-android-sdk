package io.embrace.android.embracesdk.instrumentation.leaks

import android.R.id.content
import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class FragmentLeakDetectionLifecycleCallbacksTest {

    private lateinit var leakDetector: LeakDetector
    private lateinit var callbacks: FragmentLeakDetectionLifecycleCallbacks

    @Before
    fun setUp() {
        leakDetector = LeakDetector(clock = { 0L })
        callbacks = FragmentLeakDetectionLifecycleCallbacks(leakDetector) { SESSION_IDS }
    }

    @Test
    fun `createFragmentSupport returns a real implementation when Fragment is present`() {
        assertTrue(createFragmentSupport(leakDetector) { SESSION_IDS } is FragmentLeakDetectionLifecycleCallbacks)
    }

    @Test
    fun `onActivityCreated on a plain Activity registers nothing and does not throw`() {
        val activity = buildActivity(Activity::class.java).setup().get()
        callbacks.onActivityCreated(activity)
    }

    @Test
    fun `attaching a fragment tracks it as opened`() {
        val activity = registerOn(buildActivity(FragmentActivity::class.java).setup().get())
        val fragment = Fragment()

        activity.supportFragmentManager.beginTransaction().add(fragment, "tag").commitNow()

        assertNotNull(
            "onFragmentAttached should have opened a sentinel for the fragment",
            leakDetector.trackClosed(fragment, LeakContext("fragment", SESSION_IDS)),
        )
    }

    @Test
    fun `destroying a fragment tracks it as closed`() {
        val activity = registerOn(buildActivity(FragmentActivity::class.java).setup().get())
        val fragment = Fragment()

        activity.supportFragmentManager.beginTransaction().add(fragment, "tag").commitNow()
        activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()

        assertNull(
            "onFragmentDestroyed should already have released the sentinel opened at attach",
            leakDetector.trackClosed(fragment, LeakContext("fragment", SESSION_IDS)),
        )
    }

    @Test
    fun `creating a fragment's view tracks it as opened`() {
        val activity = registerOn(buildActivity(FragmentActivity::class.java).setup().get())
        val fragment = ViewFragment()

        activity.supportFragmentManager.beginTransaction().add(content, fragment).commitNow()
        val view = checkNotNull(fragment.view)

        assertNotNull(
            "onFragmentViewCreated should have opened a sentinel for the fragment's view",
            leakDetector.trackClosed(view, LeakContext("fragment_view", SESSION_IDS)),
        )
    }

    @Test
    fun `destroying a fragment's view tracks it as closed`() {
        val activity = registerOn(buildActivity(FragmentActivity::class.java).setup().get())
        val fragment = ViewFragment()

        activity.supportFragmentManager.beginTransaction().add(content, fragment).commitNow()
        val view = checkNotNull(fragment.view)
        activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()

        assertNull(
            "onFragmentViewDestroyed should already have released the sentinel opened at view-created",
            leakDetector.trackClosed(view, LeakContext("fragment_view", SESSION_IDS)),
        )
    }

    @Test
    fun `child fragments are tracked recursively`() {
        val activity = registerOn(buildActivity(FragmentActivity::class.java).setup().get())
        val parent = Fragment()
        activity.supportFragmentManager.beginTransaction().add(parent, "parent").commitNow()

        val child = Fragment()
        parent.childFragmentManager.beginTransaction().add(child, "child").commitNow()

        assertNotNull(
            "a fragment attached to a child FragmentManager should be tracked too",
            leakDetector.trackClosed(child, LeakContext("fragment", SESSION_IDS)),
        )
    }

    private fun registerOn(activity: FragmentActivity): FragmentActivity {
        callbacks.onActivityCreated(activity)
        return activity
    }

    private companion object {
        val SESSION_IDS = SessionIdsSnapshot(userSessionId = "session-1", sessionPartId = "part-1")
    }
}

/**
 * A [Fragment] with a real view. Must be a top-level, non-private class - Fragment requires its subclasses to be
 * public and non-inner so they can be recreated from saved instance state.
 */
internal class ViewFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        View(requireContext())
}
