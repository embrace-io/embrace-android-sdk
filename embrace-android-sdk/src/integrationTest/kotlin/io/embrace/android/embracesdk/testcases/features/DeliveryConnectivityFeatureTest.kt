package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.fakeSessionEnvelope
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata2
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class DeliveryConnectivityFeatureTest {

    private lateinit var payloadStorageService: FakePayloadStorageService

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Before
    fun setUp() {
        payloadStorageService = FakePayloadStorageService()
    }

    @Test
    fun `stored payload not sent with no connection`() {
        val sessionMetadata = fakeSessionStoredTelemetryMetadata
        val startMs = sessionMetadata.timestamp
        val envelope = fakeSessionEnvelope(startMs = startMs)
        testRule.runTest(
            setupAction = {
                networkConnectivityService.networkStatus = NetworkStatus.NOT_REACHABLE
                payloadStorageService.addPayload(sessionMetadata, envelope)
                payloadStorageServiceProvider = { payloadStorageService }
            },
            testCaseAction = {},
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)
            }
        )
    }

    @Test
    fun `new payload not sent with no connection`() {
        testRule.runTest(
            setupAction = {
                networkConnectivityService.networkStatus = NetworkStatus.NOT_REACHABLE
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)
            }
        )
    }

    @Test
    fun `stored and new payloads sent when connection restored`() {
        val sessionMetadata = fakeSessionStoredTelemetryMetadata
        val startMs = sessionMetadata.timestamp
        val envelope = fakeSessionEnvelope(startMs = startMs)
        testRule.runTest(
            setupAction = {
                networkConnectivityService.networkStatus = NetworkStatus.NOT_REACHABLE
                payloadStorageService.addPayload(sessionMetadata, envelope)
                payloadStorageServiceProvider = { payloadStorageService }
            },
            testCaseAction = {
                recordSession()
                simulateNetworkChange(NetworkStatus.WIFI)
            },
            assertAction = {
                assertEquals(2, getSessionEnvelopes(2).size)
            }
        )
    }

    @Test
    fun `stored payload sent on startup`() {
        val startMs = fakeSessionStoredTelemetryMetadata.timestamp
        testRule.runTest(
            setupAction = {
                payloadStorageService.addPayload(
                    fakeSessionStoredTelemetryMetadata,
                    fakeSessionEnvelope(sessionId = "1", startMs = startMs))
                payloadStorageService.addPayload(
                    fakeSessionStoredTelemetryMetadata2,
                    fakeSessionEnvelope(sessionId = "2", startMs = startMs + 1000)
                )
                payloadStorageServiceProvider = { payloadStorageService }
            },
            testCaseAction = {},
            assertAction = {
                assertEquals(2, getSessionEnvelopes(2).size)
            }
        )
    }
}
