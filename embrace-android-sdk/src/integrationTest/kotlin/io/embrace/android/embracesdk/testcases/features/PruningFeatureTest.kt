package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.worker.Worker.Priority.DataPersistenceWorker
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val STORAGE_LIMIT = 500
private const val OVERAGE = 100

@RunWith(AndroidJUnit4::class)
internal class PruningFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        val clock = FakeClock(0)
        EmbraceSetupInterface(
            overriddenClock = clock,
            overriddenInitModule = FakeInitModule(
                clock,
                FakeEmbLogger(throwOnInternalError = false)
            )
        )
    }

    @Test
    fun `stored payloads are pruned appropriately`() {
        testRule.runTest(
            testCaseAction = {
                simulateNetworkChange(NetworkStatus.NOT_REACHABLE)
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
                    val sessionSpan = envelope.findSessionSpan()
                    val breadcrumbEvent = checkNotNull(sessionSpan.events?.single {
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
            simulateNetworkChange(NetworkStatus.WIFI)
        }
    }
}
