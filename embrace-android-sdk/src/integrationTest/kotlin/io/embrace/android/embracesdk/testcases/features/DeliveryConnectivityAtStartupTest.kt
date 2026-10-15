package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.fakeSessionEnvelope
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule.Companion.DEFAULT_EXPIRED_USER_SESSION_ID
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule.Companion.DEFAULT_SDK_START_TIME_MS
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Runs startup and delivery tasks on the calling thread, so a delivery attempt that runs before the
 * scheduler knows the connectivity status reaches the server during startup instead of racing it.
 *
 * Only for scenarios that finish at startup: running the scheduling worker inline drops its
 * single-thread guarantee of prod and will wreak havoc on SDK behavior beyond this narrow case.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class DeliveryConnectivityAtStartupTest(
    otelSdkMode: OtelSdkMode,
) {

    private lateinit var payloadStorageService: FakePayloadStorageService

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
        EmbraceSetupInterface(fakeStorageLayer = true, workersToFake = inlineWorkers).also {
            payloadStorageService = checkNotNull(it.fakePayloadStorageService)
            inlineWorkers.forEach { worker -> it.getFakedWorkerExecutor(worker).blockingMode = false }
        }
    }

    /**
     * Make workers run synchronously on the calling thread to test the order of job queueing.
     * This will verify that the network connectivity status listener is hooked up before
     * we attempt to send undelivered payloads, and reduce the likelihood of an attempt on
     * an unverified network connection.
     */
    @Test
    fun `resurrected payload not sent at startup when offline and sent as soon as the device is online`() {
        testRule.runTest(
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                persistExpiredUserSession(
                    sdkStartTimeMs = DEFAULT_SDK_START_TIME_MS,
                    userSessionId = DEFAULT_EXPIRED_USER_SESSION_ID,
                    cacheIncompletePartPayload = true,
                )
            },
            testCaseAction = {
                goOnlineAndAssertDelivery()
            },
            assertAction = {
                assertEquals(DEFAULT_EXPIRED_USER_SESSION_ID, getSessionEnvelopes(1).single().getUserSessionId())
            }
        )
    }

    /**
     * With nothing to resurrect, the payloads already stored for delivery still wait for the device to be online.
     */
    @Test
    fun `stored payload not sent at startup when offline with nothing to resurrect and sent once online`() {
        testRule.runTest(
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                payloadStorageService.addPayload(
                    fakeSessionStoredTelemetryMetadata,
                    fakeSessionEnvelope(startMs = fakeSessionStoredTelemetryMetadata.timestamp),
                )
            },
            testCaseAction = {
                goOnlineAndAssertDelivery()
            },
            assertAction = {
                assertEquals(1, getSessionEnvelopes(1).size)
            }
        )
    }

    /**
     * Asserts startup left the one session payload undelivered, then brings the device online and asserts that
     * change alone delivered it: the fake clock doesn't move, so no retry or backoff can be what sent it.
     */
    private fun EmbraceActionInterface.goOnlineAndAssertDelivery() {
        // the workers run inline, so a payload delivered during startup would have left storage already
        assertEquals(1, storedSessionPayloadCount())
        simulateConnectivityChange(ConnectivityStatus.Wifi(true))
        assertEquals(0, storedSessionPayloadCount())
    }

    private fun storedSessionPayloadCount(): Int =
        payloadStorageService.storedPayloadMetadata().count { it.envelopeType == SupportedEnvelopeType.SESSION }

    internal companion object {
        private val inlineWorkers = listOf(
            Worker.Background.IoRegWorker,
            Worker.Background.DeliverySchedulingWorker,
            Worker.Background.HttpRequestWorker,
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
