package io.embrace.android.embracesdk.macrobenchmark.app

import android.os.Trace
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.benchmark.scenario.DeviceScenarioScope
import io.embrace.android.embracesdk.benchmark.scenario.PersistenceScenarios
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_AWAIT
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_ERROR
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_NOT_STARTED
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_OK
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_PREFIX
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_RUNNING
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs one scenario to completion, ends the session it produced, and reports progress as a status
 * the benchmark driving the app reads off the screen.
 */
object ScenarioRunner {

    private val display = AtomicReference<((String) -> Unit)?>(null)
    private val foregrounded = AtomicReference<CountDownLatch?>(null)
    private var thread: Thread? = null

    /**
     * The first call starts [scenarioId]; each
     * later one releases a scenario waiting to be foregrounded again.
     */
    @Synchronized
    fun onForeground(scenarioId: String, onStatus: (String) -> Unit) {
        display.set(onStatus)
        onStatus(STATUS_RUNNING)

        val latch = foregrounded.getAndSet(null)
        if (latch != null) {
            latch.countDown()
        } else if (thread == null) {
            thread = Thread({ run(scenarioId) }, THREAD_NAME).apply { start() }
        }
    }

    private fun run(scenarioId: String) {
        val outcome = try {
            runScenario(scenarioId)
        } catch (exc: Exception) {
            STATUS_ERROR + (exc.message ?: exc.javaClass.simpleName)
        }
        // the SDK persists on its own workers, so give this run's writes time to land in the trace
        Thread.sleep(SETTLE_MS)
        publish(STATUS_PREFIX + outcome)
    }

    private fun runScenario(scenarioId: String): String {
        val scenario = PersistenceScenarios.byId(scenarioId)
        val started = Embrace.isStarted
        val scope = DeviceScenarioScope(Embrace, ::awaitForeground)
        trace(SECTION_RUN) { scenario.action(scope) }

        val previousSessionId = Embrace.currentUserSessionId
        trace(SECTION_SESSION_END, Embrace::endUserSession)

        return when {
            !started -> STATUS_NOT_STARTED
            Embrace.currentUserSessionId == previousSessionId -> STATUS_END_DECLINED
            else -> STATUS_OK
        }
    }

    /**
     * Asks the benchmark to background the app for [ms], and blocks until it has done so.
     */
    private fun awaitForeground(ms: Long) {
        val latch = CountDownLatch(1)
        foregrounded.set(latch)
        publish(STATUS_PREFIX + STATUS_AWAIT + ms)
        latch.await()
    }

    private fun publish(text: String) {
        display.get()?.invoke(text)
    }

    private inline fun <T> trace(sectionName: String, block: () -> T): T {
        Trace.beginSection(sectionName)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }

    private const val THREAD_NAME = "scenario-driver"
    private const val SECTION_RUN = "scenario-run"
    private const val SECTION_SESSION_END = "session-end"
    private const val STATUS_END_DECLINED = "end-declined"
    private const val SETTLE_MS = 2000L
}
