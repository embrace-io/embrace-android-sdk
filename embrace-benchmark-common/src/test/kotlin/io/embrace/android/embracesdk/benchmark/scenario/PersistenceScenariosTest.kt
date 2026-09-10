package io.embrace.android.embracesdk.benchmark.scenario

import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs every scenario against a stand-in SDK. Because a scenario is code rather than data, running
 * it is the only way to know it holds together - this catches a scenario that throws, never
 * advances the clock, or calls the API in a way that does not compile against the current surface.
 */
internal class PersistenceScenariosTest {

    @Test
    fun `scenario ids are unique`() {
        val ids = PersistenceScenarios.all.map(ScenarioSpec::id)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every scenario has a description`() {
        PersistenceScenarios.all.forEach { scenario ->
            assertTrue("${scenario.id} has no description", scenario.description.isNotBlank())
        }
    }

    @Test
    fun `every scenario runs without throwing`() {
        PersistenceScenarios.all.forEach { scenario ->
            scenario.action(scope())
        }
    }

    @Test
    fun `every scenario lasts long enough to be a session`() {
        PersistenceScenarios.all.forEach { scenario ->
            val scope = scope()
            scenario.action(scope)
            assertTrue(
                "${scenario.id} only lasts ${scope.elapsedMs}ms",
                scope.elapsedMs >= MIN_SESSION_MS,
            )
        }
    }

    @Test
    fun `no scenario emits telemetry faster than a real app would`() {
        PersistenceScenarios.all.forEach { scenario ->
            val scope = scope()
            scenario.action(scope)
            val perMinute = scope.apiCalls * MINUTE_MS / scope.elapsedMs
            assertTrue(
                "${scenario.id} emits $perMinute telemetry calls per minute",
                perMinute <= MAX_CALLS_PER_MINUTE,
            )
        }
    }

    @Test
    fun `only the scenarios about leaving the app change its foreground state`() {
        val transitions = PersistenceScenarios.all.associate { scenario ->
            val scope = scope()
            scenario.action(scope)
            scenario.id to scope.backgroundTransitions
        }

        assertEquals(4, transitions.getValue(PersistenceScenarios.interruptedSession.id))
        assertEquals(1, transitions.getValue(PersistenceScenarios.longEngagedSession.id))
        assertEquals(0, transitions.getValue(PersistenceScenarios.browseFeed.id))
        assertEquals(0, transitions.getValue(PersistenceScenarios.quickCheck.id))
    }

    @Test
    fun `scenarios are looked up by id and an unknown id fails loudly`() {
        assertEquals(PersistenceScenarios.browseFeed, PersistenceScenarios.byId("browse_feed"))
        assertThrows(IllegalStateException::class.java) { PersistenceScenarios.byId("not_a_scenario") }
    }

    private fun scope() = RecordingScope(mockk<SdkApi>(relaxed = true))

    private class RecordingScope(delegate: SdkApi) : ScenarioScope {

        var elapsedMs: Long = 0
            private set

        var backgroundTransitions: Int = 0
            private set

        var apiCalls: Int = 0
            private set

        override val embrace: SdkApi = CountingSdkApi(delegate) { apiCalls++ }

        override val nowMs: Long
            get() = START_TIME_MS + elapsedMs

        override fun advanceTime(ms: Long) {
            elapsedMs += ms
        }

        override fun backgroundAndReturn(ms: Long) {
            backgroundTransitions++
            elapsedMs += ms
        }
    }

    private class CountingSdkApi(
        private val delegate: SdkApi,
        private val onCall: () -> Unit,
    ) : SdkApi by delegate {

        override fun <T> recordSpan(
            name: String,
            parent: EmbraceSpan?,
            attributes: Map<String, String>,
            events: List<EmbraceSpanEvent>,
            autoTerminationMode: AutoTerminationMode,
            code: () -> T,
        ): T {
            onCall()
            return code()
        }

        override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) = onCall()

        override fun addBreadcrumb(message: String) = onCall()

        override fun logInfo(message: String) = onCall()

        override fun logWarning(message: String) = onCall()

        override fun logError(message: String) = onCall()
    }

    private companion object {
        const val START_TIME_MS = 1_692_201_601_000L
        const val MINUTE_MS = 60_000L
        const val MIN_SESSION_MS = 10_000L
        const val MAX_CALLS_PER_MINUTE = 600L
    }
}
