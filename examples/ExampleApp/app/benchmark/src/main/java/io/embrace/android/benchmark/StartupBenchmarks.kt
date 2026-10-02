package io.embrace.android.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives repeated instrumented cold starts so each iteration records a perfetto trace. The
 * analysis of SDK init (EmbTrace section durations, SDK-init window, scheduling) is done
 * entirely from those traces by the startup-analysis skill's analyze_startup.py — the metric
 * list here is only the minimum measureRepeated requires, not the source of the numbers.
 */
@RunWith(AndroidJUnit4::class)
internal class StartupBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = "io.embrace.android.exampleapp",
            metrics = listOf(StartupTimingMetric()),
            iterations = 50,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() }
        ) {
            startActivityAndWait()
        }
    }

    /**
     * Baseline-profile A/B pair: [coldStartupNoAot] runs fully JIT/interpreted (profiles
     * ignored); [coldStartupBaselineProfile] compiles with the packaged baseline profile and
     * FAILS if none is packaged — which is itself the diagnostic. The default [coldStartup]
     * stays on CompilationMode.DEFAULT (fresh-install state) for continuity with past runs.
     */
    @Test
    fun coldStartupNoAot() {
        benchmarkRule.measureRepeated(
            packageName = "io.embrace.android.exampleapp",
            metrics = listOf(StartupTimingMetric()),
            iterations = 50,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.None(),
            setupBlock = { pressHome() }
        ) {
            startActivityAndWait()
        }
    }

    /** Discriminator for the ART-12 inversion: everything-AOT vs profile-AOT vs JIT. */
    @Test
    fun coldStartupFullAot() {
        benchmarkRule.measureRepeated(
            packageName = "io.embrace.android.exampleapp",
            metrics = listOf(StartupTimingMetric()),
            iterations = 20,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Full(),
            setupBlock = { pressHome() }
        ) {
            startActivityAndWait()
        }
    }

    /**
     * The production cold-start shape. Every iteration starts with the app's data cleared, so the SDK
     * finds no restorable user session and takes the new-user-session WRITE path inside
     * `start-first-session`. A bench that relaunches within the 30-minute inactivity window (every other
     * method here) restores the previous user session and skips that write, which is why the lab never
     * saw the cost production pays at its median. Clearing data also puts the persisted-config load on
     * its first-launch (no file) path; compare against [coldStartupBaselineProfile] with that in mind.
     */
    @Test
    fun coldStartupBaselineProfileNewUserSession() {
        benchmarkRule.measureRepeated(
            packageName = "io.embrace.android.exampleapp",
            metrics = listOf(StartupTimingMetric()),
            iterations = 50,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require,
                warmupIterations = 0,
            ),
            setupBlock = {
                device.executeShellCommand("pm clear io.embrace.android.exampleapp")
                pressHome()
            }
        ) {
            startActivityAndWait()
        }
    }

    /**
     * The production cold-start shape WITHOUT the first-launch side effects of `pm clear`: the app's
     * `BenchmarkStateHooks` deletes only the persisted user session before `Embrace.start`, so the
     * SDK creates a new one (the path production takes hours after its last launch) while the
     * persisted config and everything else stay as a returning user would have them. Compare
     * `start-first-session` against [coldStartupBaselineProfile] (restores) and
     * [coldStartupBaselineProfileNewUserSession] (fresh install). The setting persists on the
     * device; `adb shell settings delete global embrace_bench_expire_user_session` disarms it.
     */
    @Test
    fun coldStartupBaselineProfileExpiredUserSession() {
        benchmarkRule.measureRepeated(
            packageName = "io.embrace.android.exampleapp",
            metrics = listOf(StartupTimingMetric()),
            iterations = 50,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require,
                warmupIterations = 0,
            ),
            setupBlock = {
                device.executeShellCommand("settings put global embrace_bench_expire_user_session 1")
                pressHome()
            }
        ) {
            startActivityAndWait()
        }
    }

    @Test
    fun coldStartupBaselineProfile() {
        benchmarkRule.measureRepeated(
            packageName = "io.embrace.android.exampleapp",
            metrics = listOf(StartupTimingMetric()),
            iterations = 50,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require,
                warmupIterations = 0,
            ),
            setupBlock = { pressHome() }
        ) {
            startActivityAndWait()
        }
    }
}
