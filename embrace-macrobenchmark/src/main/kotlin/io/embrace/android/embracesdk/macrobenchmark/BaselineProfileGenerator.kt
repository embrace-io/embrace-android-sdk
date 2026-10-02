package io.embrace.android.embracesdk.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the rules shipped in `embrace-android-sdk/src/main/baseline-prof.txt`, from a cold start
 * of the app's `ProfileActivity`, which starts the SDK and then drives its telemetry APIs.
 *
 * Runs only with `androidx.benchmark.enabledRules=BaselineProfile`, on a device that is API 33+ or
 * rooted. `scripts/generate-baseline-profile.sh` does both and copies the output into place.
 */
@RunWith(AndroidJUnit4::class)
internal class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        filterPredicate = ::isShippedRule,
        profileBlock = {
            startActivityAndWait(Intent().setClassName(PACKAGE_NAME, "$PACKAGE_NAME.ProfileActivity"))
            val status = device.wait(Until.findObject(By.textStartsWith("done:")), STATUS_TIMEOUT_MS)
            checkNotNull(status) { "the profile journey reported no outcome within ${STATUS_TIMEOUT_MS}ms" }
            check(status.text == "done: ok" || status.text == "done: end-declined") {
                "${status.text} - 'sdk-not-started' means the app has no appId - run the generator again (see README)."
            }
        }
    )

    private companion object {
        const val PACKAGE_NAME = "io.embrace.android.embracesdk.macrobenchmark.app"
        const val STATUS_TIMEOUT_MS = 30_000L
    }
}

/**
 * Keeps every rule for Embrace SDK and OpenTelemetry classes, whatever its flags. Class rules and
 * `H`/`S` methods all speed up a consumer app's startup, and `P`-only rules are harmless, so keeping
 * only `HSP` rules would discard useful ones; how many methods get `H` also varies by device image.
 * Benchmark classes share the SDK's package prefix but are not part of it, so they are excluded.
 *
 * Flags: https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure#define-rules-manually
 */
private fun isShippedRule(rule: String): Boolean {
    val descriptor = rule.trimStart('H', 'S', 'P')
    return SHIPPED_PACKAGES.any(descriptor::startsWith) && BENCHMARK_PACKAGES.none(descriptor::startsWith)
}

private val SHIPPED_PACKAGES = listOf("Lio/embrace/", "Lio/opentelemetry/")

private val BENCHMARK_PACKAGES = listOf(
    "Lio/embrace/android/embracesdk/macrobenchmark/",
    "Lio/embrace/android/embracesdk/benchmark/",
)
