package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.unimplemented
import io.embrace.android.embracesdk.internal.instrumentation.crash.LastRunCrashVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SdkStateApiDelegateTest {

    private lateinit var delegate: SdkStateApiDelegate
    private lateinit var configService: FakeConfigService
    private lateinit var sessionIdsProvider: FakeSessionIdsProvider

    @Before
    fun setUp() {
        configService = FakeConfigService(deviceId = TestUuidSource().createUuid())
        sessionIdsProvider = FakeSessionIdsProvider()
        delegate = SdkStateApiDelegate(
            deviceIdentifier = lazy { configService.deviceId },
            sessionIdsProvider = sessionIdsProvider,
            crashVerifier = LastRunCrashVerifier(unimplemented()),
        )
    }

    @Test
    fun isStarted() {
        assertTrue(delegate.isStarted)
    }

    @Test
    fun getDeviceId() {
        configService.deviceId = "foo"
        assertEquals("foo", delegate.deviceId)
    }

    @Test
    fun getCurrentSessionId() {
        sessionIdsProvider.userSessionId = "test"
        assertEquals("test", delegate.currentUserSessionId)
    }
}
