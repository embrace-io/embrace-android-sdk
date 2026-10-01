package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectionType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.worker.Worker.Priority.DataPersistenceWorker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

private const val STORAGE_LIMIT = 500
private const val OVERAGE = 100

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class PruningFeatureTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
        EmbraceSetupInterface().apply {
            getEmbLogger().throwOnInternalError = false
        }
    }

    @Test
    fun `stored payloads are pruned appropriately`() {
        testRule.runTest(
            testCaseAction = {
                simulateConnectionTypeChange(ConnectionType.NONE)
                repeat(STORAGE_LIMIT + OVERAGE) { k ->
                    recordSession {
                        embrace.addBreadcrumb("$k")
                    }
                }
                submitNetworkChange()
            },
            assertAction = {
                val sessionEnvelopes = getSessionEnvelopes(STORAGE_LIMIT, waitTimeMs = 10000)
                assertEquals(STORAGE_LIMIT, sessionEnvelopes.size)

                val breadcrumbs = sessionEnvelopes.map { envelope ->
                    val sessionPartSpan = envelope.findSessionPartSpan()
                    val breadcrumbEvent = checkNotNull(sessionPartSpan.events?.single {
                        it.name == "emb-breadcrumb"
                    })
                    checkNotNull(breadcrumbEvent.attributes?.findAttributeValue("message")).toInt()
                }
                val expected = List(STORAGE_LIMIT) { it + OVERAGE }
                assertEquals(STORAGE_LIMIT, breadcrumbs.size)
                assertEquals(expected, breadcrumbs)
            }
        )
    }

    /**
     * Submits a network change AFTER all the sessions have been written to disk & triggered the
     * pruning logic.
     */
    private fun EmbraceActionInterface.submitNetworkChange() {
        val workerThreadModule = testRule.bootstrapper.workerThreadModule
        val worker =
            workerThreadModule.priorityWorker<StoredTelemetryMetadata>(DataPersistenceWorker)
        worker.submit(fakeSessionStoredTelemetryMetadata) {
            simulateConnectionTypeChange(ConnectionType.WIFI)
        }
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
