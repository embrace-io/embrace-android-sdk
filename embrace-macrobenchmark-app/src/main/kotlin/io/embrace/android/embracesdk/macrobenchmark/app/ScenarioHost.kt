package io.embrace.android.embracesdk.macrobenchmark.app

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.os.Trace
import android.provider.Settings
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.benchmark.scenario.HostTask
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.MULTI_FILE_PERSISTENCE_SETTING
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.OTEL_KOTLIN_SDK_SETTING
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.RESTORE_FIXTURE_SETTING
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_AWAIT
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_ERROR
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_NOT_STARTED
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_OK
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_PREFIX
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_RUNNING
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioSpec
import io.embrace.android.embracesdk.benchmark.scenario.hostTaskFrom
import io.embrace.android.embracesdk.macrobenchmark.app.fixture.FixtureOutcome
import io.embrace.android.embracesdk.macrobenchmark.app.fixture.FixtureRecorder
import io.embrace.android.embracesdk.macrobenchmark.app.storage.FixtureStorage
import io.embrace.android.embracesdk.macrobenchmark.app.storage.SdkStorage
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs the [HostTask] that a launch of [ScenarioHostActivity] asks for, and reports its progress as a status
 * that the benchmark driving the app reads off the screen.
 *
 * It does everything a benchmark needs of the app it runs in, [app], which only creates it and calls [start] with
 * the way it starts the SDK under test, so that another app can be benchmarked the same way. There is one per
 * process, registered for activity lifecycle callbacks, so that a task outlives its activity being backgrounded
 * and brought back. It acts on [ScenarioHostActivity] alone: the first time one resumes it starts the task the
 * launch intent names, and each later time it releases a task waiting in [awaitForeground]. A task runs on a
 * thread of its own, since its calls block.
 */
internal class ScenarioHost(private val app: Application) : ActivityLifecycleCallbacks {

    // the activities are held weakly, so that the benchmark's own bookkeeping never keeps one alive longer than
    // the app would, which would change what is being measured
    @Volatile
    private var hostActivity: WeakReference<ScenarioHostActivity>? = null

    // the directory names of the session parts in multi-file mode's directory when the SDK started
    @Volatile
    private var partsAtStart: List<String> = emptyList()

    private val foregrounded = AtomicReference<CountDownLatch?>(null)
    private var thread: Thread? = null

    /**
     * Readies this process for what the benchmark launched it to do, then starts the SDK under test through
     * [startSdk]. The app calls it from `Application.onCreate`, before anything else that touches the SDK. It
     * registers the host for activity lifecycle callbacks, notes the session parts on disk and writes the remote
     * config the benchmark's global settings select, both before the SDK reads them. A launch that restores a fixture leaves the SDK stopped, so that
     * nothing of its own lands on disk.
     */
    fun start(startSdk: () -> Unit) {
        app.registerActivityLifecycleCallbacks(this)
        if (globalSetting(RESTORE_FIXTURE_SETTING) != null) {
            return
        }
        val storage = SdkStorage(File(app.applicationInfo.dataDir))
        partsAtStart = storage.sessionPartDirs()
        seedRemoteConfig(storage)
        startSdk()
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is ScenarioHostActivity) {
            return
        }
        hostActivity = WeakReference(activity)
        publish(STATUS_RUNNING)

        val latch = foregrounded.getAndSet(null)
        if (latch != null) {
            latch.countDown()
        } else if (thread == null) {
            val intent = activity.intent
            thread = Thread({ run(intent) }, THREAD_NAME).apply { start() }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (hostActivity?.get() === activity) {
            hostActivity = null
        }
    }

    /**
     * Asks the benchmark to background the app for [ms], and blocks until it has brought it back.
     */
    fun awaitForeground(ms: Long) {
        val latch = CountDownLatch(1)
        foregrounded.set(latch)
        publish(STATUS_PREFIX + STATUS_AWAIT + ms)
        latch.await()
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}

    private fun run(intent: Intent) {
        val outcome = try {
            when (val task = hostTaskFrom(intent::getStringExtra)) {
                is HostTask.RunPersistenceScenario -> runPersistenceScenario(task.scenario)
                is HostTask.RecordFixture -> status(fixtureRecorder().record { runScenario(task.scenario) })
                HostTask.RestoreFixture -> status(fixtureRecorder().restore())
                HostTask.AwaitResurrection -> awaitResurrection()
            }
        } catch (exc: Exception) {
            STATUS_ERROR + (exc.message ?: exc.javaClass.simpleName)
        }
        publish(STATUS_PREFIX + outcome)
    }

