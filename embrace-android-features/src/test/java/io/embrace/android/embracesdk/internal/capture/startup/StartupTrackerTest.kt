package io.embrace.android.embracesdk.internal.capture.startup

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeActivity
import io.embrace.android.embracesdk.fakes.FakeActivityLifecycleListener
import io.embrace.android.embracesdk.fakes.FakeAppStartupDataCollector
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeDrawEventEmitter
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeNotStartupActivity
import io.embrace.android.embracesdk.fakes.FakeSplashScreenActivity
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private lateinit var activityLifecycleListener: FakeActivityLifecycleListener
    private lateinit var drawEventEmitter: FakeDrawEventEmitter
    private lateinit var startupTracker: StartupTracker
    private lateinit var defaultActivityController: ActivityController<Activity>

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        clock = FakeClock()
        logger = FakeEmbLogger()
        dataCollector = FakeAppStartupDataCollector(clock = clock)
        activityLifecycleListener = FakeActivityLifecycleListener()
        drawEventEmitter = FakeDrawEventEmitter()
        startupTracker = StartupTracker(
            appStartupDataCollector = dataCollector,
            activityLoadEventEmitter = activityLifecycleListener,
            drawEventEmitterFactory = { drawEventEmitter },
        )
        application.registerActivityLifecycleCallbacks(startupTracker)
        defaultActivityController = Robolectric.buildActivity(Activity::class.java)
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `cold start in U`() {
        with(launchActivity()) {
            assertEquals("android.app.Activity", dataCollector.startupActivityName)
            verifyLifecycle(
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
            verifyLifecycle(
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
            verifyLifecycle(
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
            verifyLifecycle(
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
            verifyLifecycle(
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
            verifyLifecycle(
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

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `lifecycle event emitter attached after activity launch`() {
        launchActivity()
        assertEquals(0, activityLifecycleListener.onCreateInvokedCount)
        defaultActivityController.create(null)
        assertEquals(1, activityLifecycleListener.onCreateInvokedCount)
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `data only collected if activity is used as startup activity`() {
        launchActivity(Robolectric.buildActivity(FakeNotStartupActivity::class.java))
        assertNull(dataCollector.applicationInitStartMs)
        assertNull(dataCollector.applicationInitEndMs)
        assertNull(dataCollector.startupActivityName)
        assertNull(dataCollector.startupActivityPreCreatedMs)
        assertNull(dataCollector.startupActivityInitStartMs)
        assertNull(dataCollector.startupActivityPostCreatedMs)
        assertNull(dataCollector.startupActivityInitEndMs)
        assertNull(dataCollector.startupActivityResumedMs)
        assertNull(dataCollector.firstFrameRenderedMs)
        assertNull(drawEventEmitter.lastRegisteredActivity)
        assertNull(drawEventEmitter.lastCallback)
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `render time tracked if first draw event emitted`() {
        launchActivity()
        checkNotNull(drawEventEmitter.lastCallback)()
        assertEquals(clock.now(), dataCollector.firstFrameRenderedMs)
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `first draw event emitted detaches startup tracker and attaches lifecycle event emitter`() {
        defaultActivityController.create(null)
        assertEquals(clock.now(), dataCollector.startupActivityInitStartMs)
        clock.tick()
        defaultActivityController.create(null)
        assertEquals(clock.now(), dataCollector.startupActivityInitStartMs)
        clock.tick()
        checkNotNull(drawEventEmitter.lastCallback)()
        defaultActivityController.create(null)
        assertNotEquals(clock.now(), dataCollector.startupActivityInitStartMs)
        assertEquals(1, activityLifecycleListener.onCreateInvokedCount)
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

    private fun verifyLifecycle(
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
        assertNull(dataCollector.firstFrameRenderedMs)
        assertNotNull(drawEventEmitter.lastRegisteredActivity)
        assertNotNull(drawEventEmitter.lastCallback)
    }

    private data class ActivityTiming(
        val createTime: Long,
        val startTime: Long,
        val resumeTime: Long
    )
}
