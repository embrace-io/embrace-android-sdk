package io.embrace.android.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.embrace.android.embracesdk.benchmark.endpoint.LocalEmbraceEndpoint
import io.embrace.android.embracesdk.benchmark.endpoint.REMOTE_CONFIG_JSON
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives repeated instrumented cold starts so each iteration records a perfetto trace. The
 * analysis of SDK init (EmbTrace section durations, SDK-init window, scheduling) is done
 * entirely from those traces by the startup-analysis skill's `tools/startup analyze` — the metric
 * list here is only the minimum measureRepeated requires, not the source of the numbers.
 *
 * Each method is one arm, differing from [coldStartup] in one thing only: its compilation mode, or the user-session
 * state each launch starts from. [measureColdStartup] is the rest. Every launch runs with an app ID against Embrace's
 * endpoints served on the device ([LocalEmbraceEndpoint], for the whole method), so the SDK fetches and caches its
 * config and delivers its payloads as in a customer's app.
 *
 * Instrumentation argument `embrace.iterations` ([ITERATIONS_ARGUMENT]) sets every method's iteration count for the
 * run, in place of [DEFAULT_ITERATIONS]; anything but a positive integer fails the run. From Gradle:
 * `-Pandroid.testInstrumentationRunnerArguments.embrace.iterations=10`.
 */
