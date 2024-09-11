package io.embrace.android.embracesdk.internal.capture.startup

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeActivity
import io.embrace.android.embracesdk.fakes.FakeAppStartupDataCollector
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeSplashScreenActivity
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
internal class StartupTrackerTest {
    private lateinit var application: Application
    private lateinit var clock: FakeClock
    private lateinit var dataCollector: FakeAppStartupDataCollector
    private lateinit var logger: EmbLogger
    private lateinit var startupTracker: StartupTracker
    private lateinit var defaultActivityController: ActivityController<Activity>

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        clock = FakeClock()
        logger = FakeEmbLogger()
        dataCollector = FakeAppStartupDataCollector(clock = clock)
        startupTracker = StartupTracker(
            appStartupDataCollector = dataCollector,
            uiLoadEventEmitter = object : ActivityLifecycleListener { },
            logger = logger,
            versionChecker = BuildVersionChecker
        )
        application.registerActivityLifecycleCallbacks(startupTracker)
        defaultActivityController = Robolectric.buildActivity(Activity::class.java)
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `cold start in U`() {
        with(launchActivity()) {
            assertEquals("android.app.Activity", dataCollector.startupActivityName)
            verifyTiming(
                preCreateTime = createTime,
                createTime = createTime,
                postCreateTime = createTime,
                startTime = startTime,
                resumeTime = resumeTime
            )
        }
    }

    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun `cold start in Q`() {
        with(launchActivity()) {
            verifyTiming(
                preCreateTime = createTime,
                createTime = createTime,
                postCreateTime = createTime,
                startTime = startTime,
                resumeTime = resumeTime
            )
        }
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `cold start in P`() {
        with(launchActivity()) {
            verifyTiming(
                createTime = createTime,
                startTime = startTime,
                resumeTime = resumeTime
            )
        }
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `cold start in L`() {
        with(launchActivity()) {
            verifyTiming(
                createTime = createTime,
                startTime = startTime,
                resumeTime = resumeTime
            )
        }
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `cold start with different activities being created and foregrounded first`() {
        defaultActivityController.create()
        clock.tick()
        with(launchActivity(Robolectric.buildActivity(FakeActivity::class.java))) {
            assertEquals("io.embrace.android.embracesdk.fakes.FakeActivity", dataCollector.startupActivityName)
            verifyTiming(
                preCreateTime = createTime,
                createTime = createTime,
                postCreateTime = createTime,
                startTime = startTime,
                resumeTime = resumeTime
            )
        }
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `cold start initial activity not tracked will use the second for timing`() {
        launchActivity(Robolectric.buildActivity(FakeSplashScreenActivity::class.java))
        clock.tick()
        with(launchActivity()) {
            assertEquals("android.app.Activity", dataCollector.startupActivityName)
            verifyTiming(
                preCreateTime = createTime,
                createTime = createTime,
                postCreateTime = createTime,
                startTime = startTime,
                resumeTime = resumeTime
            )
        }
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify startup tracker detached after trace recorded`() {
        val firstLaunchTimes = launchActivity()
        assertEquals(firstLaunchTimes.createTime, dataCollector.startupActivityInitStartMs)
        clock.tick(500_000)
        val secondLaunchTimes = launchActivity()
        assertNotEquals(secondLaunchTimes.createTime, dataCollector.startupActivityInitStartMs)
    }

    private fun launchActivity(controller: ActivityController<*> = defaultActivityController): ActivityTiming {
        val createTime = clock.now()
        controller.create()
        clock.tick()
        val startTime = clock.now()
        controller.start()
        clock.tick()
        val resumeTime = clock.now()
        controller.resume()
        clock.tick()
        return ActivityTiming(createTime, startTime, resumeTime)
    }

    private fun verifyTiming(
        preCreateTime: Long? = null,
        createTime: Long,
        postCreateTime: Long? = null,
        startTime: Long,
        resumeTime: Long,
    ) {
        assertEquals(preCreateTime, dataCollector.startupActivityPreCreatedMs)
        assertEquals(createTime, dataCollector.startupActivityInitStartMs)
        assertEquals(postCreateTime, dataCollector.startupActivityPostCreatedMs)
        assertEquals(startTime, dataCollector.startupActivityInitEndMs)
        assertEquals(resumeTime, dataCollector.startupActivityResumedMs)
    }

    private data class ActivityTiming(
        val createTime: Long,
        val startTime: Long,
        val resumeTime: Long,
    )
}
