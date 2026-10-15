package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.fakeSessionEnvelope
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
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
    fun `stored payload not sent at startup when the device is known to be offline`() {
        testRule.runTest(
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                payloadStorageService.addPayload(
                    fakeSessionStoredTelemetryMetadata,
                    fakeSessionEnvelope(startMs = fakeSessionStoredTelemetryMetadata.timestamp),
                )
            },
            testCaseAction = {},
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)
            }
        )
    }

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