    /**
     * Runs [scenario] to completion and ends the session it produced.
     */
    private fun runPersistenceScenario(scenario: ScenarioSpec): String {
        try {
            val started = Embrace.isStarted
            trace(SECTION_RUN) { runScenario(scenario) }

            val previousSessionId = Embrace.currentUserSessionId
            trace(SECTION_SESSION_END, Embrace::endUserSession)

            return when {
                !started -> STATUS_NOT_STARTED
                Embrace.currentUserSessionId == previousSessionId -> STATUS_END_DECLINED
                else -> STATUS_OK
            }
        } finally {
            // the SDK persists on its own workers, so give this run's writes time to land in the trace
            Thread.sleep(SCENARIO_SETTLE_MS)
        }
    }

    /**
     * Runs [scenario] against the SDK under test.
     */
    private fun runScenario(scenario: ScenarioSpec) {
        scenario.action(AppScenarioScope(Embrace, this))
    }

    /**
     * Waits until every session part that was on disk when the SDK started has left multi-file mode's
     * directory, and the cache of incomplete payloads that the SDK may pass parts through on their way to being
     * resurrected is empty again. It fails when the SDK started with no parts, so that a launch with nothing to
     * resurrect fails rather than measuring nothing.
     */
    private fun awaitResurrection(): String {
        val parts = partsAtStart
        if (parts.isEmpty()) {
            return STATUS_ERROR + "no session parts were on disk when the SDK started"
        }
        val storage = SdkStorage(File(app.applicationInfo.dataDir))
        val sessionsDir = storage.sessionsDir
        val cacheDir = storage.cacheDir
        val deadlineMs = SystemClock.uptimeMillis() + RESURRECTION_TIMEOUT_MS
        while (parts.any { File(sessionsDir, it).exists() } || cacheDir.list().orEmpty().isNotEmpty()) {
            if (SystemClock.uptimeMillis() > deadlineMs) {
                return STATUS_ERROR + "${parts.count { File(sessionsDir, it).exists() }} dead parts and " +
                    "${cacheDir.list().orEmpty().size} cached payloads left after ${RESURRECTION_TIMEOUT_MS}ms"
            }
            SystemClock.sleep(RESURRECTION_POLL_MS)
        }
        // the SDK's sections have ended, but give perfetto time to flush them
        SystemClock.sleep(RESURRECTION_SETTLE_MS)
        return STATUS_OK
    }

    private fun fixtureRecorder(): FixtureRecorder {
        val dataDir = File(app.applicationInfo.dataDir)
        return FixtureRecorder(
            sdk = SdkStorage(dataDir),
            fixture = FixtureStorage(dataDir),
            sdkStarted = { Embrace.isStarted },
        )
    }

    private fun status(outcome: FixtureOutcome): String = when (outcome) {
        FixtureOutcome.Recorded, FixtureOutcome.Restored -> STATUS_OK
        FixtureOutcome.SdkNotStarted -> STATUS_NOT_STARTED
        FixtureOutcome.SdkRunning ->
            STATUS_ERROR + "the SDK is running, so this launch did not start a process with " +
                "$RESTORE_FIXTURE_SETTING set"
        FixtureOutcome.NothingToRestore -> STATUS_ERROR + "there is no recorded fixture to restore"
    }

    /**
     * Selects the persistence mode and the opentelemetry-kotlin implementation without rebuilding the APK, from
     * global settings that survive the `pm clear` the benchmark runs between iterations:
     *
     * ```
     * adb shell settings put global embrace_pct_multi_file_persistence 100
     * adb shell settings put global embrace_pct_otel_kotlin_sdk 100
     * ```
     *
     * This is required as the APK is non-debuggable in typical conditions.
     */
    private fun seedRemoteConfig(storage: SdkStorage) {
        val fields = listOfNotNull(
            globalSetting(MULTI_FILE_PERSISTENCE_SETTING)?.let { "\"pct_multi_file_persistence_enabled\":$it" },
            globalSetting(OTEL_KOTLIN_SDK_SETTING)?.let { "\"otel_kotlin_sdk\":{\"pct_enabled\":$it}" },
        )
        if (fields.isEmpty()) {
            return
        }
        storage.writeRemoteConfig(fields.joinToString(",", "{", "}"))
    }

    private fun globalSetting(name: String): String? = Settings.Global.getString(app.contentResolver, name)

    private fun publish(text: String) {
        hostActivity?.get()?.let { activity ->
            activity.runOnUiThread { activity.showStatus(text) }
        }
    }

    private inline fun <T> trace(sectionName: String, block: () -> T): T {
        Trace.beginSection(sectionName)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }

    private companion object {
        const val THREAD_NAME = "scenario-driver"
        const val SECTION_RUN = "scenario-run"
        const val SECTION_SESSION_END = "session-end"
        const val STATUS_END_DECLINED = "end-declined"
        const val SCENARIO_SETTLE_MS = 2000L
        const val RESURRECTION_POLL_MS = 10L
        const val RESURRECTION_TIMEOUT_MS = 60_000L
        const val RESURRECTION_SETTLE_MS = 500L
    }
}
