package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakeNdkService
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.internal.capture.session.EmbraceSessionProperties
import io.embrace.android.embracesdk.internal.session.orchestrator.OrchestratorBoundaryDelegate
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class OrchestratorBoundaryDelegateTest {

    private lateinit var delegate: OrchestratorBoundaryDelegate
    private lateinit var memoryCleanerService: FakeMemoryCleanerService
    private lateinit var userService: FakeUserService
    private lateinit var ndkService: FakeNdkService
    private lateinit var sessionProperties: EmbraceSessionProperties
    private lateinit var networkConnectivityService: FakeNetworkConnectivityService

    @Before
    fun setUp() {
        memoryCleanerService = FakeMemoryCleanerService()
        userService = FakeUserService()
        ndkService = FakeNdkService()
        sessionProperties = fakeEmbraceSessionProperties().apply {
            add("key", "value", false)
        }
        networkConnectivityService = FakeNetworkConnectivityService()
        delegate = OrchestratorBoundaryDelegate(
            memoryCleanerService,
            userService,
            ndkService,
            sessionProperties,
            networkConnectivityService
        )
    }

    @Test
    fun `prepare new session clear user info true`() {
        delegate.prepareForNewSession(clearUserInfo = true)
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(0, sessionProperties.get().size)
        assertEquals(1, userService.clearedCount)
        assertEquals(1, ndkService.userUpdateCount)
    }

    @Test
    fun `prepare new session clear user info false`() {
        delegate.prepareForNewSession(clearUserInfo = false)
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(0, sessionProperties.get().size)
        assertEquals(0, userService.clearedCount)
        assertEquals(0, ndkService.userUpdateCount)
    }

    @Test
    fun `on session started`() {
        delegate.onSessionStarted(1000)
        assertEquals(1, networkConnectivityService.networkStatusOnSessionStartedCount)
    }
}
