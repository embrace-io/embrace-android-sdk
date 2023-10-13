package io.embrace.android.embracesdk

import android.app.Activity
import android.os.Bundle
import io.embrace.android.embracesdk.capture.crumbs.activity.EmbraceActivityLifecycleBreadcrumbService
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeSdkModeBehavior
import io.embrace.android.embracesdk.payload.ActivityLifecycleState
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

internal class EmbraceActivityLifecycleBreadcrumbServiceTest {

    private val configService = FakeConfigService(
        sdkModeBehavior = fakeSdkModeBehavior(
            isDebug = true
        )
    )

    @Test
    fun `test breadcrumb collection`() {
        val activity = mockk<Activity>()
        val collector = EmbraceActivityLifecycleBreadcrumbService(configService) { 1600000000L }
        assertEquals(0, collector.getCapturedData().size)

        with(collector) {
            onActivityPreCreated(activity, null)
            onActivityPostCreated(activity, null)
            onActivityPreStarted(activity)
            onActivityPostStarted(activity)
            onActivityPreResumed(activity)
            onActivityPostResumed(activity)
            onActivityPrePaused(activity)
            onActivityPostPaused(activity)
            onActivityPreStopped(activity)
            onActivityPostStopped(activity)
            onActivityPreDestroyed(activity)
            onActivityPostDestroyed(activity)
        }

        // check top-level data
        val data = collector.getCapturedData()
        val obj = data.single()
        assertEquals("Activity", obj.activity)
        checkNotNull(obj.data)
        assertEquals(6, obj.data.size)

        // check crumbs
        obj.data.forEachIndexed { index, crumb ->
            assertEquals("Activity", crumb.activity)
            assertEquals(1600000000L, crumb.start)
            assertEquals(1600000000L, crumb.end)
            assertEquals(false, crumb.bundlePresent)
            val expectedState = ActivityLifecycleState.values()[index]
            assertEquals(expectedState, crumb.state)
        }
    }

    @Test
    fun `test saved state check`() {
        val activity = mockk<Activity>()
        val bundle = mockk<Bundle>()
        val collector = EmbraceActivityLifecycleBreadcrumbService(configService) { 0 }
        collector.onActivityPreCreated(activity, bundle)
        collector.onActivityPostCreated(activity, bundle)

        val obj = collector.getCapturedData()
        val crumb = checkNotNull(obj.single().data).single()
        assertEquals(true, crumb.bundlePresent)
    }

    @Test
    fun `test exceeding breadcrumb limit drops oldest`() {
        val activity = mockk<Activity>()
        val tick = AtomicLong(-1)
        val collector =
            EmbraceActivityLifecycleBreadcrumbService(configService) { tick.incrementAndGet() }

        repeat(80) {
            collector.onActivityPreResumed(activity)
            collector.onActivityPostResumed(activity)
            collector.onActivityPrePaused(activity)
            collector.onActivityPostPaused(activity)
        }
        val obj = collector.getCapturedData()
        val crumbs = checkNotNull(obj.single().data)
        assertEquals(80, crumbs.size)
        assertEquals(160L, crumbs.first().start)
        assertEquals(318L, crumbs.last().start)
    }

    @Test
    fun `test breadcrumbs captured`() {
        val activity = mockk<Activity>()
        val configService = FakeConfigService(
            sdkModeBehavior = fakeSdkModeBehavior(
                isDebug = true
            )
        )

        val collector = EmbraceActivityLifecycleBreadcrumbService(configService) { 1600000000L }
        collector.onActivityPreStarted(activity)
        collector.onActivityPostStarted(activity)

        val capturedData = collector.getCapturedData()
        val obj = capturedData.single()
        assertEquals("Activity", obj.activity)

        val crumb = checkNotNull(obj.data).single()
        assertEquals(1600000000L, crumb.start)
        assertEquals(1600000000L, crumb.end)
        assertEquals(false, crumb.bundlePresent)
        assertEquals(ActivityLifecycleState.ON_START, crumb.state)
    }

    @Test
    fun `test sending disabled`() {
        val activity = mockk<Activity>()
        val configService = FakeConfigService(
            sdkModeBehavior = fakeSdkModeBehavior(
                remoteCfg = { RemoteConfig(pctBetaFeaturesEnabled = 0.0f) }
            )
        )
        val collector = EmbraceActivityLifecycleBreadcrumbService(configService) { 1600000000L }
        collector.onActivityPreStarted(activity)
        collector.onActivityPostStarted(activity)
        assertEquals(0, collector.getCapturedData().size)
    }

    @Test
    fun testCleanCollections() {
        val activity = mockk<Activity>()
        val service = EmbraceActivityLifecycleBreadcrumbService(configService) { 1600000000L }
        service.onActivityPreStarted(activity)
        assertEquals(1, service.getCapturedData().size)

        service.cleanCollections()
        assertEquals(0, service.getCapturedData().size)
    }
}
