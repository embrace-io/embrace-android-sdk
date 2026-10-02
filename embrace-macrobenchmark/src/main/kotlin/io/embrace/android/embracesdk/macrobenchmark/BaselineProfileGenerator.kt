package io.embrace.android.embracesdk.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the rules shipped in `embrace-android-sdk/src/main/baseline-prof.txt`.
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
        packageName = "io.embrace.android.embracesdk.macrobenchmark.app",
        filterPredicate = {
            it.startsWith("HSPLio/embrace/android/embracesdk") ||
                it.startsWith("HSPLio/embrace/opentelemetry") ||
                it.startsWith("HSPLio/opentelemetry")
        },
        profileBlock = {
            startActivityAndWait()
            device.waitForIdle()
        }
    )
}
