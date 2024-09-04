package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class OrchestratorBoundaryDelegateTest {

    private lateinit var delegate: OrchestratorBoundaryDelegate
    private lateinit var memoryCleanerService: FakeMemoryCleanerService
    private lateinit var userService: FakeUserService
    private lateinit var sessionPropertiesService: SessionPropertiesService

    @Before
    fun setUp() {
        memoryCleanerService = FakeMemoryCleanerService()
        userService = FakeUserService()
        sessionPropertiesService = FakeSessionPropertiesService().apply {
            addProperty("key", "value", false)
        }
        delegate = OrchestratorBoundaryDelegate(
            memoryCleanerService,
            userService,
            sessionPropertiesService
        )
    }

    @Test
    fun `prepare new session clear user info true`() {
        delegate.prepareForNewSession(clearUserInfo = true)
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(0, sessionPropertiesService.getProperties().size)
        assertEquals(1, userService.clearedCount)
    }

    @Test
    fun `prepare new session clear user info false`() {
        delegate.prepareForNewSession(clearUserInfo = false)
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(0, sessionPropertiesService.getProperties().size)
        assertEquals(0, userService.clearedCount)
    }
}
