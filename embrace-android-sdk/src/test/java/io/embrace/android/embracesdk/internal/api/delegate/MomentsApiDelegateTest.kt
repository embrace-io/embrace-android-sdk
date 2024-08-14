package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class MomentsApiDelegateTest {

    private val props = mapOf("key" to "value")

    private lateinit var delegate: MomentsApiDelegate
    private lateinit var orchestrator: FakeSessionOrchestrator
    private lateinit var eventService: FakeEventService

    @Before
    fun setUp() {
        val moduleInitBootstrapper = fakeModuleInitBootstrapper()
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext(), AppFramework.NATIVE, 0)
        orchestrator = moduleInitBootstrapper.sessionOrchestrationModule.sessionOrchestrator as FakeSessionOrchestrator
        eventService = moduleInitBootstrapper.momentsModule.eventService as FakeEventService

        val sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = MomentsApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun startMoment() {
        delegate.startMoment("test")
        val event = eventService.startedEvents.single()
        assertEquals("test", event.name)
    }

    @Test
    fun testStartMoment() {
        delegate.startMoment("test", "id")
        val event = eventService.startedEvents.single()
        assertEquals("test", event.name)
        assertEquals("id", event.identifier)
    }

    @Test
    fun testStartMoment1() {
        delegate.startMoment("test", "id", props)
        val event = eventService.startedEvents.single()
        assertEquals("test", event.name)
        assertEquals("id", event.identifier)
        assertEquals(props, event.properties)
    }

    @Test
    fun endMoment() {
        delegate.endMoment("test")
        val event = eventService.endedEvents.single()
        assertEquals("test", event.name)
    }

    @Test
    fun testEndMoment() {
        delegate.endMoment("test", "id")
        val event = eventService.endedEvents.single()
        assertEquals("test", event.name)
        assertEquals("id", event.identifier)
    }

    @Test
    fun testEndMoment1() {
        delegate.endMoment("test", props)
        val event = eventService.endedEvents.single()
        assertEquals("test", event.name)
        assertEquals(props, event.properties)
    }

    @Test
    fun testEndMoment2() {
        delegate.endMoment("test", "id", props)
        val event = eventService.endedEvents.single()
        assertEquals("test", event.name)
        assertEquals("id", event.identifier)
        assertEquals(props, event.properties)
    }

    @Test
    fun endAppStartup() {
        delegate.endAppStartup()
        val event = eventService.endedEvents.single()
        assertEquals("_startup", event.name)
    }

    @Test
    fun testEndAppStartup() {
        delegate.endAppStartup(props)
        val event = eventService.endedEvents.single()
        assertEquals("_startup", event.name)
        assertEquals(props, event.properties)
    }
}
