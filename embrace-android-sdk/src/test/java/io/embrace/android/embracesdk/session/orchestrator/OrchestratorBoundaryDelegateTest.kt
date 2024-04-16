package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class OrchestratorBoundaryDelegateTest {

    private lateinit var delegate: OrchestratorBoundaryDelegate
    private lateinit var memoryCleanerService: FakeMemoryCleanerService
    private lateinit var userService: FakeUserService
    private lateinit var ndkService: FakeNdkService
    private lateinit var sessionProperties: EmbraceSessionProperties
    private lateinit var internalErrorService: FakeInternalErrorService
    private lateinit var networkConnectivityService: FakeNetworkConnectivityService

    @Before
    fun setUp() {
        memoryCleanerService = FakeMemoryCleanerService()
        userService = FakeUserService()
        ndkService = FakeNdkService()
        sessionProperties = fakeEmbraceSessionProperties().apply {
            add("key", "value", false)
        }
        internalErrorService = FakeInternalErrorService()
        networkConnectivityService = FakeNetworkConnectivityService()
        delegate = OrchestratorBoundaryDelegate(
            memoryCleanerService,
            userService,
            ndkService,
            sessionProperties,
            internalErrorService,
            networkConnectivityService
        )
    }

    @Test
    fun `prepare new session clear user info true`() {
        delegate.prepareForNewSession(1000L, clearUserInfo = true)
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(0, sessionProperties.get().size)
        assertEquals(1, userService.clearedCount)
        assertEquals(1, ndkService.userUpdateCount)
        assertEquals(1, networkConnectivityService.networkStatusOnSessionStartedCount)
    }

    @Test
    fun `prepare new session clear user info false`() {
        delegate.prepareForNewSession(1000L, clearUserInfo = false)
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(0, sessionProperties.get().size)
        assertEquals(0, userService.clearedCount)
        assertEquals(0, ndkService.userUpdateCount)
        assertEquals(1, networkConnectivityService.networkStatusOnSessionStartedCount)
    }
}