@RunWith(AndroidJUnit4::class)
internal class StartupBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * Macrobenchmark's default compilation ([CompilationMode.DEFAULT]: the packaged baseline profiles, the SDK's
     * included, where the device supports them). Each launch follows the last within seconds, so it restores the user
     * session the previous launch left, as a customer's app does when reopened within the SDK's inactivity timeout.
     */
    @Test
    fun coldStartup() = measureColdStartup(CompilationMode.DEFAULT)

    /** No ahead-of-time compilation at all: interpreted and JIT, every profile ignored. */
    @Test
    fun coldStartupNoAot() = measureColdStartup(CompilationMode.None())

    /**
     * The whole app compiled ahead of time (`speed`), which builds no app image: its code runs compiled, but its classes
     * load at runtime, as with no compilation. Against [coldStartupNoAot], which has no image either, it separates what
     * compiled code saves; against [coldStartup], what the baseline profile's app image saves. Not a state customers'
     * installs reach, as installs compile from profiles.
     */
    @Test
    fun coldStartupFullAot() = measureColdStartup(CompilationMode.Full())

    /**
     * Every launch the first after an install: the app's data cleared before each, so the SDK finds nothing to restore
     * or resurrect, creates its first user session, and fetches its remote config with none cached.
     */
    @Test
    fun coldStartupNewUserSession() = measureColdStartup(CompilationMode.DEFAULT) {
        device.executeShellCommand("pm clear $TARGET_PACKAGE")
    }

    /**
     * The shape most customers' cold starts have: the previous user session has expired, so the SDK creates a new one,
     * while everything else, the cached config and anything to resurrect included, is as a returning user's app has
     * it. The endpoint serves a remote config with the shortest inactivity timeout the SDK takes, and each launch comes
     * longer than that after the previous one was backgrounded.
     *
     * A user session keeps the inactivity timeout it was created with, so the session the first measured launch finds
     * must have been created with that config already cached. [expiringSession] arranges it, in the first iteration's
     * setup so that it follows macrobenchmark's compilation reset, which on some devices reinstalls the app.
     */
    @Test
    fun coldStartupExpiredUserSession() {
        var prepared = false
        measureColdStartup(
            compilationMode = CompilationMode.DEFAULT,
            remoteConfig = EXPIRING_SESSION_CONFIG,
        ) { endpoint ->
            if (!prepared) {
                expiringSession(endpoint)
                prepared = true
            }
            Thread.sleep(SESSION_EXPIRY_WAIT_MS)
        }
    }

    /**
     * Cold starts of the ExampleApp under [compilationMode], each after a press of home and [beforeLaunch], against a
     * [LocalEmbraceEndpoint] serving [remoteConfig] for the whole method.
     */
    private fun measureColdStartup(
        compilationMode: CompilationMode,
        remoteConfig: String = REMOTE_CONFIG_JSON,
        beforeLaunch: MacrobenchmarkScope.(LocalEmbraceEndpoint) -> Unit = {},
    ) {
        LocalEmbraceEndpoint(remoteConfig = remoteConfig).use { endpoint ->
            endpoint.start()
            benchmarkRule.measureRepeated(
                packageName = TARGET_PACKAGE,
                metrics = listOf(StartupTimingMetric()),
                iterations = iterations(),
                startupMode = StartupMode.COLD,
                compilationMode = compilationMode,
                setupBlock = {
                    pressHome()
                    beforeLaunch(endpoint)
                },
            ) {
                startActivityAndWait()
            }
            endpoint.close()
            println("local Embrace endpoint answered: ${endpoint.answered}")
        }
    }

    /**
     * Leaves the app with a user session that expires [SESSION_INACTIVITY_SECONDS] after it is backgrounded, through
     * two launches and the app's own UI. The first fetches and caches the remote config, with no config cached yet, so
     * the session it creates keeps the SDK's default timeout. The second starts with the cached config and ends that
     * session from the app's End Session example (`Embrace.endUserSession()`), so the session the SDK creates in its
     * place takes the config's timeout. Fails when the config fetch or the UI isn't there.
     */
    private fun MacrobenchmarkScope.expiringSession(endpoint: LocalEmbraceEndpoint) {
        killProcess()
        startActivityAndWait()
        val deadline = System.currentTimeMillis() + UI_TIMEOUT_MS
        while (endpoint.answered.keys.none { it.startsWith(CONFIG_SERVED) }) {
            check(System.currentTimeMillis() < deadline) { "the app never fetched its remote config: ${endpoint.answered}" }
            Thread.sleep(POLL_MS)
        }
        // the SDK writes the fetched config to its cache after the response arrives
        Thread.sleep(SETTLE_MS)
        killProcess()

        startActivityAndWait()
        click(By.text(END_SESSION), "the End Session example in the app's list")
        checkNotNull(device.wait(Until.findObject(By.desc(BACK)), UI_TIMEOUT_MS)) { "the End Session example never opened" }
        click(By.clickable(true).hasDescendant(By.text(END_SESSION)), "the End Session button")
        Thread.sleep(SETTLE_MS)
        pressHome()
    }

    private fun MacrobenchmarkScope.click(selector: BySelector, what: String) {
        checkNotNull(device.wait(Until.findObject(selector), UI_TIMEOUT_MS)) { "$what is not on the screen" }.click()
    }

    /** The run's [ITERATIONS_ARGUMENT], or [DEFAULT_ITERATIONS] without one; anything but a positive count fails. */
    private fun iterations(): Int {
        val argument = InstrumentationRegistry.getArguments().getString(ITERATIONS_ARGUMENT) ?: return DEFAULT_ITERATIONS
        return requireNotNull(argument.toIntOrNull()?.takeIf { it > 0 }) {
            "$ITERATIONS_ARGUMENT must be a positive integer, not '$argument'"
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "io.embrace.android.exampleapp"
        const val ITERATIONS_ARGUMENT = "embrace.iterations"
        const val DEFAULT_ITERATIONS = 20

        /** The shortest user-session inactivity timeout the SDK's remote config takes, in seconds. */
        const val SESSION_INACTIVITY_SECONDS = 30

        const val EXPIRING_SESSION_CONFIG = "{\"user_session\":{\"inactivity_timeout_seconds\":$SESSION_INACTIVITY_SECONDS}}"

        /** How long a launch waits after the previous one was backgrounded: past the inactivity timeout. */
        const val SESSION_EXPIRY_WAIT_MS = (SESSION_INACTIVITY_SECONDS + 3) * 1_000L

        /** The [LocalEmbraceEndpoint.answered] key of a config fetch answered with a config, not a 304. */
        const val CONFIG_SERVED = "GET /v2/config 200"

        /** The ExampleApp's End Session example: its entry in the app's list, and its button. */
        const val END_SESSION = "End Session"

        /** The content description of an example screen's back button. */
        const val BACK = "Back"

        const val UI_TIMEOUT_MS = 15_000L
        const val POLL_MS = 200L
        const val SETTLE_MS = 2_000L
    }
}
