package io.embrace.android.embracesdk.instrumentation.leaks

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Covers whether [LeakDetectionDataSource] wires up fragment leak detection depending on
 * [io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehavior.isFragmentLeakDetectionEnabled]. Kept
 * separate from [LeakDetectionDataSourceTest] since each test here needs its own [LeakDetectionDataSource], built with
 * different behavior, rather than sharing the one constructed in a common `@Before`.
 */
@RunWith(RobolectricTestRunner::class)
internal class LeakDetectionDataSourceFragmentGatingTest {

    private lateinit var dataSource: LeakDetectionDataSource

    @After
    fun tearDown() {
        dataSource.onDataCaptureDisabled()
    }

    @Test
    fun `fragment leak detection is not wired when disabled (the default)`() {
        dataSource = LeakDetectionDataSource(FakeInstrumentationArgs(RuntimeEnvironment.getApplication()))
        dataSource.onDataCaptureEnabled()

        val activity = buildActivity(FragmentActivity::class.java).setup().get()
        val fragment = Fragment()
        activity.supportFragmentManager.beginTransaction().add(fragment, "tag").commitNow()

        assertNull(
            "fragment tracking should be a no-op when the RemoteConfig flag is off",
            dataSource.leakDetector.trackClosed(fragment, LeakContext("fragment", SESSION_IDS)),
        )
    }

    @Test
    fun `fragment leak detection is wired when enabled`() {
        val args = FakeInstrumentationArgs(RuntimeEnvironment.getApplication())
        args.configService.autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(fragmentLeakDetectionEnabled = true)
        dataSource = LeakDetectionDataSource(args)
        dataSource.onDataCaptureEnabled()

        val activity = buildActivity(FragmentActivity::class.java).setup().get()
        val fragment = Fragment()
        activity.supportFragmentManager.beginTransaction().add(fragment, "tag").commitNow()

        assertNotNull(
            "attaching a fragment should have opened a sentinel once fragment leak detection is enabled",
            dataSource.leakDetector.trackClosed(fragment, LeakContext("fragment", SESSION_IDS)),
        )
    }

    private companion object {
        val SESSION_IDS = SessionIdsSnapshot(userSessionId = "session-1", sessionPartId = "part-1")
    }
}
